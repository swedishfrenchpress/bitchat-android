package com.bitchat.android.wallet.engine

import com.bitchat.android.wallet.cashu.Hex
import com.bitchat.android.wallet.cashu.P2pk
import com.bitchat.android.wallet.cashu.Secp256k1
import com.bitchat.android.wallet.cashu.TokenCodec
import com.bitchat.android.wallet.testsupport.FakeMint
import com.bitchat.android.wallet.testsupport.TestClock
import com.bitchat.android.wallet.testsupport.TestSeeds
import com.bitchat.android.wallet.testsupport.WalletHarness
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigInteger

class CashuWalletTest {
    private val clock = TestClock()

    private fun mint(feePpk: Long = 0, units: List<String> = listOf("sat")) =
        FakeMint(feePpk = feePpk, now = { clock.seconds }, units = units)

    private fun harness(mint: FakeMint, words: List<String> = TestSeeds.ALICE, keys: List<BigInteger> = emptyList()) =
        WalletHarness(words, clock, mapOf(mint.url to mint), extraKeys = keys)

    private suspend fun expectError(expected: WalletError, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $expected")
        } catch (e: WalletException) {
            assertEquals(expected, e.error)
        }
    }

    private suspend inline fun <reified T : WalletError> expectErrorType(crossinline block: suspend () -> Unit): T {
        try {
            block()
        } catch (e: WalletException) {
            if (e.error is T) return e.error as T
            fail("expected ${T::class.simpleName} but got ${e.error}")
        }
        fail("expected ${T::class.simpleName}")
        error("unreachable")
    }

    // MARK: - Lightning receive

    @Test
    fun `paid lightning quote mints exact balance`() = runTest {
        val m = mint()
        val alice = harness(m)
        val entry = alice.fund(m, 1000)
        assertEquals(HistoryStatus.COMPLETED, entry.status)
        assertEquals(1000L, alice.balance())
        assertTrue(alice.store.snapshot().operations.isEmpty())
    }

    @Test
    fun `unpaid quote stays pending, mints after payment, expires when abandoned`() = runTest {
        val m = mint().apply { mintQuotesPaid = false }
        val alice = harness(m)
        alice.wallet.addMint(m.url)
        val entry = alice.wallet.createMintQuote(m.url, 100)
        assertEquals(HistoryStatus.PENDING, alice.wallet.checkMintQuote(entry.id).status)
        m.pay(entry.quoteId!!)
        assertEquals(HistoryStatus.COMPLETED, alice.wallet.checkMintQuote(entry.id).status)
        assertEquals(100L, alice.balance())

        val stale = alice.wallet.createMintQuote(m.url, 50)
        clock.advanceSeconds(3600)
        m.expireQuotes()
        assertEquals(HistoryStatus.EXPIRED, alice.wallet.checkMintQuote(stale.id).status)
        assertEquals(100L, alice.balance())
    }

    @Test
    fun `lost mint response is recovered with restore and never double mints`() = runTest {
        val m = mint()
        val alice = harness(m)
        alice.wallet.addMint(m.url)
        val entry = alice.wallet.createMintQuote(m.url, 300)
        m.faults = { if (it == "mint") FakeMint.Fault.DropResponse else null }
        expectError(WalletError.OutcomeUnknown) { alice.wallet.checkMintQuote(entry.id) }
        assertEquals(0L, alice.balance())
        m.faults = { null }
        alice.wallet.recover()
        assertEquals(300L, alice.balance())
        alice.wallet.recover()
        assertEquals(300L, alice.balance())
        assertEquals(HistoryStatus.COMPLETED, alice.wallet.state.value.history.first { it.id == entry.id }.status)
    }

    @Test
    fun `amount limits and zero amounts are refused before the mint is asked`() = runTest {
        val m = mint()
        val alice = harness(m)
        alice.wallet.addMint(m.url)
        expectError(WalletError.InvalidAmount) { alice.wallet.createMintQuote(m.url, 0) }
        expectError(WalletError.InvalidAmount) { alice.wallet.createMintQuote(m.url, -5) }
        expectErrorType<WalletError.AmountOutOfRange> { alice.wallet.createMintQuote(m.url, 900_000) }
        assertFalse(m.calls.contains("mintQuote"))
    }

    // MARK: - Send and receive ecash

    @Test
    fun `exact-coin send works offline and the recipient claims it`() = runTest {
        val m = mint()
        val alice = harness(m)
        val bob = harness(m, TestSeeds.BOB)
        alice.fund(m, 64)
        m.offline = true
        val sent = alice.wallet.send(m.url, 64)
        assertNotNull(sent.token)
        assertEquals(0L, alice.balance())
        assertEquals(64L, alice.wallet.state.value.pendingOut("sat"))
        m.offline = false

        bob.wallet.addMint(m.url)
        val claim = bob.wallet.inspect(sent.token!!)
        assertEquals(CashuWallet.DleqStatus.VERIFIED, claim.dleq)
        assertEquals(64L, claim.receiveAmount)
        bob.wallet.receive(sent.token!!)
        assertEquals(64L, bob.balance())

        assertEquals(1, alice.wallet.checkPendingSends())
        val after = alice.wallet.state.value.history.first { it.id == sent.id }
        assertEquals(HistoryStatus.COMPLETED, after.status)
        assertEquals(HistoryNote.CLAIMED, after.note)
        assertEquals(0L, alice.wallet.state.value.pendingOut("sat"))
    }

    @Test
    fun `with fees the sender covers the claim so the recipient gets the typed amount`() = runTest {
        val m = mint(feePpk = 100)
        val alice = harness(m)
        val bob = harness(m, TestSeeds.BOB)
        alice.fund(m, 1000)
        val preview = alice.wallet.previewSend(m.url, 100)
        assertTrue(preview.fee > 0)
        val sent = alice.wallet.send(m.url, 100)
        assertEquals(1000L - preview.total, alice.balance() + 0)
        bob.wallet.addMint(m.url)
        val received = bob.wallet.receive(sent.token!!)
        assertEquals(100L, received.amount)
        assertEquals(100L, bob.balance())
    }

    @Test
    fun `claiming twice is recognised and a spent token is refused`() = runTest {
        val m = mint()
        val alice = harness(m)
        val bob = harness(m, TestSeeds.BOB)
        val carol = harness(m, TestSeeds.CAROL)
        alice.fund(m, 32)
        val token = alice.wallet.send(m.url, 32).token!!
        bob.wallet.addMint(m.url)
        bob.wallet.receive(token)
        expectError(WalletError.TokenAlreadyClaimed) { bob.wallet.receive(token) }
        carol.wallet.addMint(m.url)
        expectError(WalletError.TokenAlreadySpent) { carol.wallet.receive(token) }
        assertEquals(0L, carol.balance())
        assertEquals(HistoryStatus.FAILED, carol.wallet.state.value.history.first().status)
    }

    @Test
    fun `claim survives the mint going offline and finishes on recovery`() = runTest {
        val m = mint()
        val alice = harness(m)
        val bob = harness(m, TestSeeds.BOB)
        alice.fund(m, 16)
        val token = alice.wallet.send(m.url, 16).token!!
        bob.wallet.addMint(m.url)
        m.offline = true
        expectError(WalletError.MintUnreachable) { bob.wallet.receive(token) }
        assertEquals(HistoryNote.WAITING_FOR_MINT, bob.wallet.state.value.history.first().note)
        bob.wallet.recover()
        assertEquals(0L, bob.balance())
        m.offline = false
        bob.wallet.recover()
        assertEquals(16L, bob.balance())
        assertEquals(HistoryStatus.COMPLETED, bob.wallet.state.value.history.first().status)
    }

    @Test
    fun `lost swap response during claim is recovered after an app restart`() = runTest {
        val m = mint()
        val alice = harness(m)
        val bob = harness(m, TestSeeds.BOB)
        alice.fund(m, 8)
        val token = alice.wallet.send(m.url, 8).token!!
        bob.wallet.addMint(m.url)
        m.faults = { if (it == "swap") FakeMint.Fault.DropResponse else null }
        expectError(WalletError.MintUnreachable) { bob.wallet.receive(token) }
        m.faults = { null }
        val restarted = bob.restart()
        assertEquals(1, bob.store.snapshot().operations.size)
        restarted.recover()
        assertEquals(8L, bob.balance())
        assertTrue(bob.store.snapshot().operations.isEmpty())
    }

    @Test
    fun `unclaimed send is reclaimed and a claimed one completes instead`() = runTest {
        val m = mint()
        val alice = harness(m)
        val bob = harness(m, TestSeeds.BOB)
        alice.fund(m, 100)
        val first = alice.wallet.send(m.url, 36)
        assertEquals(HistoryStatus.RECLAIMED, alice.wallet.reclaim(first.id).status)
        assertEquals(100L, alice.balance())

        val second = alice.wallet.send(m.url, 20)
        bob.wallet.addMint(m.url)
        bob.wallet.receive(second.token!!)
        expectError(WalletError.TokenAlreadySpent) { alice.wallet.reclaim(second.id) }
        val entry = alice.wallet.state.value.history.first { it.id == second.id }
        assertEquals(HistoryStatus.COMPLETED, entry.status)
        assertEquals(80L, alice.balance())
    }

    @Test
    fun `pasting our own pending token takes it back`() = runTest {
        val m = mint()
        val alice = harness(m)
        alice.fund(m, 40)
        val sent = alice.wallet.send(m.url, 8)
        val back = alice.wallet.receive(sent.token!!)
        assertEquals(HistoryStatus.RECLAIMED, back.status)
        assertEquals(40L, alice.balance())
    }

    @Test
    fun `send retried with the same id is idempotent`() = runTest {
        val m = mint()
        val alice = harness(m)
        alice.fund(m, 100)
        val a = alice.wallet.send(m.url, 10, historyId = "tap-1")
        val b = alice.wallet.send(m.url, 10, historyId = "tap-1")
        assertEquals(a, b)
        assertEquals(90L, alice.balance())
    }

    @Test
    fun `insufficient balance and dust that fees would eat are refused`() = runTest {
        val m = mint(feePpk = 1000)
        val alice = harness(m)
        val bob = harness(m, TestSeeds.BOB)
        alice.fund(m, 10)
        expectErrorType<WalletError.InsufficientBalance> { alice.wallet.send(m.url, 50) }
        expectError(WalletError.InvalidAmount) { alice.wallet.send(m.url, 0) }
        // A lone 1 sat coin at 1 sat/input fee nets nothing for the recipient.
        val dustOwner = harness(m, TestSeeds.CAROL)
        dustOwner.fund(m, 1)
        val coin = dustOwner.store.snapshot().proofs.single().proof
        val dust = TokenCodec.encodeV4(com.bitchat.android.wallet.cashu.Token(m.url, "sat", listOf(coin)))
        bob.wallet.addMint(m.url)
        expectErrorType<WalletError.FeesExceedAmount> { bob.wallet.receive(dust) }
    }

    @Test
    fun `malformed, unknown-mint and forged tokens fail closed`() = runTest {
        val m = mint()
        val alice = harness(m)
        val bob = harness(m, TestSeeds.BOB)
        alice.fund(m, 16)
        val token = alice.wallet.send(m.url, 16).token!!
        expectError(WalletError.TokenMalformed) { bob.wallet.receive("cashuBnotreally") }
        expectError(WalletError.UnknownMint) { bob.wallet.receive(token) }
        bob.wallet.addMint(m.url)
        val decoded = TokenCodec.decode(token)
        val forged = TokenCodec.encodeV4(decoded.copy(proofs = decoded.proofs.map {
            it.copy(dleq = it.dleq!!.copy(e = Hex.encode(Secp256k1.randomBytes(32))))
        }))
        assertEquals(CashuWallet.DleqStatus.INVALID, bob.wallet.inspect(forged).dleq)
        expectError(WalletError.TokenSignatureInvalid) { bob.wallet.receive(forged) }
        assertEquals(0L, bob.balance())
    }

    @Test
    fun `tokens in another unit are held separately and never mixed`() = runTest {
        val m = mint(units = listOf("sat", "usd"))
        val alice = harness(m)
        val bob = harness(m, TestSeeds.BOB)
        alice.fund(m, 500, unit = "usd")
        alice.fund(m, 20)
        val usd = alice.wallet.send(m.url, 125, unit = "usd")
        bob.wallet.addMint(m.url)
        bob.wallet.receive(usd.token!!)
        assertEquals(125L, bob.balance("usd"))
        assertEquals(0L, bob.balance("sat"))
        val mislabelled = TokenCodec.encodeV4(TokenCodec.decode(alice.wallet.send(m.url, 10, unit = "usd").token!!).copy(unit = "sat"))
        expectError(WalletError.UnitUnsupported) { bob.wallet.receive(mislabelled) }
    }

    @Test
    fun `storage failure stops the operation before anything reaches the mint`() = runTest {
        val m = mint()
        val alice = harness(m)
        alice.fund(m, 100)
        val swapsBefore = m.calls.count { it == "swap" }
        alice.store.failNextCommit = true
        try {
            alice.wallet.send(m.url, 37)
            fail("expected storage failure")
        } catch (_: java.io.IOException) {
        }
        assertEquals(swapsBefore, m.calls.count { it == "swap" })
        assertEquals(100L, alice.balance())
    }

    @Test
    fun `secrets are never reused across operations and restarts`() = runTest {
        val m = mint(feePpk = 100)
        val alice = harness(m)
        alice.fund(m, 500)
        repeat(3) { alice.wallet.send(m.url, 11L + it) }
        alice.restart()
        alice.fund(m, 64)
        val secrets = alice.store.snapshot().proofs.map { it.proof.secret }
        assertEquals(secrets.size, secrets.toSet().size)
    }

    // MARK: - P2PK

    @Test
    fun `locked send is only claimable by its key and refundable after locktime`() = runTest {
        val m = mint()
        val bobKey = BigInteger(1, Secp256k1.sha256("bob-nostr-key".toByteArray())).mod(Secp256k1.N)
        val alice = harness(m)
        val bob = harness(m, TestSeeds.BOB, keys = listOf(bobKey))
        val carol = harness(m, TestSeeds.CAROL)
        alice.fund(m, 100)
        val lock = CashuWallet.LockRequest(P2pk.compressedPublicKey(bobKey), clock.seconds + 7 * 86_400)

        val toBob = alice.wallet.send(m.url, 20, lock = lock)
        carol.wallet.addMint(m.url)
        assertTrue(carol.wallet.inspect(toBob.token!!).lock is CashuWallet.LockStatus.ToSomeoneElse)
        expectError(WalletError.TokenLockedToSomeoneElse) { carol.wallet.receive(toBob.token!!) }
        bob.wallet.addMint(m.url)
        assertTrue(bob.wallet.inspect(toBob.token!!).lock is CashuWallet.LockStatus.ToUs)
        bob.wallet.receive(toBob.token!!)
        assertEquals(20L, bob.balance())

        val unclaimed = alice.wallet.send(m.url, 30, lock = lock)
        expectErrorType<WalletError.TokenLockNotExpired> { alice.wallet.reclaim(unclaimed.id) }
        clock.advanceSeconds(7 * 86_400 + 60)
        assertEquals(HistoryStatus.RECLAIMED, alice.wallet.reclaim(unclaimed.id).status)
        assertEquals(80L, alice.balance())
    }

    @Test
    fun `locking needs a mint that enforces it`() = runTest {
        val m = mint().apply { supportsP2pk = false }
        val alice = harness(m)
        alice.fund(m, 50)
        val lock = CashuWallet.LockRequest(P2pk.compressedPublicKey(Secp256k1.randomScalar()), clock.seconds + 3600)
        expectError(WalletError.MintFeatureMissing) { alice.wallet.send(m.url, 10, lock = lock) }
    }

    // MARK: - Lightning send

    @Test
    fun `melt pays, returns unused fee reserve as change`() = runTest {
        val m = mint().apply { meltFeeReserve = 10; meltActualFee = 2 }
        val alice = harness(m)
        alice.fund(m, 1000)
        val preview = alice.wallet.previewMelt(m.url, FakeMint.invoice(200, 99))
        assertEquals(200L, preview.quote.amount)
        assertEquals(210L, preview.maxTotal)
        val paid = alice.wallet.melt(preview)
        assertEquals(HistoryStatus.COMPLETED, paid.status)
        assertEquals(2L, paid.fee)
        assertEquals(798L, alice.balance())
    }

    @Test
    fun `failed lightning payment returns the funds`() = runTest {
        val m = mint().apply { meltOutcome = FakeMint.MeltOutcome.FAIL }
        val alice = harness(m)
        alice.fund(m, 300)
        val preview = alice.wallet.previewMelt(m.url, FakeMint.invoice(100, 1))
        expectError(WalletError.PaymentFailed) { alice.wallet.melt(preview, historyId = "pay") }
        assertEquals(300L, alice.balance())
        assertEquals(HistoryStatus.FAILED, alice.wallet.state.value.history.first { it.id == "pay" }.status)
        assertEquals(HistoryNote.PAYMENT_FAILED, alice.wallet.state.value.history.first { it.id == "pay" }.note)
    }

    @Test
    fun `pending payment holds funds until the mint settles either way`() = runTest {
        val m = mint().apply { meltOutcome = FakeMint.MeltOutcome.PENDING; meltFeeReserve = 4; meltActualFee = 1 }
        val alice = harness(m)
        alice.fund(m, 300)
        expectError(WalletError.PaymentPending) { alice.wallet.melt(alice.wallet.previewMelt(m.url, FakeMint.invoice(100, 1)), "p1") }
        assertTrue(alice.balance() < 300)
        alice.wallet.recover()
        assertTrue(alice.wallet.state.value.history.first { it.id == "p1" }.isPending)
        m.settlePendingMelts(FakeMint.MeltOutcome.PAY)
        alice.wallet.recover()
        assertEquals(199L, alice.balance())

        m.meltOutcome = FakeMint.MeltOutcome.PENDING
        expectError(WalletError.PaymentPending) { alice.wallet.melt(alice.wallet.previewMelt(m.url, FakeMint.invoice(50, 2)), "p2") }
        m.settlePendingMelts(FakeMint.MeltOutcome.FAIL)
        alice.wallet.recover()
        assertEquals(199L, alice.balance())
        assertEquals(HistoryStatus.COMPLETED, alice.wallet.state.value.history.first { it.id == "p1" }.status)
        assertEquals(HistoryStatus.FAILED, alice.wallet.state.value.history.first { it.id == "p2" }.status)
    }

    @Test
    fun `melt with a lost response is settled from the quote state`() = runTest {
        val m = mint().apply { meltFeeReserve = 8; meltActualFee = 0 }
        val alice = harness(m)
        alice.fund(m, 256)
        val preview = alice.wallet.previewMelt(m.url, FakeMint.invoice(100, 3))
        m.faults = { if (it == "melt") FakeMint.Fault.DropResponse else null }
        expectError(WalletError.PaymentPending) { alice.wallet.melt(preview, "lost") }
        m.faults = { null }
        alice.restart().recover()
        assertEquals(156L, alice.balance())
        assertEquals(HistoryStatus.COMPLETED, alice.wallet.state.value.history.first { it.id == "lost" }.status)
    }

    @Test
    fun `expired melt quote and already paid invoice are refused`() = runTest {
        val m = mint()
        val alice = harness(m)
        alice.fund(m, 500)
        val preview = alice.wallet.previewMelt(m.url, FakeMint.invoice(10, 4))
        clock.advanceSeconds(3600)
        expectError(WalletError.QuoteExpired) { alice.wallet.melt(preview) }
        assertEquals(500L, alice.balance())
        expectError(WalletError.InvoiceInvalid) { alice.wallet.previewMelt(m.url, "not an invoice") }
    }

    // MARK: - Mints, keysets, restore

    @Test
    fun `keyset rotation keeps old coins spendable and spends them first`() = runTest {
        val m = mint()
        val alice = harness(m)
        alice.fund(m, 100)
        val oldKeyset = m.activeKeysetId()
        m.rotateKeysets()
        alice.wallet.refreshMint(m.url)
        val record = alice.wallet.mint(m.url)!!
        assertFalse(record.keyset(oldKeyset)!!.active)
        val before = alice.store.snapshot().proofs.map { it.proof.secret }.toSet()
        alice.wallet.send(m.url, 33)
        val minted = alice.store.snapshot().proofs.filter { it.proof.secret !in before }
        assertTrue(minted.isNotEmpty())
        assertEquals(setOf(m.activeKeysetId()), minted.map { it.proof.keysetId }.toSet())
        assertEquals(67L, alice.balance())
        // Remaining old-keyset coins are still accepted as inputs.
        alice.wallet.send(m.url, alice.balance())
        assertEquals(0L, alice.balance())
    }

    @Test
    fun `mint whose keys do not match their id is refused`() = runTest {
        val m = mint()
        val tampering = object : com.bitchat.android.wallet.mint.MintApi by m {
            override suspend fun keys(keysetId: String) = m.keys(keysetId).let { ks ->
                ks.copy(keys = ks.keys + (BigInteger.ONE to P2pk.compressedPublicKey(Secp256k1.randomScalar())))
            }
        }
        val alice = WalletHarness(TestSeeds.ALICE, clock, mapOf(m.url to tampering))
        expectError(WalletError.MintKeysInvalid) { alice.wallet.addMint(m.url) }
        assertNull(alice.wallet.mint(m.url))
    }

    @Test
    fun `removing a mint with funds needs confirmation`() = runTest {
        val m = mint()
        val alice = harness(m)
        alice.fund(m, 10)
        expectError(WalletError.MintHasFunds) { alice.wallet.removeMint(m.url) }
        alice.wallet.removeMint(m.url, force = true)
        assertNull(alice.wallet.mint(m.url))
    }

    @Test
    fun `seed restore recovers unspent coins and moves counters past them`() = runTest {
        val m = mint(feePpk = 100)
        val alice = harness(m)
        val bob = harness(m, TestSeeds.BOB)
        alice.fund(m, 500)
        val sent = alice.wallet.send(m.url, 120)
        bob.wallet.addMint(m.url)
        bob.wallet.receive(sent.token!!)
        val expected = alice.balance()

        val fresh = WalletHarness(TestSeeds.ALICE, clock, mapOf(m.url to m))
        val results = fresh.wallet.restore(listOf(m.url), batchSize = 10)
        assertNull(results.single().error)
        assertEquals(expected, fresh.balance())
        // New coins after restore must not collide with anything restored.
        fresh.fund(m, 32)
        assertEquals(expected + 32, fresh.balance())
    }

    @Test
    fun `restore with a different seed finds nothing`() = runTest {
        val m = mint()
        val alice = harness(m)
        alice.fund(m, 200)
        val stranger = WalletHarness(TestSeeds.CAROL, clock, mapOf(m.url to m))
        val result = stranger.wallet.restore(listOf(m.url), batchSize = 10).single()
        assertTrue(result.recovered.isEmpty())
        assertEquals(0L, stranger.balance())
    }

    @Test
    fun `counter collision after a short restore is resynced instead of losing funds`() = runTest {
        val m = mint()
        val alice = harness(m)
        alice.fund(m, 64)
        // Same seed, empty store: counters start at 0 and collide with what alice already minted.
        val twin = WalletHarness(TestSeeds.ALICE, clock, mapOf(m.url to m))
        twin.wallet.addMint(m.url)
        val quote = twin.wallet.createMintQuote(m.url, 64)
        expectError(WalletError.CountersResynced) { twin.wallet.checkMintQuote(quote.id) }
        assertEquals(HistoryStatus.COMPLETED, twin.wallet.checkMintQuote(quote.id).status)
        assertEquals(64L, twin.balance())
    }

    @Test
    fun `wipe erases everything`() = runTest {
        val m = mint()
        val alice = harness(m)
        alice.fund(m, 64)
        alice.wallet.send(m.url, 10)
        assertTrue(alice.wallet.wipe())
        assertEquals(WalletSnapshot(), alice.store.snapshot())
        assertTrue(alice.wallet.state.value.history.isEmpty())
        assertEquals(0L, alice.balance())
    }
}
