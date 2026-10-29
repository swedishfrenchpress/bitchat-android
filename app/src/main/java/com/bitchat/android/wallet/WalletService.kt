package com.bitchat.android.wallet

import android.content.Context
import android.util.Log
import com.bitchat.android.BuildConfig
import com.bitchat.android.net.OkHttpProvider
import com.bitchat.android.nostr.NostrEvent
import com.bitchat.android.nostr.NostrIdentityBridge
import com.bitchat.android.nostr.NostrRelayManager
import com.bitchat.android.wallet.backup.MintListBackup
import com.bitchat.android.wallet.cashu.Bip39
import com.bitchat.android.wallet.cashu.Hex
import com.bitchat.android.wallet.cashu.Secp256k1
import com.bitchat.android.wallet.data.MnemonicVault
import com.bitchat.android.wallet.data.SqliteWalletStore
import com.bitchat.android.wallet.engine.CashuWallet
import com.bitchat.android.wallet.mint.HttpMintApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-wide owner of the Cashu wallet: storage, recovery words, the engine, and the NUT-27
 * backup. Created lazily the first time anything wallet-related is touched, so people who
 * never use the wallet pay nothing for it.
 */
object WalletService {
    private const val TAG = "WalletService"
    const val SETTING_NOSTR_BACKUP = "nostr_backup_enabled"
    const val SETTING_LAST_MINT = "last_mint"
    const val SETTING_HIDE_BALANCE = "hide_balance"
    /** Mint suggested in debug builds only (fake sats, see decisions.md D-005). */
    const val TEST_MINT_URL = "https://testnut.cashu.space"

    private val lock = Any()
    private var appContext: Context? = null
    private var vault: MnemonicVault? = null
    private var engine: CashuWallet? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var backupJob: Job? = null

    private val _hasWallet = MutableStateFlow(false)
    /** True once recovery words exist on this device. */
    val hasWallet: StateFlow<Boolean> = _hasWallet.asStateFlow()

    val suggestTestMint: Boolean get() = BuildConfig.DEBUG

    fun wallet(context: Context): CashuWallet = synchronized(lock) {
        engine ?: build(context.applicationContext).also { engine = it }
    }

    fun vault(context: Context): MnemonicVault = synchronized(lock) {
        vault ?: MnemonicVault.create(context.applicationContext).also {
            vault = it
            _hasWallet.value = it.hasWallet()
        }
    }

    /** Cheap check usable from UI code; does not open the database. */
    fun hasWallet(context: Context): Boolean = vault(context).hasWallet()

    /**
     * Cheapest possible check for chat rendering: a file stat, no Keystore or decryption on
     * the main thread. True once recovery words have been stored on this device.
     */
    fun walletExists(context: Context): Boolean =
        _hasWallet.value || vault != null && vault!!.hasWallet() || hasPrefsFile(context.applicationContext)

    private fun build(context: Context): CashuWallet {
        appContext = context
        val vault = vault(context)
        val wallet = CashuWallet(
            store = SqliteWalletStore(context),
            apiFactory = { url -> HttpMintApi(url) { OkHttpProvider.httpClient() } },
            seedProvider = { vault.seed() },
            extraSigningKeys = { nostrSigningKeys(context) },
        )
        scope.launch {
            runCatching { wallet.load() }.onFailure { Log.e(TAG, "Wallet failed to load: ${it.javaClass.simpleName}") }
            if (vault.hasWallet()) runCatching { wallet.recover() }
            observeMintListForBackup(context, wallet)
        }
        return wallet
    }

    /** Creates fresh recovery words. Returns them once so the backup flow can show them. */
    fun createWallet(context: Context): List<String> {
        val words = Bip39.generate()
        check(vault(context).store(words)) { "could not store recovery words" }
        _hasWallet.value = true
        wallet(context).refreshSeedState()
        engine?.let { e -> scope.launch { runCatching { e.putSetting(SETTING_NOSTR_BACKUP, "true") } } }
        return words
    }

    /** Installs restored recovery words. The caller then runs [CashuWallet.restore] on chosen mints. */
    fun installRecoveredWords(context: Context, words: List<String>) {
        check(vault(context).store(words)) { "could not store recovery words" }
        vault(context).backedUp = true
        _hasWallet.value = true
        wallet(context).refreshSeedState()
        engine?.let { e -> scope.launch { runCatching { e.putSetting(SETTING_NOSTR_BACKUP, "true") } } }
    }

    /**
     * Keys the wallet can sign P2PK locks with: the Bitchat Nostr identity, so tokens a peer
     * locked to our npub can be claimed.
     */
    private fun nostrSigningKeys(context: Context): List<BigInteger> = runCatching {
        val identity = NostrIdentityBridge.getCurrentNostrIdentity(context) ?: return emptyList()
        listOf(BigInteger(1, Hex.decode(identity.privateKeyHex)))
    }.getOrDefault(emptyList())

    /** Our compressed P2PK key as peers should lock to it (the Nostr identity, even-y form). */
    fun nostrLockKeyFor(xOnlyNostrPubkeyHex: String): String? =
        runCatching { com.bitchat.android.wallet.cashu.P2pk.fromXOnly(xOnlyNostrPubkeyHex) }.getOrNull()

    // MARK: - NUT-27 backup

    @OptIn(FlowPreview::class)
    private fun observeMintListForBackup(context: Context, wallet: CashuWallet) {
        backupJob?.cancel()
        backupJob = scope.launch {
            wallet.state
                .map { s -> s.mints.map { it.mint.url }.sorted() to (s.settings[SETTING_NOSTR_BACKUP] == "true") }
                .distinctUntilChanged()
                .drop(1)
                .debounce(2_000)
                .collect { (mints, enabled) -> if (enabled && mints.isNotEmpty()) publishBackup(context, mints) }
        }
    }

    fun publishBackup(context: Context, mints: List<String>) {
        val seed = vault(context).seed() ?: return
        runCatching {
            val event = MintListBackup.buildEvent(seed, mints, System.currentTimeMillis() / 1000)
            NostrRelayManager.getInstance(context).sendEvent(event, NostrRelayManager.defaultRelays())
        }.onFailure { Log.w(TAG, "Mint list backup not published: ${it.javaClass.simpleName}") }
    }

    /** Looks for a NUT-27 backup for [words] on the default relays, waiting at most [timeoutMs]. */
    suspend fun fetchBackup(context: Context, words: List<String>, timeoutMs: Long = 8_000): MintListBackup.Backup? {
        val seed = Bip39.toSeed(words)
        val relays = NostrRelayManager.getInstance(context)
        val received = CopyOnWriteArrayList<NostrEvent>()
        val first = CompletableDeferred<Unit>()
        val id = "wallet-backup-" + Hex.encode(Secp256k1.randomBytes(6))
        relays.subscribe(
            filter = MintListBackup.filter(seed),
            id = id,
            handler = { event -> received += event; first.complete(Unit) },
            targetRelayUrls = NostrRelayManager.defaultRelays(),
        )
        try {
            // Wait for the first answer, then give slower relays a moment to offer a newer one.
            withTimeoutOrNull(timeoutMs) { first.await() }
            if (received.isNotEmpty()) withTimeoutOrNull(1_500) { CompletableDeferred<Unit>().await() }
        } finally {
            relays.unsubscribe(id)
        }
        return MintListBackup.newest(seed, received.toList())
    }

    // MARK: - Panic

    /**
     * Erases the wallet completely: database, Keystore key, recovery words, and the prefs file.
     * Returns true only if every step confirmed. Safe to call when no wallet was ever created.
     */
    suspend fun panicWipe(context: Context): Boolean {
        val ctx = context.applicationContext
        val (currentEngine, currentVault) = synchronized(lock) {
            backupJob?.cancel()
            backupJob = null
            (engine to vault).also {
                engine = null
                vault = null
            }
        }
        // Only touch what exists: a person who never opened the wallet must not get a failed
        // panic report because the Keystore misbehaved for a key that was never created.
        val dbExists = ctx.getDatabasePath(SqliteWalletStore.DATABASE_NAME).exists()
        val engineWiped = when {
            currentEngine != null -> currentEngine.wipe()
            dbExists -> SqliteWalletStore(ctx).wipe()
            else -> true
        }
        val vaultCleared = when {
            currentVault != null -> currentVault.clear()
            hasPrefsFile(ctx) -> runCatching { MnemonicVault.create(ctx).clear() }.getOrDefault(false)
            else -> true
        }
        val fileDeleted = !hasPrefsFile(ctx) || MnemonicVault.deleteFile(ctx)
        _hasWallet.value = false
        return engineWiped && vaultCleared && fileDeleted
    }

    private fun hasPrefsFile(context: Context): Boolean =
        java.io.File(context.applicationInfo.dataDir, "shared_prefs/bitchat_wallet_secrets.xml").exists()
}
