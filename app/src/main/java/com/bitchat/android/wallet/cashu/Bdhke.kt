package com.bitchat.android.wallet.cashu

import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Blind Diffie-Hellman key exchange (NUT-00) and DLEQ verification (NUT-12).
 *
 * The wallet side only: blinding, unblinding, `Y = hash_to_curve(secret)` for state
 * checks, and checking the mint's DLEQ proofs. Mint-side signing lives in the test
 * double, never in production code.
 */
internal object Bdhke {
    private val DOMAIN_SEPARATOR = "Secp256k1_HashToCurve_Cashu_".toByteArray(Charsets.UTF_8)

    fun hashToCurve(message: ByteArray): ECPoint {
        val msgHash = Secp256k1.sha256(DOMAIN_SEPARATOR, message)
        val counter = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 65_536) {
            counter.clear()
            counter.putInt(i)
            val candidate = byteArrayOf(0x02) + Secp256k1.sha256(msgHash, counter.array())
            val point = runCatching { Secp256k1.decodePoint(candidate) }.getOrNull()
            if (point != null) return point
        }
        // 2^-65536 probability; treat as a programming error rather than looping forever.
        error("hash_to_curve found no point")
    }

    /** `Y` for a proof secret, as used by NUT-07 state checks. Secrets are UTF-8 strings. */
    fun y(secret: String): ECPoint = hashToCurve(secret.toByteArray(Charsets.UTF_8))

    fun yHex(secret: String): String = Secp256k1.compressedHex(y(secret))

    /** `B_ = Y + rG`. */
    fun blind(secret: String, r: BigInteger): ECPoint =
        y(secret).add(Secp256k1.G.multiply(r)).normalize()

    /** `C = C_ - rK`. */
    fun unblind(blindSignature: ECPoint, r: BigInteger, mintKey: ECPoint): ECPoint =
        blindSignature.subtract(mintKey.multiply(r)).normalize()

    /** `hash(R1, R2, A, C_)` over uncompressed hex strings, exactly as NUT-12 specifies. */
    fun hashE(vararg points: ECPoint): ByteArray {
        val joined = points.joinToString("") { Hex.encode(Secp256k1.uncompressed(it)) }
        return Secp256k1.sha256(joined.toByteArray(Charsets.UTF_8))
    }

    /**
     * Verifies a DLEQ proof that the mint key behind [mintKey] produced [blindSignature] on
     * [blindedMessage]. Any malformed input yields `false` rather than an exception.
     */
    fun verifyDleq(
        e: String,
        s: String,
        mintKey: ECPoint,
        blindedMessage: ECPoint,
        blindSignature: ECPoint,
    ): Boolean = runCatching {
        val eInt = BigInteger(1, Hex.decode(e).also { require(it.size == 32) })
        val sInt = BigInteger(1, Hex.decode(s).also { require(it.size == 32) })
        require(eInt < Secp256k1.N && sInt < Secp256k1.N)
        val r1 = Secp256k1.G.multiply(sInt).subtract(mintKey.multiply(eInt)).normalize()
        val r2 = blindedMessage.multiply(sInt).subtract(blindSignature.multiply(eInt)).normalize()
        if (r1.isInfinity || r2.isInfinity) return@runCatching false
        hashE(r1, r2, mintKey, blindSignature).contentEquals(Hex.decode(e))
    }.getOrDefault(false)

    /**
     * Carol-side check of a proof received from another user (NUT-12): rebuild `B_` and `C_`
     * from the revealed blinding factor and verify the mint's DLEQ.
     */
    fun verifyProofDleq(proof: Proof, mintKey: ECPoint): Boolean = runCatching {
        val dleq = proof.dleq ?: return@runCatching false
        val r = Secp256k1.scalar(dleq.r ?: return@runCatching false)
        val c = Secp256k1.decodePoint(proof.c)
        val blindSignature = c.add(mintKey.multiply(r)).normalize()
        val blindedMessage = blind(proof.secret, r)
        verifyDleq(dleq.e, dleq.s, mintKey, blindedMessage, blindSignature)
    }.getOrDefault(false)
}
