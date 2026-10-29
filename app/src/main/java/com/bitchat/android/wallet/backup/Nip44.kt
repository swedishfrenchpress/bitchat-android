package com.bitchat.android.wallet.backup

import com.bitchat.android.wallet.cashu.Secp256k1
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64

/**
 * Standard NIP-44 v2 (ChaCha20 + HMAC-SHA256, padded), as NUT-27 requires for mint-list
 * backups so they restore in other Cashu wallets.
 *
 * This is deliberately separate from [com.bitchat.android.nostr.NostrCrypto.encryptNIP44],
 * whose XChaCha20 variant matches Bitchat's iOS client for direct messages.
 */
internal object Nip44 {
    private val SALT = "nip44-v2".toByteArray(Charsets.UTF_8)

    fun conversationKey(privateKey: BigInteger, xOnlyPublicKeyHex: String): ByteArray {
        val point = Secp256k1.decodePoint("02$xOnlyPublicKeyHex")
        val sharedX = Secp256k1.compressed(point.multiply(privateKey).normalize()).copyOfRange(1, 33)
        return hmac(SALT, sharedX)
    }

    fun encrypt(plaintext: String, conversationKey: ByteArray, nonce: ByteArray = Secp256k1.randomBytes(32)): String {
        require(nonce.size == 32) { "nonce must be 32 bytes" }
        val (chachaKey, chachaNonce, hmacKey) = messageKeys(conversationKey, nonce)
        val ciphertext = chacha(chachaKey, chachaNonce, pad(plaintext))
        val mac = hmac(hmacKey, nonce + ciphertext)
        return Base64.getEncoder().encodeToString(byteArrayOf(2) + nonce + ciphertext + mac)
    }

    fun decrypt(payload: String, conversationKey: ByteArray): String {
        require(payload.isNotEmpty() && payload[0] != '#') { "unsupported version" }
        val data = Base64.getDecoder().decode(payload)
        require(data.size in 99..65_603) { "invalid payload size" }
        require(data[0] == 2.toByte()) { "unsupported version" }
        val nonce = data.copyOfRange(1, 33)
        val ciphertext = data.copyOfRange(33, data.size - 32)
        val mac = data.copyOfRange(data.size - 32, data.size)
        val (chachaKey, chachaNonce, hmacKey) = messageKeys(conversationKey, nonce)
        require(MessageDigest.isEqual(hmac(hmacKey, nonce + ciphertext), mac)) { "invalid MAC" }
        return unpad(chacha(chachaKey, chachaNonce, ciphertext))
    }

    fun paddedLength(length: Int): Int {
        require(length >= 1) { "empty plaintext" }
        if (length <= 32) return 32
        val nextPower = 1 shl (32 - Integer.numberOfLeadingZeros(length - 1))
        val chunk = if (nextPower <= 256) 32 else nextPower / 8
        return chunk * ((length - 1) / chunk + 1)
    }

    private fun pad(plaintext: String): ByteArray {
        val bytes = plaintext.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..65_535) { "invalid plaintext length" }
        val out = ByteArray(2 + paddedLength(bytes.size))
        out[0] = (bytes.size ushr 8).toByte()
        out[1] = bytes.size.toByte()
        System.arraycopy(bytes, 0, out, 2, bytes.size)
        return out
    }

    private fun unpad(padded: ByteArray): String {
        val length = ((padded[0].toInt() and 0xff) shl 8) or (padded[1].toInt() and 0xff)
        require(length >= 1 && padded.size == 2 + paddedLength(length)) { "invalid padding" }
        return String(padded, 2, length, Charsets.UTF_8)
    }

    private fun messageKeys(conversationKey: ByteArray, nonce: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
        val generator = HKDFBytesGenerator(SHA256Digest())
        generator.init(HKDFParameters.skipExtractParameters(conversationKey, nonce))
        val keys = ByteArray(76).also { generator.generateBytes(it, 0, it.size) }
        return Triple(keys.copyOfRange(0, 32), keys.copyOfRange(32, 44), keys.copyOfRange(44, 76))
    }

    private fun chacha(key: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray {
        val engine = ChaCha7539Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce))
        return ByteArray(input.size).also { engine.processBytes(input, 0, input.size, it, 0) }
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = HMac(SHA256Digest())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        return ByteArray(32).also { mac.doFinal(it, 0) }
    }
}
