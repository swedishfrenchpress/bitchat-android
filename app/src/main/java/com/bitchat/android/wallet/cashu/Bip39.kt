package com.bitchat.android.wallet.cashu

import java.text.Normalizer
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter

/** BIP-39 mnemonics (English wordlist, no passphrase by default). */
internal object Bip39 {
    sealed interface Validation {
        data object Valid : Validation
        data class WrongLength(val count: Int) : Validation
        /** 1-based positions of words that are not in the wordlist. */
        data class UnknownWords(val positions: List<Int>) : Validation
        data object BadChecksum : Validation
    }

    private val index: Map<String, Int> by lazy {
        Bip39Wordlist.words.withIndex().associate { it.value to it.index }
    }

    fun isWord(word: String): Boolean = word in index

    /** Words starting with [prefix], for the restore keyboard's suggestions. */
    fun suggestions(prefix: String, limit: Int = 4): List<String> =
        if (prefix.isEmpty()) emptyList()
        else Bip39Wordlist.words.asSequence().filter { it.startsWith(prefix) }.take(limit).toList()

    fun generate(entropy: ByteArray = Secp256k1.randomBytes(16)): List<String> {
        require(entropy.size in setOf(16, 20, 24, 28, 32)) { "invalid entropy length" }
        val checksumBits = entropy.size * 8 / 32
        val hash = Secp256k1.sha256(entropy)
        val bits = BooleanArray(entropy.size * 8 + checksumBits) { i ->
            val source = if (i < entropy.size * 8) entropy[i / 8] else hash[(i - entropy.size * 8) / 8]
            val bit = if (i < entropy.size * 8) i % 8 else (i - entropy.size * 8) % 8
            (source.toInt() shr (7 - bit)) and 1 == 1
        }
        return (0 until bits.size / 11).map { w ->
            var value = 0
            for (b in 0 until 11) value = (value shl 1) or (if (bits[w * 11 + b]) 1 else 0)
            Bip39Wordlist.words[value]
        }
    }

    fun normalize(input: String): List<String> =
        Normalizer.normalize(input, Normalizer.Form.NFKD)
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

    fun validate(words: List<String>): Validation {
        if (words.size !in setOf(12, 15, 18, 21, 24)) return Validation.WrongLength(words.size)
        val unknown = words.withIndex().filter { it.value !in index }.map { it.index + 1 }
        if (unknown.isNotEmpty()) return Validation.UnknownWords(unknown)
        val totalBits = words.size * 11
        val checksumBits = totalBits / 33
        val entropyBits = totalBits - checksumBits
        val bits = BooleanArray(totalBits)
        words.forEachIndexed { w, word ->
            val value = index.getValue(word)
            for (b in 0 until 11) bits[w * 11 + b] = (value shr (10 - b)) and 1 == 1
        }
        val entropy = ByteArray(entropyBits / 8) { i ->
            var v = 0
            for (b in 0 until 8) v = (v shl 1) or (if (bits[i * 8 + b]) 1 else 0)
            v.toByte()
        }
        val hash = Secp256k1.sha256(entropy)
        for (i in 0 until checksumBits) {
            val expected = (hash[i / 8].toInt() shr (7 - i % 8)) and 1 == 1
            if (bits[entropyBits + i] != expected) return Validation.BadChecksum
        }
        return Validation.Valid
    }

    /** PBKDF2-HMAC-SHA512, 2048 rounds, salt "mnemonic" + passphrase (NFKD). */
    fun toSeed(words: List<String>, passphrase: String = ""): ByteArray {
        val mnemonic = Normalizer.normalize(words.joinToString(" "), Normalizer.Form.NFKD)
        val salt = Normalizer.normalize("mnemonic$passphrase", Normalizer.Form.NFKD)
        // BouncyCastle's generator takes raw UTF-8 bytes, which is what BIP-39 specifies; the JCA
        // PBEKeySpec path mangles non-ASCII passwords on some Android versions.
        val generator = PKCS5S2ParametersGenerator(SHA512Digest())
        generator.init(mnemonic.toByteArray(Charsets.UTF_8), salt.toByteArray(Charsets.UTF_8), 2048)
        return (generator.generateDerivedParameters(512) as KeyParameter).key
    }
}
