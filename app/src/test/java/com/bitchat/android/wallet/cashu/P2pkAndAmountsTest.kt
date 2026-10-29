package com.bitchat.android.wallet.cashu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pkAndAmountsTest {

    @Test
    fun `NUT-11 vector signature verifies and a signature on another secret does not`() {
        val secret = """["P2PK",{"nonce":"859d4935c4907062a6297cf4e663e2835d90d97ecdd510745d32f6816323a41f","data":"0249098aa8b9d2fbec49ff8598feb17b592b986e62319a4fa488a3dc36387157a7","tags":[["sigflag","SIG_INPUTS"]]}]"""
        val pub = "0249098aa8b9d2fbec49ff8598feb17b592b986e62319a4fa488a3dc36387157a7"
        assertTrue(P2pk.verify(secret,
            "60f3c9b766770b46caac1d27e1ae6b77c8866ebaeba0b9489fe6a15a837eaa6fcd6eaa825499c72ac342983983fd3ba3a8a41f56677cc99ffd73da68b59e1383", pub))
        assertFalse(P2pk.verify(secret,
            "83564aca48c668f50d022a426ce0ed19d3a9bdcffeeaee0dc1e7ea7e98e9eff1840fcc821724f623468c94f72a8b0a7280fa9ef5a54a1b130ef3055217f467b3", pub))
        assertEquals(pub, P2pk.parse(secret)!!.pubkey)
    }

    @Test
    fun `locked secrets round trip and our witness verifies`() {
        val key = Secp256k1.randomScalar()
        val refund = Secp256k1.randomScalar()
        val secret = P2pk.secret(
            pubkey = P2pk.compressedPublicKey(key),
            locktime = 1_900_000_000,
            refundKeys = listOf(P2pk.compressedPublicKey(refund)),
        )
        val condition = P2pk.parse(secret)!!
        assertEquals(1_900_000_000L, condition.locktime)
        assertEquals(listOf(P2pk.compressedPublicKey(refund)), condition.refundKeys)
        assertFalse(condition.sigAll)

        val witness = com.google.gson.JsonParser.parseString(P2pk.witness(secret, listOf(key))).asJsonObject
        val signature = witness.getAsJsonArray("signatures")[0].asString
        assertTrue(P2pk.verify(secret, signature, condition.pubkey))
        assertFalse(P2pk.verify(secret, signature, P2pk.compressedPublicKey(refund)))
    }

    @Test
    fun `malformed P2PK secrets are recognised as unspendable`() {
        val pub = P2pk.compressedPublicKey(Secp256k1.randomScalar())
        val duplicateTag = """["P2PK",{"nonce":"00","data":"$pub","tags":[["locktime","1"],["locktime","2"]]}]"""
        assertThrows(IllegalArgumentException::class.java) { P2pk.parse(duplicateTag) }
        val badSigs = """["P2PK",{"nonce":"00","data":"$pub","tags":[["n_sigs","3"]]}]"""
        assertThrows(IllegalArgumentException::class.java) { P2pk.parse(badSigs) }
        assertNull(P2pk.parse("a".repeat(64)))
        assertEquals("HTLC", P2pk.kind("""["HTLC",{"nonce":"00","data":"ff"}]"""))
    }

    @Test
    fun `x-only nostr keys map to compressed keys`() {
        val key = Secp256k1.randomScalar()
        val compressed = P2pk.compressedPublicKey(key)
        val fromXOnly = P2pk.fromXOnly(Secp256k1.xOnlyHex(compressed))
        assertTrue(P2pk.sameKey(compressed, fromXOnly))
    }

    @Test
    fun `split uses keyset denominations`() {
        val denominations = (0..20).map { 1L shl it }
        assertEquals(listOf(8L, 4L, 1L), Amounts.split(13, denominations))
        assertEquals(emptyList<Long>(), Amounts.split(0, denominations))
        assertThrows(IllegalArgumentException::class.java) { Amounts.split(3, listOf(2L, 4L)) }
    }

    @Test
    fun `input fees round up per NUT-02`() {
        assertEquals(1L, Amounts.inputFee(3, 100))
        assertEquals(1L, Amounts.inputFee(10, 100))
        assertEquals(2L, Amounts.inputFee(11, 100))
        assertEquals(0L, Amounts.inputFee(5, 0))
    }

    @Test
    fun `blank output count follows NUT-08`() {
        assertEquals(0, Amounts.blankOutputCount(0))
        assertEquals(1, Amounts.blankOutputCount(1))
        assertEquals(1, Amounts.blankOutputCount(2))
        assertEquals(2, Amounts.blankOutputCount(3))
        assertEquals(10, Amounts.blankOutputCount(1000))
        assertEquals(10, Amounts.blankOutputCount(1024))
    }

    @Test
    fun `selection covers amount plus its own fee and prefers inactive keysets`() {
        fun proof(amount: Long, keyset: String = "01a") = Proof(amount, keyset, "s$amount$keyset", "02")
        val proofs = listOf(proof(64), proof(32), proof(8), proof(4), proof(1), proof(16, "01old"))
        val fees = mapOf("01a" to 100L, "01old" to 100L)
        val selection = Amounts.select(proofs, 20, fees, inactiveKeysets = setOf("01old"))!!
        assertTrue(selection.proofs.any { it.keysetId == "01old" })
        assertTrue(selection.total >= 20 + selection.fee)
        assertNull(Amounts.select(proofs, 1000, fees))
        assertNull(Amounts.select(proofs, 0, fees))
    }

    @Test
    fun `exact match finds offline sendable coins or nothing`() {
        fun proof(amount: Long) = Proof(amount, "01a", "s$amount", "02")
        val proofs = listOf(proof(8), proof(4), proof(2), proof(2))
        assertEquals(12L, Amounts.exactMatch(proofs, 12)!!.sumOf { it.amount })
        assertEquals(10L, Amounts.exactMatch(proofs, 10)!!.sumOf { it.amount })
        assertNull(Amounts.exactMatch(proofs, 17))
        assertNull(Amounts.exactMatch(proofs, 1))
    }
}
