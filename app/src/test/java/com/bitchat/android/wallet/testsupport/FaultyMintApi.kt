package com.bitchat.android.wallet.testsupport

import com.bitchat.android.wallet.cashu.BlindedMessage
import com.bitchat.android.wallet.cashu.Proof
import com.bitchat.android.wallet.mint.MintApi
import com.bitchat.android.wallet.mint.MintException

/**
 * Wraps a real mint client and injects transport faults, so recovery can be exercised
 * against a live mint: [dropResponseOn] lets the mint act and then loses its answer;
 * [offline] fails every call before it is sent.
 */
class FaultyMintApi(private val real: MintApi) : MintApi by real {
    @Volatile var offline = false
    @Volatile var dropResponseOn: String? = null

    private suspend fun <T> guard(call: String, block: suspend () -> T): T {
        if (offline) throw MintException.Unreachable()
        if (dropResponseOn == call) {
            dropResponseOn = null
            block()
            throw MintException.Unreachable()
        }
        return block()
    }

    override suspend fun info() = guard("info") { real.info() }
    override suspend fun keysets() = guard("keysets") { real.keysets() }
    override suspend fun keys(keysetId: String) = guard("keys") { real.keys(keysetId) }
    override suspend fun mintQuote(amount: Long, unit: String) = guard("mintQuote") { real.mintQuote(amount, unit) }
    override suspend fun checkMintQuote(quote: String) = guard("checkMintQuote") { real.checkMintQuote(quote) }
    override suspend fun mint(quote: String, outputs: List<BlindedMessage>) = guard("mint") { real.mint(quote, outputs) }
    override suspend fun meltQuote(request: String, unit: String) = guard("meltQuote") { real.meltQuote(request, unit) }
    override suspend fun checkMeltQuote(quote: String) = guard("checkMeltQuote") { real.checkMeltQuote(quote) }
    override suspend fun melt(quote: String, inputs: List<Proof>, outputs: List<BlindedMessage>) = guard("melt") { real.melt(quote, inputs, outputs) }
    override suspend fun swap(inputs: List<Proof>, outputs: List<BlindedMessage>) = guard("swap") { real.swap(inputs, outputs) }
    override suspend fun checkState(ys: List<String>) = guard("checkState") { real.checkState(ys) }
    override suspend fun restore(outputs: List<BlindedMessage>) = guard("restore") { real.restore(outputs) }
}
