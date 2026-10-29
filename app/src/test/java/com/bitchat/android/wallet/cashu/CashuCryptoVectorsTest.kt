package com.bitchat.android.wallet.cashu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/** Golden vectors from the cashubtc/nuts test suites (NUT-00, NUT-02, NUT-12). */
class CashuCryptoVectorsTest {

    @Test
    fun `hash_to_curve matches NUT-00 vectors including multi-iteration case`() {
        val vectors = mapOf(
            "0000000000000000000000000000000000000000000000000000000000000000" to
                "024cce997d3b518f739663b757deaec95bcd9473c30a14ac2fd04023a739d1a725",
            "0000000000000000000000000000000000000000000000000000000000000001" to
                "022e7158e11c9506f1aa4248bf531298daa7febd6194f003edcd9b93ade6253acf",
            "0000000000000000000000000000000000000000000000000000000000000002" to
                "026cdbe15362df59cd1dd3c9c11de8aedac2106eca69236ecd9fbe117af897be4f",
        )
        vectors.forEach { (message, point) ->
            assertEquals(point, Secp256k1.compressedHex(Bdhke.hashToCurve(Hex.decode(message))))
        }
    }

    @Test
    fun `blinded messages match NUT-00 vectors`() {
        // The vectors hash the raw bytes of x, so blind via hashToCurve directly.
        fun blindRaw(x: String, r: String) = Secp256k1.compressedHex(
            Bdhke.hashToCurve(Hex.decode(x)).add(Secp256k1.G.multiply(BigInteger(r, 16))).normalize()
        )
        assertEquals(
            "033b1a9737a40cc3fd9b6af4b723632b76a67a36782596304612a6c2bfb5197e6d",
            blindRaw("d341ee4871f1f889041e63cf0d3823c713eea6aff01e80f1719f08f9e5be98f6",
                "99fce58439fc37412ab3468b73db0569322588f62fb3a49182d67e23d877824a")
        )
        assertEquals(
            "029bdf2d716ee366eddf599ba252786c1033f47e230248a4612a5670ab931f1763",
            blindRaw("f1aaf16c2239746f369572c0784d9dd3d032d952c2d992175873fb58fae31a60",
                "f78476ea7cc9ade20f9e05e58a804cf19533f03ea805ece5fee88c8e2874ba50")
        )
    }

    @Test
    fun `unblinding inverts a mint signature`() {
        val k = BigInteger("7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f", 16)
        val blinded = Secp256k1.decodePoint("02a9acc1e48c25eeeb9289b5031cc57da9fe72f3fe2861d264bdc074209b107ba2")
        assertEquals(
            "0398bc70ce8184d27ba89834d19f5199c84443c31131e48d3c1214db24247d005d",
            Secp256k1.compressedHex(blinded.multiply(k))
        )

        val secret = "test-secret"
        val r = Secp256k1.randomScalar()
        val mintKey = Secp256k1.publicKey(k)
        val c = Bdhke.unblind(Bdhke.blind(secret, r).multiply(k).normalize(), r, mintKey)
        assertEquals(Secp256k1.compressedHex(Bdhke.y(secret).multiply(k)), Secp256k1.compressedHex(c))
    }

    @Test
    fun `hash_e and DLEQ verification match NUT-12 vectors`() {
        val one = Secp256k1.decodePoint("020000000000000000000000000000000000000000000000000000000000000001")
        val c = Secp256k1.decodePoint("02a9acc1e48c25eeeb9289b5031cc57da9fe72f3fe2861d264bdc074209b107ba2")
        assertEquals(
            "a4dc034b74338c28c6bc3ea49731f2a24440fc7c4affc08b31a93fc9fbe6401e",
            Hex.encode(Bdhke.hashE(one, one, one, c))
        )

        val a = Secp256k1.decodePoint("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798")
        val b = Secp256k1.decodePoint("02a9acc1e48c25eeeb9289b5031cc57da9fe72f3fe2861d264bdc074209b107ba2")
        val e = "9818e061ee51d5c8edc3342369a554998ff7b4381c8652d724cdf46429be73d9"
        val s = "9818e061ee51d5c8edc3342369a554998ff7b4381c8652d724cdf46429be73da"
        assertTrue(Bdhke.verifyDleq(e, s, a, b, b))
        // Fail closed: a flipped bit or the wrong key must not verify.
        assertFalse(Bdhke.verifyDleq(e, s.replaceRange(63, 64, "b"), a, b, b))
        assertFalse(Bdhke.verifyDleq(e, s, Secp256k1.publicKey(BigInteger.TWO), b, b))
        assertFalse(Bdhke.verifyDleq("zz", s, a, b, b))
    }

    @Test
    fun `proof DLEQ from another user verifies offline`() {
        val a = Secp256k1.decodePoint("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798")
        val proof = Proof(
            amount = 1,
            keysetId = "00882760bfa2eb41",
            secret = "daf4dd00a2b68a0858a80450f52c8a7d2ccf87d375e43e216e0c571f089f63e9",
            c = "024369d2d22a80ecf78f3937da9d5f30c1b9f74f0c32684d583cca0fa6a61cdcfc",
            dleq = DleqProof(
                e = "b31e58ac6527f34975ffab13e70a48b6d2b0d35abc4b03f0151f09ee1a9763d4",
                s = "8fbae004c59e754d71df67e392b6ae4e29293113ddc2ec86592a0431d16306d8",
                r = "a6d13fcd7a18442e6076f5e1e7c887ad5de40a019824bdfa9fe740d302e8d861",
            ),
        )
        assertTrue(Bdhke.verifyProofDleq(proof, a))
        assertFalse(Bdhke.verifyProofDleq(proof.copy(secret = proof.secret.reversed()), a))
        assertFalse(Bdhke.verifyProofDleq(proof.copy(dleq = proof.dleq!!.copy(r = null)), a))
    }

    @Test
    fun `invalid points are rejected`() {
        assertFalse(Secp256k1.isCompressedPublicKey("02" + "00".repeat(32)))
        assertFalse(Secp256k1.isCompressedPublicKey("04" + "11".repeat(32)))
        assertFalse(Secp256k1.isCompressedPublicKey("not hex"))
        assertTrue(Secp256k1.isCompressedPublicKey("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"))
    }

    @Test
    fun `keyset ids derive per NUT-02 v1 and v2 vectors`() {
        val keys = mapOf(
            BigInteger.ONE to "03a40f20667ed53513075dc51e715ff2046cad64eb68960632269ba7f0210e38bc",
            BigInteger.TWO to "03fd4ce5a16b65576145949e6f99f445f8249fee17c606b688b504a849cdc452de",
            BigInteger.valueOf(4) to "02648eccfa4c026960966276fa5a4cae46ce0fd432211a4f449bf84f13aa5f8303",
            BigInteger.valueOf(8) to "02fdfd6796bfeac490cbee12f778f867f0a2c68f6508d17c649759ea0dc3547528",
        )
        assertEquals("00456a94ab4e1c46", KeysetIds.deriveV1(keys))
        assertEquals(
            "015ba18a8adcd02e715a58358eb618da4a4b3791151a4bee5e968bb88406ccf76a",
            KeysetIds.deriveV2(keys, "sat", 100, 2059210353)
        )
        val keyset = Keyset(
            "015ba18a8adcd02e715a58358eb618da4a4b3791151a4bee5e968bb88406ccf76a",
            "sat", true, 100, 2059210353, keys
        )
        assertTrue(KeysetIds.matches(keyset))
        assertFalse(KeysetIds.matches(keyset.copy(inputFeePpk = 0)))
        assertFalse(KeysetIds.matches(keyset.copy(keys = keys - BigInteger.ONE)))
    }

    @Test
    fun `keyset with denominations above Long range keeps usable amounts`() {
        val max = BigInteger.TWO.pow(63)
        val keyset = Keyset(
            "00ffffffffffffff", "sat", true, 0, null,
            mapOf(
                BigInteger.ONE to "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                max to "02c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
            )
        )
        assertEquals(listOf(1L), keyset.amounts)
        assertTrue(keyset.keyFor(1) != null)
    }
}
