package com.bitchat.android.wallet.cashu

import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger

/** NUT-12 DLEQ proof. `r` is only present user-to-user, never toward the mint. */
data class DleqProof(val e: String, val s: String, val r: String? = null)

/** NUT-00 `Proof`. `secret` is the UTF-8 string exactly as signed, never re-serialized. */
data class Proof(
    val amount: Long,
    val keysetId: String,
    val secret: String,
    val c: String,
    val dleq: DleqProof? = null,
    val witness: String? = null,
) {
    /** `Y = hash_to_curve(secret)`, the identifier the mint uses for state checks. */
    fun y(): String = Bdhke.yHex(secret)

    /** What we send to a mint: DLEQ stripped for privacy (NUT-12). */
    fun forMint(): Proof = copy(dleq = null)

    override fun toString(): String = "Proof(amount=$amount, keysetId=$keysetId)"
}

data class BlindedMessage(val amount: Long, val keysetId: String, val b: String) {
    override fun toString(): String = "BlindedMessage(amount=$amount, keysetId=$keysetId)"
}

data class BlindSignature(
    val amount: Long,
    val keysetId: String,
    val c: String,
    val dleq: DleqProof? = null,
)

/** NUT-02 keyset metadata from `/v1/keysets`. */
data class KeysetInfo(
    val id: String,
    val unit: String,
    val active: Boolean,
    val inputFeePpk: Long = 0,
    val finalExpiry: Long? = null,
)

/**
 * A keyset with its public keys. Amount keys are kept as [BigInteger] because mints
 * publish denominations up to 2^63, which does not fit a signed Long; the wallet only ever
 * uses amounts that do.
 */
data class Keyset(
    val id: String,
    val unit: String,
    val active: Boolean,
    val inputFeePpk: Long,
    val finalExpiry: Long?,
    val keys: Map<BigInteger, String>,
) {
    @Transient
    private var pointCache: Map<Long, ECPoint>? = null

    /** Denominations usable by the wallet, ascending. */
    val amounts: List<Long>
        get() = keys.keys.filter { it.bitLength() <= 62 }.map { it.toLong() }.sorted()

    fun keyFor(amount: Long): ECPoint? {
        val cache = pointCache ?: keys.entries
            .filter { it.key.bitLength() <= 62 }
            .associate { it.key.toLong() to Secp256k1.decodePoint(it.value) }
            .also { pointCache = it }
        return cache[amount]
    }

    fun info(): KeysetInfo = KeysetInfo(id, unit, active, inputFeePpk, finalExpiry)
}

enum class ProofState { UNSPENT, PENDING, SPENT }

data class ProofStateResult(val y: String, val state: ProofState, val witness: String? = null)

enum class MintQuoteState { UNPAID, PAID, ISSUED }

data class MintQuote(
    val quote: String,
    val request: String,
    val amount: Long,
    val unit: String,
    val state: MintQuoteState,
    /** Unix seconds; null when the request never expires. */
    val expiry: Long?,
    val amountPaid: Long? = null,
    val amountIssued: Long? = null,
) {
    /** What can still be minted. Falls back to the legacy `state` field for older mints. */
    val mintable: Long
        get() = if (amountPaid != null && amountIssued != null) {
            (amountPaid - amountIssued).coerceAtLeast(0)
        } else when (state) {
            MintQuoteState.PAID -> amount
            else -> 0
        }

    override fun toString(): String = "MintQuote(amount=$amount, unit=$unit, state=$state)"
}

enum class MeltQuoteState { UNPAID, PENDING, PAID }

data class MeltQuote(
    val quote: String,
    val request: String,
    val amount: Long,
    val unit: String,
    val feeReserve: Long,
    val state: MeltQuoteState,
    val expiry: Long?,
    val preimage: String? = null,
    val change: List<BlindSignature> = emptyList(),
) {
    override fun toString(): String =
        "MeltQuote(amount=$amount, feeReserve=$feeReserve, state=$state)"
}

data class MethodSetting(
    val method: String,
    val unit: String,
    val minAmount: Long?,
    val maxAmount: Long?,
)

/** NUT-06 mint info, reduced to what the wallet shows or relies on. */
data class MintInfo(
    val name: String?,
    val pubkey: String?,
    val version: String?,
    val description: String?,
    val descriptionLong: String?,
    val motd: String?,
    val iconUrl: String?,
    val supportedNuts: Set<Int>,
    val mintMethods: List<MethodSetting>,
    val meltMethods: List<MethodSetting>,
    val mintingDisabled: Boolean,
    val meltingDisabled: Boolean,
) {
    fun supports(nut: Int): Boolean = nut in supportedNuts

    fun mintSetting(method: String, unit: String): MethodSetting? =
        mintMethods.firstOrNull { it.method == method && it.unit == unit }

    fun meltSetting(method: String, unit: String): MethodSetting? =
        meltMethods.firstOrNull { it.method == method && it.unit == unit }

    val units: Set<String>
        get() = (mintMethods + meltMethods).map { it.unit }.toSet()
}
