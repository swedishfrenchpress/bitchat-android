package com.bitchat.android.wallet.cashu

import com.bitchat.android.ui.CashuTokenDecoder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.util.Base64

/** A decoded single-mint token. Multi-mint V3 tokens are rejected (see `decisions.md`). */
data class Token(
    val mint: String,
    val unit: String,
    val proofs: List<Proof>,
    val memo: String? = null,
) {
    /** Sum of proof amounts; decoding guarantees this does not overflow. */
    val amount: Long get() = proofs.sumOf { it.amount }

    override fun toString(): String = "Token(mint=$mint, unit=$unit, amount=$amount, proofs=${proofs.size})"
}

sealed class TokenDecodeException(message: String) : Exception(message) {
    class NotAToken : TokenDecodeException("not a cashu token")
    class Malformed(reason: String) : TokenDecodeException(reason)
    class MultipleMints : TokenDecodeException("token mixes mints")
    class Empty : TokenDecodeException("token has no proofs")
    class AmountOutOfRange : TokenDecodeException("amount out of range")
}

/**
 * Full-fidelity token codec (NUT-00): decodes V3 (`cashuA`, JSON) and V4 (`cashuB`, CBOR),
 * and encodes V4. Unlike [CashuTokenDecoder], which only extracts display metadata, this keeps
 * every proof field so the wallet can redeem it.
 */
object TokenCodec {
    /** Upper bound for any single amount or total, comfortably above 21M BTC in sats. */
    const val MAX_AMOUNT = 2_100_000_000_000_000L
    private const val MAX_PROOFS = 2_000

    fun decode(raw: String): Token {
        val bare = CashuTokenDecoder.bareToken(raw) ?: throw TokenDecodeException.NotAToken()
        val payload = base64UrlDecode(bare.substring(6))
            ?: throw TokenDecodeException.Malformed("bad base64")
        val token = when (bare[5]) {
            'A' -> decodeV3(payload)
            'B' -> decodeV4(payload)
            else -> throw TokenDecodeException.NotAToken()
        }
        if (token.proofs.isEmpty()) throw TokenDecodeException.Empty()
        var total = 0L
        token.proofs.forEach { proof ->
            if (proof.amount <= 0 || proof.amount > MAX_AMOUNT) throw TokenDecodeException.AmountOutOfRange()
            total += proof.amount
            if (total > MAX_AMOUNT) throw TokenDecodeException.AmountOutOfRange()
        }
        return token
    }

    fun decodeOrNull(raw: String): Token? = runCatching { decode(raw) }.getOrNull()

    /** Encodes a V4 token. Keyset IDs are written in full for compatibility with older wallets. */
    fun encodeV4(token: Token): String {
        val groups = token.proofs.groupBy { it.keysetId }.map { (id, proofs) ->
            Cbor.map(
                "i" to Cbor.Bytes(Hex.decode(id)),
                "p" to Cbor.Array(proofs.map(::proofToCbor)),
            )
        }
        val root = Cbor.map(
            "t" to Cbor.Array(groups),
            "d" to token.memo?.let(Cbor::Text),
            "m" to Cbor.Text(MintUrl.normalize(token.mint)),
            "u" to Cbor.Text(token.unit),
        )
        return "cashuB" + Base64.getUrlEncoder().withoutPadding().encodeToString(Cbor.encode(root))
    }

    private fun proofToCbor(proof: Proof): Cbor = Cbor.map(
        "a" to Cbor.UInt(proof.amount),
        "s" to Cbor.Text(proof.secret),
        "c" to Cbor.Bytes(Hex.decode(proof.c)),
        "d" to proof.dleq?.let { dleq ->
            Cbor.map(
                "e" to Cbor.Bytes(Hex.decode(dleq.e)),
                "s" to Cbor.Bytes(Hex.decode(dleq.s)),
                "r" to dleq.r?.let { Cbor.Bytes(Hex.decode(it)) },
            )
        },
        "w" to proof.witness?.let(Cbor::Text),
    )

    private fun decodeV4(payload: ByteArray): Token {
        val root = try {
            Cbor.decode(payload) as? Cbor.Map
        } catch (e: CborException) {
            throw TokenDecodeException.Malformed(e.message ?: "bad cbor")
        } ?: throw TokenDecodeException.Malformed("root not a map")
        val mint = (root["m"] as? Cbor.Text)?.value ?: throw TokenDecodeException.Malformed("missing mint")
        val unit = (root["u"] as? Cbor.Text)?.value ?: throw TokenDecodeException.Malformed("missing unit")
        val memo = (root["d"] as? Cbor.Text)?.value
        val groups = root["t"] as? Cbor.Array ?: throw TokenDecodeException.Malformed("missing proofs")
        val proofs = ArrayList<Proof>()
        for (group in groups.items) {
            group as? Cbor.Map ?: throw TokenDecodeException.Malformed("bad group")
            val id = (group["i"] as? Cbor.Bytes)?.value?.let(Hex::encode)
                ?: throw TokenDecodeException.Malformed("missing keyset id")
            val list = group["p"] as? Cbor.Array ?: throw TokenDecodeException.Malformed("missing proofs")
            for (item in list.items) {
                item as? Cbor.Map ?: throw TokenDecodeException.Malformed("bad proof")
                val dleq = (item["d"] as? Cbor.Map)?.let { d ->
                    val e = (d["e"] as? Cbor.Bytes)?.value
                    val s = (d["s"] as? Cbor.Bytes)?.value
                    if (e == null || s == null) null
                    else DleqProof(Hex.encode(e), Hex.encode(s), (d["r"] as? Cbor.Bytes)?.value?.let(Hex::encode))
                }
                proofs += Proof(
                    amount = (item["a"] as? Cbor.UInt)?.value ?: throw TokenDecodeException.Malformed("missing amount"),
                    keysetId = id,
                    secret = (item["s"] as? Cbor.Text)?.value ?: throw TokenDecodeException.Malformed("missing secret"),
                    c = (item["c"] as? Cbor.Bytes)?.value?.let(Hex::encode)
                        ?: throw TokenDecodeException.Malformed("missing signature"),
                    dleq = dleq,
                    witness = (item["w"] as? Cbor.Text)?.value,
                )
                if (proofs.size > MAX_PROOFS) throw TokenDecodeException.Malformed("too many proofs")
            }
        }
        return Token(normalizeOrThrow(mint), unit, proofs, memo)
    }

    private fun decodeV3(payload: ByteArray): Token {
        val root = runCatching { JsonParser.parseString(String(payload, Charsets.UTF_8)).asJsonObject }
            .getOrElse { throw TokenDecodeException.Malformed("bad json") }
        val entries = root.get("token")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw TokenDecodeException.Malformed("missing token array")
        val nonEmpty = entries.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
            .filter { (it.get("proofs") as? JsonArray)?.size() ?: 0 > 0 }
        val mints = nonEmpty.mapNotNull { it.string("mint") }.map(::normalizeOrThrow).distinct()
        if (mints.size > 1) throw TokenDecodeException.MultipleMints()
        val mint = mints.firstOrNull() ?: throw TokenDecodeException.Empty()
        val proofs = ArrayList<Proof>()
        for (entry in nonEmpty) {
            for (element in entry.getAsJsonArray("proofs")) {
                val p = element.takeIf { it.isJsonObject }?.asJsonObject
                    ?: throw TokenDecodeException.Malformed("bad proof")
                val amount = runCatching { p.get("amount").asBigDecimal.longValueExact() }
                    .getOrElse { throw TokenDecodeException.Malformed("bad amount") }
                val witness = p.get("witness")?.let { w ->
                    when {
                        w.isJsonNull -> null
                        w.isJsonPrimitive -> w.asString
                        else -> w.toString()
                    }
                }
                proofs += Proof(
                    amount = amount,
                    keysetId = p.string("id") ?: throw TokenDecodeException.Malformed("missing keyset id"),
                    secret = p.string("secret") ?: throw TokenDecodeException.Malformed("missing secret"),
                    c = p.string("C") ?: throw TokenDecodeException.Malformed("missing signature"),
                    dleq = (p.get("dleq") as? JsonObject)?.let { d ->
                        val e = d.string("e")
                        val s = d.string("s")
                        if (e == null || s == null) null else DleqProof(e, s, d.string("r"))
                    },
                    witness = witness,
                )
                if (proofs.size > MAX_PROOFS) throw TokenDecodeException.Malformed("too many proofs")
            }
        }
        val unit = root.string("unit") ?: "sat"
        return Token(mint, unit, proofs, root.string("memo"))
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun normalizeOrThrow(url: String): String =
        runCatching { MintUrl.normalize(url) }.getOrElse { throw TokenDecodeException.Malformed("bad mint url") }

    internal fun base64UrlDecode(input: String): ByteArray? {
        val normalized = input.replace('-', '+').replace('_', '/').trimEnd('=')
        if (normalized.length % 4 == 1) return null
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return runCatching { Base64.getDecoder().decode(padded) }.getOrNull()
    }
}

/** Mint URL normalization shared by tokens, storage and the mint client. */
object MintUrl {
    /**
     * Lowercases scheme and host, drops trailing slashes, query and fragment. Plain `http` is
     * only accepted for `.onion` hosts and loopback (local test mints); everything else must
     * be `https` so mint traffic is never sent in the clear.
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim().let { if ("://" !in it) "https://$it" else it }
        val uri = URI(trimmed)
        val scheme = uri.scheme?.lowercase() ?: throw IllegalArgumentException("no scheme")
        val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("no host")
        require(uri.userInfo == null) { "credentials not allowed" }
        when (scheme) {
            "https" -> Unit
            "http" -> require(host.endsWith(".onion") || host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2") {
                "insecure scheme"
            }
            else -> throw IllegalArgumentException("unsupported scheme")
        }
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val path = (uri.rawPath ?: "").trimEnd('/')
        return "$scheme://$host$port$path"
    }

    fun normalizeOrNull(raw: String): String? = runCatching { normalize(raw) }.getOrNull()

    fun host(url: String): String = runCatching { URI(url).host }.getOrNull() ?: url
}
