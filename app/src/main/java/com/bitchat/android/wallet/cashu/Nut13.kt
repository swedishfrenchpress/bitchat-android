package com.bitchat.android.wallet.cashu

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import java.math.BigInteger
import java.nio.ByteBuffer

/**
 * Deterministic secrets (NUT-13) and the seed-derived keys built on the same seed.
 *
 * Everything the wallet blinds is derived from the BIP-39 seed plus a per-keyset counter, so
 * a lost device can be restored (NUT-09) and an interrupted request can be replayed with the
 * very same outputs.
 */
internal object Nut13 {
    private val KDF_DOMAIN = "Cashu_KDF_HMAC_SHA256".toByteArray(Charsets.UTF_8)
    private val BACKUP_DOMAIN = "cashu-mint-backup".toByteArray(Charsets.UTF_8)

    class SecretAndR(val secret: String, val r: BigInteger) {
        override fun toString(): String = "SecretAndR(<redacted>)"
    }

    fun derive(seed: ByteArray, keysetId: String, counter: Long): SecretAndR {
        require(counter >= 0) { "negative counter" }
        return when {
            keysetId.startsWith("01") -> deriveHmac(seed, keysetId, counter)
            keysetId.startsWith("00") -> deriveBip32(seed, keysetId, counter)
            else -> throw IllegalArgumentException("unsupported keyset version")
        }
    }

    private fun deriveHmac(seed: ByteArray, keysetId: String, counter: Long): SecretAndR {
        val message = KDF_DOMAIN + Hex.decode(keysetId) + ByteBuffer.allocate(8).putLong(counter).array()
        val secret = hmacSha256(seed, message + byteArrayOf(0x00))
        val r = BigInteger(1, hmacSha256(seed, message + byteArrayOf(0x01))).mod(Secp256k1.N)
        require(r.signum() > 0) { "derived r == 0" }
        return SecretAndR(Hex.encode(secret), r)
    }

    private fun deriveBip32(seed: ByteArray, keysetId: String, counter: Long): SecretAndR {
        require(counter < 0x80000000L) { "counter out of range" }
        val keysetInt = keysetIdInt(keysetId)
        val base = "m/129372'/0'/$keysetInt'/$counter'"
        val secret = Bip32.derive(seed, "$base/0").key
        val r = Bip32.derive(seed, "$base/1").key
        return SecretAndR(Hex.encode(Secp256k1.scalarBytes(secret)), r)
    }

    /** Deprecated keyset-to-integer mapping, still required for `00` keysets. */
    fun keysetIdInt(keysetId: String): Long {
        val bytes = Hex.decode(keysetId.take(16))
        return BigInteger(1, bytes).mod(BigInteger.valueOf(0x7FFFFFFFL)).toLong()
    }

    /** NUT-13 P2PK key `m/129373'/10'/0'/0'/{counter}`, used as our refund key for locked sends. */
    fun p2pkPrivateKey(seed: ByteArray, counter: Long): BigInteger =
        Bip32.derive(seed, "m/129373'/10'/0'/0'/$counter").key

    /** NUT-27 mint-list backup key: `SHA256(seed || "cashu-mint-backup")`. */
    fun mintBackupPrivateKey(seed: ByteArray): BigInteger =
        Secp256k1.scalar(Secp256k1.sha256(seed, BACKUP_DOMAIN))

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = HMac(SHA256Digest())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        return ByteArray(32).also { mac.doFinal(it, 0) }
    }
}
