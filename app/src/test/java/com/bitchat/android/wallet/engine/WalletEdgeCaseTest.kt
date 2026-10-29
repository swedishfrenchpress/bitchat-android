package com.bitchat.android.wallet.engine

import com.bitchat.android.wallet.cashu.BlindSignature
import com.bitchat.android.wallet.cashu.BlindedMessage
import com.bitchat.android.wallet.cashu.DleqProof
import com.bitchat.android.wallet.cashu.Hex
import com.bitchat.android.wallet.cashu.P2pk
import com.bitchat.android.wallet.cashu.Proof
import com.bitchat.android.wallet.cashu.Secp256k1
import com.bitchat.android.wallet.cashu.Token
import com.bitchat.android.wallet.cashu.TokenCodec
import com.bitchat.android.wallet.mint.MintApi
import com.bitchat.android.wallet.mint.MintException
import com.bitchat.android.wallet.testsupport.FakeMint
import com.bitchat.android.wallet.testsupport.TestClock
import com.bitchat.android.wallet.testsupport.TestSeeds
import com.bitchat.android.wallet.testsupport.WalletHarness
import com.bitchat.android.wallet.ui.AmountDigits
import com.bitchat.android.wallet.ui.BackupFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigInteger

/** Regression tests for the cases logged in docs/cashu/edge-cases.md. */
class WalletEdgeCaseTest {
    private val clock = TestClock()

    private fun mint(feePpk: Long = 0) = FakeMint(feePpk = feePpk, now = { clock.seconds })

    private fun harness(api: MintApi, url: String, words: List<String> = TestSeeds.ALICE, keys: List<BigInteger> = emptyList()) =
        WalletHarness(words, clock, mapOf(url to api), extraKeys = keys)

    private suspend fun expect(error: WalletError, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $error")
        } catch (e: WalletException) {
            assertEquals(error, e.error)
        }
    }

    @Test
    fun `partially double-spent token claims the unspent remainder`() = runTest {
        val m = mint()
        val alice = harness(m, m.url)
        val bob = harness(m, m.url, TestSeeds.BOB)
        val carol = harness(m, m.url, TestSeeds.CAROL)
        alice.fund(m, 13) // 8 + 4 + 1
        val full = TokenCodec.decode(alice.wallet.send(m.url, 13).token!!)
        // Alice double-spends the 8 to Carol before Bob claims.
        carol.wallet.addMint(m.url)
        carol.wallet.receive(TokenCodec.encodeV4(full.copy(proofs = full.proofs.filter { it.amount == 8L })))
        bob.wallet.addMint(m.url)
        val entry = bob.wallet.receive(TokenCodec.encodeV4(full))
        assertEquals(5L, entry.amount)
        assertEquals(HistoryNote.PARTIAL, entry.note)
        assertEquals(5L, bob.balance())
        assertEquals(1, bob.wallet.state.value.history.size)
        assertEquals(CashuWallet.CardStatus.CLAIMED_BY_US, bob.wallet.cardStatus(TokenCodec.encodeV4(full)))
    }

    @Test
    fun `mint returning a bad DLEQ keeps the coins and flags the mint`() = runTest {
        val m = mint()
        val lying = object : MintApi by m {
            override suspend fun mint(quote: String, outputs: List<BlindedMessage>): List<BlindSignature> =
                m.mint(quote, outputs).map { it.copy(dleq = DleqProof(Hex.encode(Secp256k1.randomBytes(32)), it.dleq!!.s)) }
        }
        val alice = harness(lying, m.url)
        alice.fund(m, 32)
        assertEquals(32L, alice.balance())
        assertTrue(alice.wallet.isSuspect(m.url))
        // Coins without a valid proof are sent without one rather than with a forged one.
        val token = TokenCodec.decode(alice.wallet.send(m.url, 32).token!!)
        assertTrue(token.proofs.all { it.dleq == null })
    }

    @Test
    fun `v4 tokens with short keyset ids resolve against the mint`() = runTest {
        val m = mint()
        val alice = harness(m, m.url)
        val bob = harness(m, m.url, TestSeeds.BOB)
        alice.fund(m, 16)
        val token = TokenCodec.decode(alice.wallet.send(m.url, 16).token!!)
        val short = token.copy(proofs = token.proofs.map { it.copy(keysetId = it.keysetId.take(16)) })
        bob.wallet.addMint(m.url)
        assertEquals(16L, bob.wallet.receive(TokenCodec.encodeV4(short)).amount)
    }

    @Test
    fun `malformed swap response is resolved through restore, not reported as failure`() = runTest {
        val m = mint()
        var garble = true
        val flaky = object : MintApi by m {
            override suspend fun swap(inputs: List<Proof>, outputs: List<BlindedMessage>): List<BlindSignature> {
                val real = m.swap(inputs, outputs)
                if (garble) {
                    garble = false
                    throw MintException.Malformed("truncated body")
                }
                return real
            }
        }
        val alice = harness(m, m.url)
        val bob = harness(flaky, m.url, TestSeeds.BOB)
        alice.fund(m, 24)
        val token = alice.wallet.send(m.url, 24).token!!
        bob.wallet.addMint(m.url)
        expect(WalletError.OutcomeUnknown) { bob.wallet.receive(token) }
        bob.wallet.recover()
        assertEquals(24L, bob.balance())
    }

    @Test
    fun `unpaid quote that expires is reported expired and never minted`() = runTest {
        val m = mint().apply { mintQuotesPaid = false; quoteTtlSeconds = 60 }
        val alice = harness(m, m.url)
        alice.wallet.addMint(m.url)
        val entry = alice.wallet.createMintQuote(m.url, 21)
        clock.advanceSeconds(120)
        alice.wallet.recover()
        assertEquals(HistoryStatus.EXPIRED, alice.wallet.state.value.history.single().status)
        assertFalse(m.calls.contains("mint"))
        assertEquals(entry.id, alice.wallet.state.value.history.single().id)
    }

    @Test
    fun `card status reflects every stage of a token's life`() = runTest {
        val m = mint()
        val bobKey = BigInteger(1, Secp256k1.sha256("bob-card".toByteArray())).mod(Secp256k1.N)
        val alice = harness(m, m.url)
        val bob = harness(m, m.url, TestSeeds.BOB, keys = listOf(bobKey))
        alice.fund(m, 64)
        bob.wallet.addMint(m.url)

        val sent = alice.wallet.send(m.url, 8).token!!
        assertEquals(CashuWallet.CardStatus.OUR_PENDING_SEND, alice.wallet.cardStatus(sent))
        assertEquals(CashuWallet.CardStatus.CLAIMABLE, bob.wallet.cardStatus(sent))
        bob.wallet.receive(sent)
        assertEquals(CashuWallet.CardStatus.CLAIMED_BY_US, bob.wallet.cardStatus(sent))
        alice.wallet.checkPendingSends()
        assertEquals(CashuWallet.CardStatus.OUR_SEND_CLAIMED, alice.wallet.cardStatus(sent))

        val locked = alice.wallet.send(m.url, 16, lock = CashuWallet.LockRequest(P2pk.compressedPublicKey(bobKey), clock.seconds + 3600)).token!!
        assertEquals(CashuWallet.CardStatus.LOCKED_TO_US, bob.wallet.cardStatus(locked))
        val carol = harness(m, m.url, TestSeeds.CAROL)
        assertEquals(CashuWallet.CardStatus.LOCKED_TO_OTHER, carol.wallet.cardStatus(locked))
        assertEquals(CashuWallet.CardStatus.UNREADABLE, bob.wallet.cardStatus("cashuBgarbage"))
    }

    @Test
    fun `reclaimed token reads as taken back in our chat`() = runTest {
        val m = mint()
        val alice = harness(m, m.url)
        alice.fund(m, 32)
        val sent = alice.wallet.send(m.url, 8)
        val token = sent.token!!
        alice.wallet.reclaim(sent.id)
        assertEquals(CashuWallet.CardStatus.OUR_SEND_RECLAIMED, alice.wallet.cardStatus(token))
    }

    @Test
    fun `token from a keyset the receiver has not seen yet triggers a refresh, not a refusal`() = runTest {
        val m = mint()
        val alice = harness(m, m.url)
        val bob = harness(m, m.url, TestSeeds.BOB)
        bob.wallet.addMint(m.url)
        m.rotateKeysets()
        alice.fund(m, 16)
        val token = alice.wallet.send(m.url, 16).token!!
        assertEquals(16L, bob.wallet.receive(token).amount)
    }

    @Test
    fun `paid melt without change in the response recovers the change through restore`() = runTest {
        val m = mint().apply { meltFeeReserve = 10; meltActualFee = 2 }
        val stingy = object : MintApi by m {
            override suspend fun melt(quote: String, inputs: List<Proof>, outputs: List<BlindedMessage>) =
                m.melt(quote, inputs, outputs).copy(change = emptyList())
        }
        val alice = harness(stingy, m.url)
        alice.fund(m, 1000)
        val paid = alice.wallet.melt(alice.wallet.previewMelt(m.url, FakeMint.invoice(200, 7)))
        assertEquals(HistoryStatus.COMPLETED, paid.status)
        assertEquals(798L, alice.balance())
    }

    @Test
    fun `amount digits refuse leading zeros and absurd lengths`() {
        var a = AmountDigits()
        assertNull(a.push(0))
        a = a.push(5)!!.push(0)!!
        assertEquals(50L, a.value)
        repeat(10) { a = a.push(9)!! }
        assertNull(a.push(9))
        assertNull(AmountDigits().pop())
        assertEquals(AmountDigits("21"), AmountDigits.of(21))
    }

    @Test
    fun `bolt11 parsing reads amounts and rejects junk`() {
        assertEquals(2000L, Bolt11.parse("lnbc20u1pqqqqqqqqqqqqqqqqqqqqqqqqq")!!.amountSat)
        assertEquals(1L, Bolt11.parse("LIGHTNING:lnbc10n1pqqqqqqqqqqqqqqqqqqqqqqqqqq")!!.amountSat)
        assertNull(Bolt11.parse("lnbc1pqqqqqqqqqqqqqqqqqqqqqqqqqqq")!!.amountMsat)
        assertEquals("tb", Bolt11.parse("lntb500n1pqqqqqqqqqqqqqqqqqqqqqqqq")!!.network)
        assertNull(Bolt11.parse("lnbc20x1pqqqqqqqqqqqqqqqqqqqq"))
        assertNull(Bolt11.parse("hello world"))
        assertNull(Bolt11.parse("lnbc20u1pqqqqbqqqqqqqqqqqqqqqqqqq"))
    }

    @Test
    fun `backup confirmation demands the right word at each position`() {
        // A phrase with a repeated word must still offer four distinct choices.
        val words = TestSeeds.ALICE.toMutableList().also { it[5] = it[4] }
        var finished = false
        val flow = BackupFlow(words) { finished = true }
        flow.reveal()
        flow.written()
        repeat(3) {
            val step = flow.step.value as BackupFlow.Step.Confirm
            assertTrue(words[step.position] in step.choices)
            assertEquals(4, step.choices.size)
            val wrong = step.choices.first { it != words[step.position] }
            assertFalse(flow.pick(wrong))
            assertTrue((flow.step.value as BackupFlow.Step.Confirm).wrong)
            assertTrue(flow.pick(words[step.position]))
        }
        assertEquals(BackupFlow.Step.Done, flow.step.value)
        flow.finish()
        assertTrue(finished)
    }
}
