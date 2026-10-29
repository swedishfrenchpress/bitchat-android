package com.bitchat.android.wallet.testsupport

import com.bitchat.android.wallet.cashu.Bdhke
import com.bitchat.android.wallet.cashu.BlindSignature
import com.bitchat.android.wallet.cashu.BlindedMessage
import com.bitchat.android.wallet.cashu.DleqProof
import com.bitchat.android.wallet.cashu.Hex
import com.bitchat.android.wallet.cashu.Keyset
import com.bitchat.android.wallet.cashu.KeysetIds
import com.bitchat.android.wallet.cashu.KeysetInfo
import com.bitchat.android.wallet.cashu.MeltQuote
import com.bitchat.android.wallet.cashu.MeltQuoteState
import com.bitchat.android.wallet.cashu.MethodSetting
import com.bitchat.android.wallet.cashu.MintInfo
import com.bitchat.android.wallet.cashu.MintQuote
import com.bitchat.android.wallet.cashu.MintQuoteState
import com.bitchat.android.wallet.cashu.P2pk
import com.bitchat.android.wallet.cashu.Proof
import com.bitchat.android.wallet.cashu.ProofState
import com.bitchat.android.wallet.cashu.ProofStateResult
import com.bitchat.android.wallet.cashu.Secp256k1
import com.bitchat.android.wallet.mint.CashuErrorCode
import com.bitchat.android.wallet.mint.MintApi
import com.bitchat.android.wallet.mint.MintException
import com.google.gson.JsonParser
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger

/**
 * In-process Cashu mint with real BDHKE signing, NUT-12 DLEQ (deterministic nonces per the
 * spec vectors), NUT-11 P2PK enforcement, NUT-02 fees, NUT-08 change and NUT-09 restore.
 *
 * Fault injection: [faults] decides, per call, whether the request is dropped before the mint
 * acts ([Fault.DropRequest]), whether the mint acts and the response is lost
 * ([Fault.DropResponse]), or whether it answers 5xx. [offline] fails every call.
 */
class FakeMint(
    override val url: String = "https://mint.test",
    feePpk: Long = 0,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    private val units: List<String> = listOf("sat"),
    private val seed: Int = 1,
) : MintApi {
    enum class Fault { DropRequest, DropResponse, ServerError }
    enum class MeltOutcome { PAY, FAIL, PENDING }

    var offline = false
    var faults: (call: String) -> Fault? = { null }
    var mintQuotesPaid = true
    var meltOutcome = MeltOutcome.PAY
    var meltFeeReserve = 4L
    var meltActualFee = 1L
    var quoteTtlSeconds = 600L
    var supportsP2pk = true
    val calls = mutableListOf<String>()

    private class KeysetPrivate(val keyset: Keyset, val privateKeys: Map<Long, BigInteger>)

    private val keysets = mutableListOf<KeysetPrivate>()
    private val spent = HashSet<String>()
    private val pending = HashSet<String>()
    private val signed = HashMap<String, BlindSignature>()
    private val mintQuotes = HashMap<String, MintQuote>()
    private val meltQuotes = HashMap<String, MeltQuote>()
    private val meltChange = HashMap<String, List<BlindSignature>>()
    private var quoteCounter = 0
    private var generation = 0

    init {
        units.forEach { addKeyset(it, feePpk) }
    }

    /** Deactivates current keysets and adds fresh ones, as a mint operator rotating keys would. */
    fun rotateKeysets(feePpk: Long = keysets.first().keyset.inputFeePpk) {
        val old = keysets.toList()
        keysets.clear()
        old.forEach { keysets += KeysetPrivate(it.keyset.copy(active = false), it.privateKeys) }
        units.forEach { addKeyset(it, feePpk) }
    }

    fun activeKeysetId(unit: String = "sat"): String = keysets.first { it.keyset.active && it.keyset.unit == unit }.keyset.id

    fun isSpent(proof: Proof) = proof.y() in spent

    /** Marks a mint quote paid (when [mintQuotesPaid] is false). */
    fun pay(quote: String) {
        mintQuotes[quote]?.let { mintQuotes[quote] = it.copy(state = MintQuoteState.PAID, amountPaid = it.amount) }
    }

    fun expireQuotes() {
        mintQuotes.replaceAll { _, q -> q.copy(expiry = now() - 1) }
        meltQuotes.replaceAll { _, q -> q.copy(expiry = now() - 1) }
    }

    fun settlePendingMelts(outcome: MeltOutcome) {
        meltQuotes.values.filter { it.state == MeltQuoteState.PENDING }.forEach { q ->
            val ys = pendingInputs.remove(q.quote).orEmpty()
            pending.removeAll(ys.toSet())
            if (outcome == MeltOutcome.PAY) {
                spent.addAll(ys)
                meltQuotes[q.quote] = q.copy(state = MeltQuoteState.PAID, preimage = "00".repeat(32), change = meltChange[q.quote].orEmpty())
            } else {
                meltQuotes[q.quote] = q.copy(state = MeltQuoteState.UNPAID)
            }
        }
    }

    private val pendingInputs = HashMap<String, List<String>>()

    private fun addKeyset(unit: String, feePpk: Long) {
        generation++
        val priv = (0 until 21).associate { i ->
            val k = BigInteger(1, Secp256k1.sha256("fake-mint-$seed-$unit-$generation-$i".toByteArray())).mod(Secp256k1.N)
            (1L shl i) to k
        }
        val keys = priv.entries.associate { (amount, k) -> BigInteger.valueOf(amount) to Secp256k1.compressedHex(Secp256k1.publicKey(k)) }
        val id = KeysetIds.deriveV2(keys, unit, feePpk, null)
        keysets += KeysetPrivate(Keyset(id, unit, true, feePpk, null, keys), priv)
    }

    // MARK: - Fault plumbing

    private inline fun <T> handle(call: String, mutates: Boolean, block: () -> T): T {
        calls += call
        if (offline) throw MintException.Unreachable()
        when (faults(call)) {
            Fault.DropRequest -> throw MintException.Unreachable()
            Fault.ServerError -> throw MintException.ServerError(500)
            Fault.DropResponse -> {
                block()
                throw MintException.Unreachable()
            }
            null -> Unit
        }
        return block()
    }

    private fun reject(code: Int): Nothing = throw MintException.Rejected(code, 400)

    // MARK: - MintApi

    override suspend fun info(): MintInfo = handle("info", false) {
        MintInfo(
            name = "Fake Mint", pubkey = null, version = "fake/1", description = "test", descriptionLong = null,
            motd = null, iconUrl = null,
            supportedNuts = setOf(4, 5, 7, 8, 9, 10, 12) + (if (supportsP2pk) setOf(11) else emptySet()),
            mintMethods = units.map { MethodSetting("bolt11", it, 1, 500_000) },
            meltMethods = units.map { MethodSetting("bolt11", it, 1, 500_000) },
            mintingDisabled = false, meltingDisabled = false,
        )
    }

    override suspend fun keysets(): List<KeysetInfo> = handle("keysets", false) { keysets.map { it.keyset.info() } }

    override suspend fun keys(keysetId: String): Keyset = handle("keys", false) {
        keysets.firstOrNull { it.keyset.id == keysetId }?.keyset ?: reject(CashuErrorCode.KEYSET_UNKNOWN)
    }

    override suspend fun mintQuote(amount: Long, unit: String): MintQuote = handle("mintQuote", true) {
        if (unit !in units) reject(CashuErrorCode.UNIT_UNSUPPORTED)
        if (amount !in 1..500_000) reject(CashuErrorCode.AMOUNT_OUT_OF_RANGE)
        val id = "mq-${++quoteCounter}"
        val quote = MintQuote(
            quote = id, request = invoice(amount, quoteCounter), amount = amount, unit = unit,
            state = if (mintQuotesPaid) MintQuoteState.PAID else MintQuoteState.UNPAID,
            expiry = now() + quoteTtlSeconds, amountPaid = if (mintQuotesPaid) amount else 0, amountIssued = 0,
        )
        mintQuotes[id] = quote
        quote
    }

    override suspend fun checkMintQuote(quote: String): MintQuote = handle("checkMintQuote", false) {
        mintQuotes[quote] ?: throw MintException.Rejected(null, 404)
    }

    override suspend fun mint(quote: String, outputs: List<BlindedMessage>): List<BlindSignature> = handle("mint", true) {
        val q = mintQuotes[quote] ?: throw MintException.Rejected(null, 404)
        checkOutputs(outputs, q.unit)
        val total = outputs.sumOf { it.amount }
        if (q.mintable == 0L && q.state == MintQuoteState.UNPAID) reject(CashuErrorCode.QUOTE_NOT_PAID)
        if (q.mintable == 0L) reject(CashuErrorCode.QUOTE_ALREADY_ISSUED)
        if (total > q.mintable) reject(CashuErrorCode.TRANSACTION_UNBALANCED)
        val sigs = outputs.map(::sign)
        mintQuotes[quote] = q.copy(state = MintQuoteState.ISSUED, amountIssued = (q.amountIssued ?: 0) + total)
        sigs
    }

    override suspend fun meltQuote(request: String, unit: String): MeltQuote = handle("meltQuote", true) {
        if (unit !in units) reject(CashuErrorCode.UNIT_UNSUPPORTED)
        val amountSat = Regex("^lnbc(\\d+)n").find(request)?.groupValues?.get(1)?.toLong()?.let { it / 10 }
            ?: reject(CashuErrorCode.AMOUNTLESS_INVOICE)
        if (meltQuotes.values.any { it.request == request && it.state == MeltQuoteState.PAID }) reject(CashuErrorCode.INVOICE_ALREADY_PAID)
        val id = "melt-${++quoteCounter}"
        val q = MeltQuote(id, request, amountSat, unit, meltFeeReserve, MeltQuoteState.UNPAID, now() + quoteTtlSeconds)
        meltQuotes[id] = q
        q
    }

    override suspend fun checkMeltQuote(quote: String): MeltQuote = handle("checkMeltQuote", false) {
        meltQuotes[quote] ?: throw MintException.Rejected(null, 404)
    }

    override suspend fun melt(quote: String, inputs: List<Proof>, outputs: List<BlindedMessage>): MeltQuote = handle("melt", true) {
        val q = meltQuotes[quote] ?: throw MintException.Rejected(null, 404)
        if (q.state == MeltQuoteState.PAID) reject(CashuErrorCode.INVOICE_ALREADY_PAID)
        if (q.state == MeltQuoteState.PENDING) reject(CashuErrorCode.QUOTE_PENDING)
        if (q.expiry != null && now() > q.expiry) reject(CashuErrorCode.QUOTE_EXPIRED)
        val ys = verifyInputs(inputs, q.unit)
        val fee = inputFee(inputs)
        val inputTotal = inputs.sumOf { it.amount }
        if (inputTotal - fee < q.amount + q.feeReserve) reject(CashuErrorCode.TRANSACTION_UNBALANCED)
        outputs.forEach { if (it.b in signed) reject(CashuErrorCode.OUTPUTS_ALREADY_SIGNED) }
        when (meltOutcome) {
            MeltOutcome.FAIL -> {
                meltQuotes[quote] = q.copy(state = MeltQuoteState.UNPAID)
                reject(CashuErrorCode.LIGHTNING_PAYMENT_FAILED)
            }
            MeltOutcome.PENDING -> {
                pending.addAll(ys)
                pendingInputs[quote] = ys
                meltChange[quote] = change(inputTotal - fee - q.amount - meltActualFee, outputs, q.unit)
                val updated = q.copy(state = MeltQuoteState.PENDING)
                meltQuotes[quote] = updated
                updated
            }
            MeltOutcome.PAY -> {
                spent.addAll(ys)
                val change = change(inputTotal - fee - q.amount - meltActualFee, outputs, q.unit)
                val paid = q.copy(state = MeltQuoteState.PAID, preimage = "00".repeat(32), change = change)
                meltQuotes[quote] = paid
                paid
            }
        }
    }

    override suspend fun swap(inputs: List<Proof>, outputs: List<BlindedMessage>): List<BlindSignature> = handle("swap", true) {
        val unit = keysetFor(inputs.firstOrNull()?.keysetId ?: reject(CashuErrorCode.TRANSACTION_UNBALANCED)).keyset.unit
        checkOutputs(outputs, unit)
        val ys = verifyInputs(inputs, unit)
        if (inputs.sumOf { it.amount } - inputFee(inputs) != outputs.sumOf { it.amount }) reject(CashuErrorCode.TRANSACTION_UNBALANCED)
        spent.addAll(ys)
        outputs.map(::sign)
    }

    override suspend fun checkState(ys: List<String>): List<ProofStateResult> = handle("checkState", false) {
        ys.map { y ->
            ProofStateResult(y, when (y) {
                in spent -> ProofState.SPENT
                in pending -> ProofState.PENDING
                else -> ProofState.UNSPENT
            })
        }
    }

    override suspend fun restore(outputs: List<BlindedMessage>): List<Pair<BlindedMessage, BlindSignature>> = handle("restore", false) {
        outputs.mapNotNull { o -> signed[o.b]?.let { o.copy(amount = it.amount) to it } }
    }

    // MARK: - Mint internals

    private fun keysetFor(id: String): KeysetPrivate =
        keysets.firstOrNull { it.keyset.id == id } ?: reject(CashuErrorCode.KEYSET_UNKNOWN)

    private fun inputFee(inputs: List<Proof>): Long =
        (inputs.sumOf { keysetFor(it.keysetId).keyset.inputFeePpk } + 999) / 1000

    private fun checkOutputs(outputs: List<BlindedMessage>, unit: String) {
        if (outputs.map { it.b }.toSet().size != outputs.size) reject(CashuErrorCode.TRANSACTION_UNBALANCED)
        outputs.forEach { o ->
            val ks = keysetFor(o.keysetId)
            if (!ks.keyset.active) reject(CashuErrorCode.KEYSET_INACTIVE)
            if (ks.keyset.unit != unit) reject(CashuErrorCode.UNIT_MISMATCH)
            if (o.b in signed) reject(CashuErrorCode.OUTPUTS_ALREADY_SIGNED)
        }
    }

    private fun verifyInputs(inputs: List<Proof>, unit: String): List<String> {
        val ys = inputs.map { it.y() }
        if (ys.toSet().size != ys.size) reject(CashuErrorCode.TRANSACTION_UNBALANCED)
        inputs.forEachIndexed { i, p ->
            val ks = keysetFor(p.keysetId)
            if (ks.keyset.unit != unit) reject(CashuErrorCode.UNIT_MISMATCH)
            if (p.dleq != null) throw AssertionError("wallet leaked DLEQ to the mint")
            val k = ks.privateKeys[p.amount] ?: reject(CashuErrorCode.PROOF_VERIFICATION_FAILED)
            val expected = Secp256k1.compressedHex(Bdhke.y(p.secret).multiply(k))
            if (expected != p.c.lowercase()) reject(CashuErrorCode.PROOF_VERIFICATION_FAILED)
            if (ys[i] in spent) reject(CashuErrorCode.PROOFS_SPENT)
            if (ys[i] in pending) reject(CashuErrorCode.PROOFS_PENDING)
            verifySpendingCondition(p)
        }
        return ys
    }

    private fun verifySpendingCondition(p: Proof) {
        val kind = P2pk.kind(p.secret) ?: return
        if (kind != "P2PK" || !supportsP2pk) return // unsupported conditions are anyone-can-spend at such a mint
        val c = runCatching { P2pk.parse(p.secret) }.getOrNull() ?: reject(CashuErrorCode.PROOF_VERIFICATION_FAILED)
        val signatures = runCatching {
            JsonParser.parseString(p.witness ?: "").asJsonObject.getAsJsonArray("signatures").map { it.asString }
        }.getOrDefault(emptyList())
        fun signedBy(keys: List<String>) = keys.any { key -> signatures.any { P2pk.verify(p.secret, it, key) } }
        val expired = c.locktime != null && now() > c.locktime
        val ok = signedBy(c.lockKeys) || (expired && (c.refundKeys.isEmpty() || signedBy(c.refundKeys)))
        if (!ok) reject(CashuErrorCode.PROOF_VERIFICATION_FAILED)
    }

    private fun change(overpaid: Long, outputs: List<BlindedMessage>, unit: String): List<BlindSignature> {
        if (overpaid <= 0 || outputs.isEmpty()) return emptyList()
        val amounts = (0 until 62).map { 1L shl it }.filter { overpaid and it != 0L }
        return amounts.take(outputs.size).mapIndexed { i, a -> sign(outputs[i].copy(amount = a)) }
    }

    private fun sign(output: BlindedMessage): BlindSignature {
        val ks = keysetFor(output.keysetId)
        val k = ks.privateKeys[output.amount] ?: reject(CashuErrorCode.TRANSACTION_UNBALANCED)
        val b = runCatching { Secp256k1.decodePoint(output.b) }.getOrElse { reject(CashuErrorCode.PROOF_VERIFICATION_FAILED) }
        val c = b.multiply(k).normalize()
        val (e, s) = dleq(k, b, c)
        return BlindSignature(output.amount, output.keysetId, Secp256k1.compressedHex(c), DleqProof(e, s)).also {
            signed[output.b] = it
        }
    }

    companion object {
        /** A BOLT-11-shaped fake invoice: `lnbc<amount*10>n1...` is `amount` sats. */
        fun invoice(amountSat: Long, nonce: Int): String =
            "lnbc${amountSat * 10}n1p" + "q".repeat(24) + nonce.toString().map { "qpzry9x8gf"[it - '0'] }.joinToString("")

        /** Mint-side DLEQ with the deterministic nonce from NUT-12. */
        fun dleq(a: BigInteger, b: ECPoint, c: ECPoint): Pair<String, String> {
            val bigA = Secp256k1.publicKey(a)
            var ctr = 0
            while (true) {
                val mac = HMac(SHA256Digest())
                mac.init(KeyParameter(Secp256k1.scalarBytes(a)))
                val data = "Cashu_DLEQ_R_v1".toByteArray() + Secp256k1.uncompressed(bigA) + Secp256k1.uncompressed(b) +
                    Secp256k1.uncompressed(c) + byteArrayOf(ctr.toByte())
                mac.update(data, 0, data.size)
                val out = ByteArray(32).also { mac.doFinal(it, 0) }
                val r = BigInteger(1, out)
                if (r.signum() == 0 || r >= Secp256k1.N) { ctr++; continue }
                val r1 = Secp256k1.G.multiply(r).normalize()
                val r2 = b.multiply(r).normalize()
                val e = BigInteger(1, Bdhke.hashE(r1, r2, bigA, c))
                val s = r.add(e.multiply(a)).mod(Secp256k1.N)
                return Hex.encode(Secp256k1.scalarBytes(e)) to Hex.encode(Secp256k1.scalarBytes(s))
            }
        }
    }
}
