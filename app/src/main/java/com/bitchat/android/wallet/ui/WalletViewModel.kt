package com.bitchat.android.wallet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bitchat.android.wallet.WalletService
import com.bitchat.android.wallet.engine.CashuWallet
import com.bitchat.android.wallet.engine.WalletState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Ways into the wallet from the rest of Bitchat. */
sealed interface WalletLaunch {
    data object Home : WalletLaunch

    /** A token tapped in a conversation. */
    data class Claim(val token: String, val viaChat: Boolean = true) : WalletLaunch

    /**
     * Send ecash into the current conversation. [lockPubkey] is the peer's compressed key when
     * Bitchat knows their Nostr identity; [deliver] posts the finished token as a message.
     */
    data class SendToChat(
        val peerLabel: String?,
        val lockPubkey: String?,
        val isPublic: Boolean,
        val deliver: (String) -> Unit,
    ) : WalletLaunch
}

/** Process-wide request to show the wallet sheet; observed by `ChatScreen`. */
object WalletLauncher {
    private val _request = MutableStateFlow<WalletLaunch?>(null)
    val request: StateFlow<WalletLaunch?> = _request.asStateFlow()

    fun open(launch: WalletLaunch = WalletLaunch.Home) {
        _request.value = launch
    }

    fun close() {
        _request.value = null
    }
}

/** Screens of the wallet sheet. Flow routes own their state machine, created on push. */
sealed class WalletRoute {
    data object Onboarding : WalletRoute()
    data object Home : WalletRoute()
    data object ReceiveChoice : WalletRoute()
    data object SendChoice : WalletRoute()
    data object Mints : WalletRoute()
    data object History : WalletRoute()
    data object Settings : WalletRoute()
    data class MintDetail(val url: String) : WalletRoute()
    data class HistoryDetail(val id: String) : WalletRoute()
    class Backup(val flow: BackupFlow) : WalletRoute()
    class Restore(val flow: RestoreFlow) : WalletRoute()
    class ReceiveLightning(val flow: ReceiveLightningFlow) : WalletRoute()
    class ReceiveEcash(val flow: ReceiveEcashFlow) : WalletRoute()
    class SendEcash(val flow: SendEcashFlow) : WalletRoute()
    class SendLightning(val flow: SendLightningFlow) : WalletRoute()
    class AddMint(val flow: AddMintFlow) : WalletRoute()
}

/**
 * Owns the wallet sheet's page stack and creates flows. Activity-scoped, so closing the sheet
 * mid-operation does not cancel anything: operations run in the engine and finish on their own.
 */
class WalletViewModel(app: Application) : AndroidViewModel(app) {
    val wallet: CashuWallet = WalletService.wallet(app)
    val state: StateFlow<WalletState> = wallet.state
    val hasWallet: StateFlow<Boolean> = WalletService.hasWallet.also { WalletService.vault(app) }

    private val _stack = MutableStateFlow<List<WalletRoute>>(listOf(WalletRoute.Home))
    val stack: StateFlow<List<WalletRoute>> = _stack.asStateFlow()

    /** Mints that failed to answer during the last refresh, for the offline banner. */
    private val _unreachable = MutableStateFlow<Set<String>>(emptySet())
    val unreachable: StateFlow<Set<String>> = _unreachable.asStateFlow()

    /**
     * Number of terminal pages (success, failure, pending) on screen. A counter rather than a
     * flag because the incoming page appears before the outgoing one leaves during transitions.
     */
    private val _terminalPages = MutableStateFlow(0)
    val terminal: StateFlow<Int> = _terminalPages.asStateFlow()

    fun terminalPage(entered: Boolean) {
        _terminalPages.value = (_terminalPages.value + if (entered) 1 else -1).coerceAtLeast(0)
    }

    private val _balanceRevealed = MutableStateFlow(false)
    val balanceRevealed: StateFlow<Boolean> = _balanceRevealed.asStateFlow()

    /** Context of a send started from a conversation; cleared when the sheet closes. */
    var chatContext: WalletLaunch.SendToChat? = null
        private set
    private var syncJob: Job? = null

    val backedUp: Boolean get() = WalletService.vault(getApplication()).backedUp

    fun open(launch: WalletLaunch) {
        chatContext = launch as? WalletLaunch.SendToChat
        _balanceRevealed.value = false
        val base = if (WalletService.hasWallet(getApplication())) WalletRoute.Home else WalletRoute.Onboarding
        _stack.value = listOf(base)
        if (base == WalletRoute.Home) {
            when (launch) {
                is WalletLaunch.Claim -> startClaim(launch.token, launch.viaChat)
                is WalletLaunch.SendToChat -> startSendEcash()
                WalletLaunch.Home -> Unit
            }
        } else {
            pendingLaunch = launch.takeIf { it !is WalletLaunch.Home }
        }
        sync()
    }

    /** A launch waiting for the wallet to exist (e.g. claim tapped before onboarding). */
    private var pendingLaunch: WalletLaunch? = null

    fun closed() {
        chatContext = null
        syncJob?.cancel()
    }

    // MARK: - Navigation

    fun push(route: WalletRoute) {
        _stack.value = _stack.value + route
    }

    /**
     * Steps back inside the current flow first (review back to amount keeps the amount), then
     * pops the page. Returns false when already at the root, so the sheet should close instead.
     */
    fun back(): Boolean {
        val current = _stack.value
        val handled = when (val top = current.lastOrNull()) {
            is WalletRoute.SendEcash -> top.flow.stepBack()
            is WalletRoute.SendLightning -> top.flow.stepBack()
            is WalletRoute.ReceiveEcash -> top.flow.stepBack()
            is WalletRoute.AddMint -> top.flow.stepBack()
            is WalletRoute.Backup -> top.flow.stepBack()
            else -> false
        }
        if (handled) return true
        if (current.size <= 1) return false
        _stack.value = current.dropLast(1)
        return true
    }

    fun popTo(route: WalletRoute) {
        val index = _stack.value.indexOfLast { it == route }
        if (index >= 0) _stack.value = _stack.value.take(index + 1)
    }

    fun home() {
        _stack.value = listOf(if (WalletService.hasWallet(getApplication())) WalletRoute.Home else WalletRoute.Onboarding)
    }

    // MARK: - Wallet lifecycle

    fun createWallet() {
        val words = WalletService.createWallet(getApplication())
        _stack.value = listOf(WalletRoute.Home, WalletRoute.Backup(BackupFlow(words, onFinished = { finishBackup() })))
    }

    fun showBackup() {
        val words = WalletService.vault(getApplication()).words() ?: return
        push(WalletRoute.Backup(BackupFlow(words, onFinished = { finishBackup() })))
    }

    private fun finishBackup() {
        WalletService.vault(getApplication()).backedUp = true
        home()
        resumePendingLaunch()
    }

    fun skipBackup() {
        home()
        resumePendingLaunch()
    }

    fun startRestore() {
        push(WalletRoute.Restore(RestoreFlow(getApplication(), viewModelScope, onFinished = { home(); resumePendingLaunch() })))
    }

    private fun resumePendingLaunch() {
        when (val launch = pendingLaunch.also { pendingLaunch = null }) {
            is WalletLaunch.Claim -> startClaim(launch.token, launch.viaChat)
            is WalletLaunch.SendToChat -> startSendEcash()
            else -> Unit
        }
    }

    // MARK: - Flows

    fun startReceiveLightning() = push(WalletRoute.ReceiveLightning(ReceiveLightningFlow(getApplication(), wallet, viewModelScope, defaultMint())))

    fun startClaim(token: String? = null, viaChat: Boolean = false) =
        push(WalletRoute.ReceiveEcash(ReceiveEcashFlow(getApplication(), wallet, viewModelScope, token, viaChat)))

    fun startSendEcash() = push(
        WalletRoute.SendEcash(SendEcashFlow(getApplication(), wallet, viewModelScope, defaultMint(), chatContext, unreachable.value))
    )

    fun startSendLightning() = push(WalletRoute.SendLightning(SendLightningFlow(getApplication(), wallet, viewModelScope, defaultMint())))

    fun startAddMint(prefill: String? = null) = push(WalletRoute.AddMint(AddMintFlow(getApplication(), wallet, viewModelScope, prefill) { back() }))

    /** The last-used mint that still exists, else the one holding the most sats. */
    fun defaultMint(): String? {
        val mints = state.value.mints
        val last = wallet.setting(WalletService.SETTING_LAST_MINT)
        return mints.firstOrNull { it.mint.url == last }?.mint?.url
            ?: mints.maxByOrNull { it.available["sat"] ?: 0 }?.mint?.url
    }

    // MARK: - Background sync

    /**
     * Refreshes mints (keyset rotation, reachability), finishes interrupted operations, and
     * checks pending sends. Runs on open and every 30 seconds while the sheet is up.
     */
    fun sync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            while (isActive) {
                val unreachable = HashSet<String>()
                for (mint in state.value.mints) {
                    if (wallet.refreshMint(mint.mint.url) == null) unreachable += mint.mint.url
                }
                _unreachable.value = unreachable
                runCatching { wallet.recover() }
                kotlinx.coroutines.delay(30_000)
            }
        }
    }

    fun toggleBalance() {
        _balanceRevealed.value = !_balanceRevealed.value
    }

    fun setSetting(key: String, value: String?) {
        viewModelScope.launch { runCatching { wallet.putSetting(key, value) } }
    }
}
