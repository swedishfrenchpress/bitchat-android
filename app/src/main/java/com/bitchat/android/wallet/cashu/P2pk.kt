package com.bitchat.android.wallet.cashu

import com.bitchat.android.nostr.NostrCrypto
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger

/**
 * NUT-10 well-known secrets and NUT-11 pay-to-public-key.
 *
 * The wallet only produces `SIG_INPUTS` locks (one signature per proof), which is what every
 * Cashu wallet can redeem. It can parse anything so it can tell the person honestly whether a
 * received token is spendable by them.
 */
internal object P2pk {

    data class Condition(
        /** Compressed pubkey in `data`. */
        val pubkey: String,
        val locktime: Long?,
        val refundKeys: List<String>,
        val additionalKeys: List<String>,
        val nSigs: Int,
        val nSigsRefund: Int,
        val sigAll: Boolean,
    ) {
        /** All keys that can sign before locktime. */
        val lockKeys: List<String> get() = listOf(pubkey) + additionalKeys
    }

    /** Builds a P2PK secret string. Tag values are strings, per NUT-10. */
    fun secret(
        pubkey: String,
        locktime: Long? = null,
        refundKeys: List<String> = emptyList(),
        nonce: String = Hex.encode(Secp256k1.randomBytes(32)),
    ): String {
        require(Secp256k1.isCompressedPublicKey(pubkey)) { "invalid pubkey" }
        refundKeys.forEach { require(Secp256k1.isCompressedPublicKey(it)) { "invalid refund key" } }
        val tags = JsonArray()
        tags.add(JsonArray().apply { add("sigflag"); add("SIG_INPUTS") })
        if (locktime != null) tags.add(JsonArray().apply { add("locktime"); add(locktime.toString()) })
        if (refundKeys.isNotEmpty()) tags.add(JsonArray().apply { add("refund"); refundKeys.forEach(::add) })
        val body = JsonObject().apply {
            addProperty("nonce", nonce)
            addProperty("data", pubkey.lowercase())
            add("tags", tags)
        }
        return JsonArray().apply { add("P2PK"); add(body) }.toString()
    }

    /** Kind of a NUT-10 secret, or null for a plain random secret. */
    fun kind(secret: String): String? {
        if (!secret.startsWith("[")) return null
        return runCatching {
            JsonParser.parseString(secret).asJsonArray.get(0).asString
        }.getOrNull()
    }

    /**
     * Parses a P2PK secret. Returns null for non-P2PK secrets. Throws for P2PK secrets that are
     * malformed under NUT-11 (duplicate tags, bad n_sigs), which are unspendable by anyone.
     */
    fun parse(secret: String): Condition? {
        if (kind(secret) != "P2PK") return null
        val body = JsonParser.parseString(secret).asJsonArray.get(1).asJsonObject
        val data = body.get("data").asString.lowercase()
        require(Secp256k1.isCompressedPublicKey(data)) { "bad data key" }
        val tags = body.get("tags")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        val seen = HashSet<String>()
        var locktime: Long? = null
        var refund = emptyList<String>()
        var pubkeys = emptyList<String>()
        var nSigs: Int? = null
        var nSigsRefund: Int? = null
        var sigAll = false
        for (tagElement in tags) {
            val tag = tagElement.asJsonArray.map { it.asString }
            require(tag.isNotEmpty()) { "empty tag" }
            val key = tag[0]
            require(seen.add(key)) { "duplicate tag" }
            val values = tag.drop(1)
            when (key) {
                "locktime" -> locktime = values.firstOrNull()?.toLongOrNull()
                "refund" -> refund = values.map { it.lowercase() }
                "pubkeys" -> pubkeys = values.map { it.lowercase() }
                "n_sigs" -> nSigs = values.firstOrNull()?.toIntOrNull()
                "n_sigs_refund" -> nSigsRefund = values.firstOrNull()?.toIntOrNull()
                "sigflag" -> sigAll = when (values.firstOrNull()) {
                    "SIG_ALL" -> true
                    "SIG_INPUTS", null -> false
                    else -> throw IllegalArgumentException("bad sigflag")
                }
            }
        }
        val lockCount = 1 + pubkeys.size
        val required = nSigs ?: 1
        require(required in 1..lockCount) { "bad n_sigs" }
        val requiredRefund = nSigsRefund ?: 1
        if (refund.isNotEmpty()) require(requiredRefund in 1..refund.size) { "bad n_sigs_refund" }
        require((listOf(data) + pubkeys).map(Secp256k1::xOnlyHex).toSet().size == lockCount) { "duplicate key" }
        return Condition(data, locktime, refund, pubkeys, required, requiredRefund, sigAll)
    }

    /**
     * Signs [secret] with [privateKey] and returns the NUT-11 witness JSON. BIP-340 signatures
     * over `SHA256(secret)` are produced by Bitchat's existing Schnorr implementation.
     */
    fun witness(secret: String, privateKeys: List<BigInteger>): String {
        val message = Secp256k1.sha256(secret.toByteArray(Charsets.UTF_8))
        val signatures = JsonArray()
        privateKeys.forEach { key ->
            signatures.add(NostrCrypto.schnorrSign(message, Hex.encode(Secp256k1.scalarBytes(key))))
        }
        return JsonObject().apply { add("signatures", signatures) }.toString()
    }

    fun verify(secret: String, signatureHex: String, pubkey: String): Boolean = runCatching {
        val message = Secp256k1.sha256(secret.toByteArray(Charsets.UTF_8))
        NostrCrypto.schnorrVerify(message, signatureHex, Secp256k1.xOnlyHex(pubkey))
    }.getOrDefault(false)

    /** True if two compressed keys share an x-coordinate (NUT-11 canonicalisation). */
    fun sameKey(a: String, b: String): Boolean = Secp256k1.xOnlyHex(a) == Secp256k1.xOnlyHex(b)

    /** Compressed form of a 32-byte x-only (Nostr) key, assuming even y as BIP-340 does. */
    fun fromXOnly(xOnlyHex: String): String {
        require(Hex.isHex(xOnlyHex, 32)) { "invalid x-only key" }
        return "02" + xOnlyHex.lowercase()
    }

    fun compressedPublicKey(privateKey: BigInteger): String =
        Secp256k1.compressedHex(Secp256k1.publicKey(privateKey))
}
