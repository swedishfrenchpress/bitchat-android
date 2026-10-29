package com.bitchat.android.wallet.cashu

import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/** Strict lowercase hex codec. Rejects odd lengths and non-hex input instead of guessing. */
internal object Hex {
    private val DIGITS = "0123456789abcdef".toCharArray()

    fun encode(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        bytes.forEachIndexed { i, b ->
            val v = b.toInt() and 0xff
            out[i * 2] = DIGITS[v ushr 4]
            out[i * 2 + 1] = DIGITS[v and 0x0f]
        }
        return String(out)
    }

    fun decode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "odd hex length" }
        return ByteArray(hex.length / 2) { i ->
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "invalid hex" }
            ((hi shl 4) or lo).toByte()
        }
    }

    fun isHex(value: String, byteLength: Int? = null): Boolean =
        value.length % 2 == 0 &&
            (byteLength == null || value.length == byteLength * 2) &&
            value.all { Character.digit(it, 16) >= 0 }
}

/**
 * secp256k1 arithmetic for the Cashu primitives, on the same BouncyCastle backend as
 * [com.bitchat.android.nostr.NostrCrypto].
 *
 * Every point that arrives from the network goes through [decodePoint], which rejects
 * the point at infinity and anything not on the curve. A malicious mint must not be
 * able to steer the wallet's arithmetic with invalid keys.
 */
internal object Secp256k1 {
    private val params = CustomNamedCurves.getByName("secp256k1")
    val curve = params.curve
    val G: ECPoint = params.g
    val N: BigInteger = params.n
    private val secureRandom = SecureRandom()

    fun decodePoint(bytes: ByteArray): ECPoint {
        require(bytes.size == 33 || bytes.size == 65) { "invalid point length" }
        val point = curve.decodePoint(bytes)
        require(!point.isInfinity && point.isValid) { "invalid point" }
        return point.normalize()
    }

    fun decodePoint(hex: String): ECPoint = decodePoint(Hex.decode(hex))

    fun compressed(point: ECPoint): ByteArray = point.normalize().getEncoded(true)

    fun compressedHex(point: ECPoint): String = Hex.encode(compressed(point))

    fun uncompressed(point: ECPoint): ByteArray = point.normalize().getEncoded(false)

    /** A scalar in [1, n-1] from 32 big-endian bytes, or an exception. */
    fun scalar(bytes: ByteArray): BigInteger {
        require(bytes.size == 32) { "scalar must be 32 bytes" }
        val k = BigInteger(1, bytes)
        require(k.signum() > 0 && k < N) { "scalar out of range" }
        return k
    }

    fun scalar(hex: String): BigInteger = scalar(Hex.decode(hex))

    fun scalarBytes(k: BigInteger): ByteArray {
        val raw = k.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
            else -> ByteArray(32 - raw.size) + raw
        }
    }

    fun publicKey(privateKey: BigInteger): ECPoint = G.multiply(privateKey).normalize()

    fun randomScalar(): BigInteger {
        while (true) {
            val bytes = ByteArray(32).also(secureRandom::nextBytes)
            val k = BigInteger(1, bytes)
            if (k.signum() > 0 && k < N) return k
        }
    }

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    fun sha256(vararg parts: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach(digest::update)
        return digest.digest()
    }

    /** True if [hex] is a valid SEC1 compressed public key. */
    fun isCompressedPublicKey(hex: String): Boolean =
        Hex.isHex(hex, 33) && (hex.startsWith("02") || hex.startsWith("03")) &&
            runCatching { decodePoint(hex) }.isSuccess

    /** 32-byte x-only form used by BIP-340 / Nostr. */
    fun xOnlyHex(compressedHex: String): String = compressedHex.lowercase().substring(2)
}
