package com.bitchat.android.wallet.mint

import com.bitchat.android.wallet.cashu.BlindSignature
import com.bitchat.android.wallet.cashu.BlindedMessage
import com.bitchat.android.wallet.cashu.Keyset
import com.bitchat.android.wallet.cashu.KeysetInfo
import com.bitchat.android.wallet.cashu.MeltQuote
import com.bitchat.android.wallet.cashu.MintInfo
import com.bitchat.android.wallet.cashu.MintQuote
import com.bitchat.android.wallet.cashu.Proof
import com.bitchat.android.wallet.cashu.ProofStateResult

/**
 * The mint's HTTP API (NUT-01..09, bolt11 per NUT-23). One instance per mint URL.
 *
 * Implementations throw [MintException]. Callers distinguish definite rejections from
 * unknown outcomes: only [MintException.Rejected] proves a state-changing call had no effect.
 */
interface MintApi {
    val url: String

    suspend fun info(): MintInfo
    suspend fun keysets(): List<KeysetInfo>
    suspend fun keys(keysetId: String): Keyset
    suspend fun mintQuote(amount: Long, unit: String): MintQuote
    suspend fun checkMintQuote(quote: String): MintQuote
    suspend fun mint(quote: String, outputs: List<BlindedMessage>): List<BlindSignature>
    suspend fun meltQuote(request: String, unit: String): MeltQuote
    suspend fun checkMeltQuote(quote: String): MeltQuote
    suspend fun melt(quote: String, inputs: List<Proof>, outputs: List<BlindedMessage>): MeltQuote
    suspend fun swap(inputs: List<Proof>, outputs: List<BlindedMessage>): List<BlindSignature>
    suspend fun checkState(ys: List<String>): List<ProofStateResult>
    /** NUT-09: signatures the mint has already issued for [outputs], paired with their output. */
    suspend fun restore(outputs: List<BlindedMessage>): List<Pair<BlindedMessage, BlindSignature>>
}

fun interface MintApiFactory {
    fun create(url: String): MintApi
}

/** Error codes from `cashubtc/nuts/error_codes.md` that the wallet reacts to. */
object CashuErrorCode {
    const val PROOF_VERIFICATION_FAILED = 10001
    const val PROOFS_SPENT = 11001
    const val PROOFS_PENDING = 11002
    const val OUTPUTS_ALREADY_SIGNED = 11003
    const val OUTPUTS_PENDING = 11004
    const val TRANSACTION_UNBALANCED = 11005
    const val AMOUNT_OUT_OF_RANGE = 11006
    const val UNIT_MISMATCH = 11010
    const val AMOUNTLESS_INVOICE = 11011
    const val UNIT_UNSUPPORTED = 11013
    const val KEYSET_UNKNOWN = 12001
    const val KEYSET_INACTIVE = 12002
    const val KEYSET_EXPIRED = 12003
    const val QUOTE_NOT_PAID = 20001
    const val QUOTE_ALREADY_ISSUED = 20002
    const val MINTING_DISABLED = 20003
    const val LIGHTNING_PAYMENT_FAILED = 20004
    const val QUOTE_PENDING = 20005
    const val INVOICE_ALREADY_PAID = 20006
    const val QUOTE_EXPIRED = 20007
}

sealed class MintException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The mint could not be reached, or the response was lost. Outcome unknown. */
    class Unreachable(cause: Throwable? = null) : MintException("mint unreachable", cause)

    /** The mint answered with a 5xx. For state-changing calls the outcome is unknown. */
    class ServerError(val status: Int) : MintException("mint server error $status")

    /** The mint answered 429. Nothing happened; try later. */
    class RateLimited : MintException("rate limited")

    /**
     * The mint refused the request with a protocol error. The request had no effect.
     * [detail] is mint-controlled text: never show it raw or log it.
     */
    class Rejected(val code: Int?, val status: Int, internal val detail: String? = null) :
        MintException("mint rejected request (code=$code, status=$status)")

    /** The response did not match the protocol. Outcome unknown. */
    class Malformed(reason: String) : MintException("malformed mint response: $reason")
}
