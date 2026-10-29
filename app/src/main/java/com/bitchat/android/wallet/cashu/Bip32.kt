package com.bitchat.android.wallet.cashu

import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import java.math.BigInteger
import java.nio.ByteBuffer

/**
 * Minimal BIP-32 private derivation, enough for NUT-13's legacy secret paths and its P2PK
 * key path. Public-only derivation and serialization (xpub/xprv) are deliberately absent.
 */
internal object Bip32 {
    private const val HARDENED = 0x80000000L

    class ExtendedKey(val key: BigInteger, val chainCode: ByteArray)

    fun master(seed: ByteArray): ExtendedKey {
        val i = hmacSha512("Bitcoin seed".toByteArray(Charsets.US_ASCII), seed)
        val key = BigInteger(1, i.copyOfRange(0, 32))
        require(key.signum() > 0 && key < Secp256k1.N) { "invalid master key" }
        return ExtendedKey(key, i.copyOfRange(32, 64))
    }

    fun child(parent: ExtendedKey, index: Long): ExtendedKey {
        require(index in 0..0xFFFFFFFFL) { "index out of range" }
        val data = if (index >= HARDENED) {
            byteArrayOf(0) + Secp256k1.scalarBytes(parent.key)
        } else {
            Secp256k1.compressed(Secp256k1.publicKey(parent.key))
        } + ByteBuffer.allocate(4).putInt(index.toInt()).array()
        val i = hmacSha512(parent.chainCode, data)
        val il = BigInteger(1, i.copyOfRange(0, 32))
        // BIP-32 says to skip to the next index on these; at 2^-127 odds we fail closed instead.
        require(il < Secp256k1.N) { "invalid child" }
        val key = il.add(parent.key).mod(Secp256k1.N)
        require(key.signum() > 0) { "invalid child" }
        return ExtendedKey(key, i.copyOfRange(32, 64))
    }

    /** Derives `m/a'/b/...` style paths. */
    fun derive(seed: ByteArray, path: String): ExtendedKey {
        val segments = path.split('/')
        require(segments.first() == "m") { "path must start with m" }
        return segments.drop(1).fold(master(seed)) { key, segment ->
            val hardened = segment.endsWith("'") || segment.endsWith("h")
            val number = segment.trimEnd('\'', 'h').toLong()
            require(number in 0 until HARDENED) { "index out of range" }
            child(key, if (hardened) number + HARDENED else number)
        }
    }

    private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = HMac(SHA512Digest())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        return ByteArray(64).also { mac.doFinal(it, 0) }
    }
}
