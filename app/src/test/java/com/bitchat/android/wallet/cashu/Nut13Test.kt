package com.bitchat.android.wallet.cashu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** NUT-13 deterministic secrets, BIP-39, and the NUT-27 backup key. */
class Nut13Test {
    private val vectorWords = "half depart obvious quality work element tank gorilla view sugar picture humble".split(" ")
    private val seed by lazy { Bip39.toSeed(vectorWords) }

    @Test
    fun `wordlist is the canonical BIP-39 english list`() {
        val joined = Bip39Wordlist.words.joinToString("\n", postfix = "\n")
        assertEquals(2048, Bip39Wordlist.words.size)
        assertEquals(
            "2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda",
            Hex.encode(Secp256k1.sha256(joined.toByteArray()))
        )
    }

    @Test
    fun `BIP-39 seed matches the reference vector`() {
        val words = Bip39.generate(ByteArray(16))
        assertEquals("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about", words.joinToString(" "))
        assertEquals(
            "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04",
            Hex.encode(Bip39.toSeed(words, "TREZOR"))
        )
    }

    @Test
    fun `mnemonic validation pinpoints the problem`() {
        assertEquals(Bip39.Validation.Valid, Bip39.validate(vectorWords))
        assertEquals(Bip39.Validation.WrongLength(11), Bip39.validate(vectorWords.dropLast(1)))
        assertEquals(
            Bip39.Validation.UnknownWords(listOf(3)),
            Bip39.validate(vectorWords.toMutableList().also { it[2] = "obvius" })
        )
        // Swapping two valid words keeps every word known but breaks the checksum.
        val swapped = vectorWords.toMutableList().also { it[0] = vectorWords[1]; it[1] = vectorWords[0] }
        assertEquals(Bip39.Validation.BadChecksum, Bip39.validate(swapped))
        assertEquals(vectorWords, Bip39.normalize("  Half DEPART obvious\nquality work element tank gorilla view sugar picture humble "))
    }

    @Test
    fun `generated mnemonics validate and differ`() {
        val a = Bip39.generate()
        val b = Bip39.generate()
        assertEquals(12, a.size)
        assertEquals(Bip39.Validation.Valid, Bip39.validate(a))
        assertNotEquals(a, b)
    }

    @Test
    fun `v1 legacy secrets and blinding factors match vectors`() {
        assertEquals(864559728L, Nut13.keysetIdInt("009a1f293253e41e"))
        val secrets = listOf(
            "485875df74771877439ac06339e284c3acfcd9be7abf3bc20b516faeadfe77ae",
            "8f2b39e8e594a4056eb1e6dbb4b0c38ef13b1b2c751f64f810ec04ee35b77270",
            "bc628c79accd2364fd31511216a0fab62afd4a18ff77a20deded7b858c9860c8",
            "59284fd1650ea9fa17db2b3acf59ecd0f2d52ec3261dd4152785813ff27a33bf",
            "576c23393a8b31cc8da6688d9c9a96394ec74b40fdaf1f693a6bb84284334ea0",
        )
        val rs = listOf(
            "ad00d431add9c673e843d4c2bf9a778a5f402b985b8da2d5550bf39cda41d679",
            "967d5232515e10b81ff226ecf5a9e2e2aff92d66ebc3edf0987eb56357fd6248",
            "b20f47bb6ae083659f3aa986bfa0435c55c6d93f687d51a01f26862d9b9a4899",
            "fb5fca398eb0b1deb955a2988b5ac77d32956155f1c002a373535211a2dfdc29",
            "5f09bfbfe27c439a597719321e061e2e40aad4a36768bb2bcc3de547c9644bf9",
        )
        for (counter in 0 until 5) {
            val derived = Nut13.derive(seed, "009a1f293253e41e", counter.toLong())
            assertEquals(secrets[counter], derived.secret)
            assertEquals(rs[counter], Hex.encode(Secp256k1.scalarBytes(derived.r)))
        }
    }

    @Test
    fun `v2 HMAC secrets and blinding factors match vectors`() {
        val keyset = "015ba18a8adcd02e715a58358eb618da4a4b3791151a4bee5e968bb88406ccf76a"
        val secrets = listOf(
            "db5561a07a6e6490f8dadeef5be4e92f7cebaecf2f245356b5b2a4ec40687298",
            "b70e7b10683da3bf1cdf0411206f8180c463faa16014663f39f2529b2fda922e",
            "78a7ac32ccecc6b83311c6081b89d84bb4128f5a0d0c5e1af081f301c7a513f5",
            "094a2b6c63bfa7970bc09cda0e1cfc9cd3d7c619b8e98fabcfc60aea9e4963e5",
            "5e89fc5d30d0bf307ddf0a3ac34aa7a8ee3702169dafa3d3fe1d0cae70ecd5ef",
        )
        val rs = listOf(
            "6d26181a3695e32e9f88b80f039ba1ae2ab5a200ad4ce9dbc72c6d3769f2b035",
            "bde4354cee75545bea1a2eee035a34f2d524cee2bb01613823636e998386952e",
            "f40cc1218f085b395c8e1e5aaa25dccc851be3c6c7526a0f4e57108f12d6dac4",
            "099ed70fc2f7ac769bc20b2a75cb662e80779827b7cc358981318643030577d0",
            "5550337312d223ba62e3f75cfe2ab70477b046d98e3e71804eade3956c7b98cf",
        )
        for (counter in 0 until 5) {
            val derived = Nut13.derive(seed, keyset, counter.toLong())
            assertEquals(secrets[counter], derived.secret)
            assertEquals(rs[counter], Hex.encode(Secp256k1.scalarBytes(derived.r)))
        }
    }

    @Test
    fun `P2PK keys derive along the NUT-13 path`() {
        val expected = listOf(
            "021693d45f4fdf610ae641fedb0944fb460fbb8264f21c19d2626c3da755fcbbcb",
            "0395461ab678058c0ed6aa39f38dda490eaa163e9ad27070b23ec3d06b41e07535",
            "02a05e4e593a633e9b4405f01c9632c8afde24cb613017a1aee56fd76291ad26d1",
            "033addea25c3873b93d67d536c61c9d9c993f6efd8b9dfa657951b66b5001e51dd",
            "03c964bdf42fc82b6c574615746eeca37527a24f1fdfc1b34a732c53843b5744a5",
        )
        expected.forEachIndexed { i, pub ->
            assertEquals(pub, P2pk.compressedPublicKey(Nut13.p2pkPrivateKey(seed, i.toLong())))
        }
    }

    @Test
    fun `NUT-27 backup key matches vector`() {
        val key = Nut13.mintBackupPrivateKey(seed)
        assertEquals("e7ca79469a270b36617e4227ff2f068d3bcbb6b072c8584190b0203597c53c0d", Hex.encode(Secp256k1.scalarBytes(key)))
        assertEquals(
            "0767277aaed200af7a8843491745272fc1ad2c7bfe340225e6f34f3a9a273aed",
            Secp256k1.xOnlyHex(P2pk.compressedPublicKey(key))
        )
    }

    @Test
    fun `secret redaction keeps material out of logs`() {
        val derived = Nut13.derive(seed, "009a1f293253e41e", 0)
        assertTrue(derived.toString().contains("redacted"))
        assertTrue(!derived.toString().contains(derived.secret))
    }
}
