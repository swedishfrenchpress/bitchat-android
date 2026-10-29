package com.bitchat.android.wallet.testsupport

import com.bitchat.android.wallet.cashu.Bip39
import com.bitchat.android.wallet.engine.CashuWallet
import com.bitchat.android.wallet.engine.HistoryEntry
import com.bitchat.android.wallet.engine.InMemoryWalletStore
import com.bitchat.android.wallet.mint.MintApi
import java.math.BigInteger

/** Synthetic recovery phrases. Never real wallets. */
object TestSeeds {
    val ALICE = Bip39.generate(ByteArray(16) { 1 })
    val BOB = Bip39.generate(ByteArray(16) { 2 })
    val CAROL = Bip39.generate(ByteArray(16) { 3 })
}

/** A clock the test advances explicitly; no sleeps. */
class TestClock(var nowMs: Long = 1_800_000_000_000L) {
    fun advanceSeconds(seconds: Long) { nowMs += seconds * 1000 }
    val seconds: Long get() = nowMs / 1000
}

/**
 * One wallet against shared fake mints. [restart] rebuilds the engine from the same store,
 * which is what an app kill and relaunch looks like to the wallet.
 */
class WalletHarness(
    words: List<String>,
    val clock: TestClock,
    private val mints: Map<String, MintApi>,
    val store: InMemoryWalletStore = InMemoryWalletStore(),
    val extraKeys: List<BigInteger> = emptyList(),
) {
    val seed: ByteArray = Bip39.toSeed(words)
    var wallet: CashuWallet = build()
        private set

    private fun build() = CashuWallet(
        store = store,
        apiFactory = { url -> mints[url] ?: error("unknown test mint") },
        seedProvider = { seed },
        extraSigningKeys = { extraKeys },
        clock = { clock.nowMs },
    )

    suspend fun restart(): CashuWallet {
        wallet = build()
        wallet.load()
        return wallet
    }

    /** Adds [mint] and funds the wallet with [amount] via a paid Lightning quote. */
    suspend fun fund(mint: FakeMint, amount: Long, unit: String = "sat"): HistoryEntry {
        if (wallet.mint(mint.url) == null) wallet.addMint(mint.url)
        val entry = wallet.createMintQuote(mint.url, amount, unit)
        return wallet.checkMintQuote(entry.id)
    }

    fun balance(unit: String = "sat") = wallet.state.value.available(unit)
}
