package com.bitchat.android.wallet

import com.bitchat.android.wallet.cashu.Bip39
import com.bitchat.android.wallet.cashu.Token
import com.bitchat.android.wallet.cashu.TokenCodec
import com.bitchat.android.wallet.engine.HistoryNote
import com.bitchat.android.wallet.engine.HistoryStatus
import com.bitchat.android.wallet.engine.WalletError
import com.bitchat.android.wallet.engine.WalletException
import com.bitchat.android.wallet.mint.HttpMintApi
import com.bitchat.android.wallet.testsupport.FaultyMintApi
import com.bitchat.android.wallet.testsupport.TestClock
import com.bitchat.android.wallet.testsupport.WalletHarness
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The edge cases from docs/cashu/edge-cases.md, run against the live testnut mint. Faults
 * are injected only at the transport ([FaultyMintApi]): the mint really acts, then the
 * answer is lost, so recovery is exercised against a real mint's NUT-09/NUT-07 endpoints.
 * Opt-in like [TestnutIntegrationTest] (`CASHU_TESTNUT=1`). Fresh random seeds each run.
 *
 * Not reproducible live, and therefore covered only by the fake mint: keyset rotation (the
 * operator controls it) and an unpaid mint quote expiring (testnut pays every invoice).
 */
@RunWith(RobolectricTestRunner::class)
class TestnutEdgeCaseTest {
    private val url = "https://testnut.cashu.space"
    private val clock = TestClock(System.currentTimeMillis())
    private val http = OkHttpClient()

    @Before
    fun requireOptIn() {
        assumeTrue("set CASHU_TESTNUT=1 to run live testnut checks", System.getenv("CASHU_TESTNUT") == "1")
        clock.nowMs = System.currentTimeMillis()
    }

    private fun api() = FaultyMintApi(HttpMintApi(url) { http })

    private fun wallet(api: FaultyMintApi = api(), words: List<String> = Bip39.generate()) =
        WalletHarness(words, clock, mapOf(url to api))

    private suspend fun expect(error: WalletError, block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("expected $error")
        } catch (e: WalletException) {
            assertEquals(error, e.error)
        }
    }

    /** Waits (bounded) until testnut's fake backend has paid a mint quote. */
    private suspend fun awaitPaid(api: FaultyMintApi, quoteId: String) {
        repeat(30) {
            if (api.checkMintQuote(quoteId).mintable > 0) return
            delay(1_000)
        }
        throw AssertionError("testnut did not pay the quote in time")
    }

    private suspend fun WalletHarness.fundLive(api: FaultyMintApi, amount: Long, unit: String = "sat") {
        if (wallet.mint(url) == null) wallet.addMint(url)
        val entry = wallet.createMintQuote(url, amount, unit)
        awaitPaid(api, entry.quoteId!!)
        assertEquals(HistoryStatus.COMPLETED, wallet.checkMintQuote(entry.id).status)
    }

    @Test
    fun `network drop mid-mint is recovered from testnut with restore`() = runBlocking {
        val api = api()
        val alice = wallet(api)
        alice.wallet.addMint(url)
        val entry = alice.wallet.createMintQuote(url, 64)
        awaitPaid(api, entry.quoteId!!)
        api.dropResponseOn = "mint"
        expect(WalletError.OutcomeUnknown) { alice.wallet.checkMintQuote(entry.id) }
        assertEquals(0L, alice.balance())
        alice.restart().recover()
        assertEquals(64L, alice.balance())
    }

    @Test
    fun `network drop mid-swap while claiming finishes after an app restart`() = runBlocking {
        val aliceApi = api()
        val alice = wallet(aliceApi)
        alice.fundLive(aliceApi, 64)
        val token = alice.wallet.send(url, 21).token!!
        val bobApi = api()
        val bob = wallet(bobApi)
        bob.wallet.addMint(url)
        bobApi.dropResponseOn = "swap"
        expect(WalletError.MintUnreachable) { bob.wallet.receive(token) }
        bob.restart().recover()
        assertEquals(21L, bob.balance())
    }

    @Test
    fun `network drop mid-melt settles as paid from the quote state`() = runBlocking {
        val aliceApi = api()
        val alice = wallet(aliceApi)
        alice.fundLive(aliceApi, 300)
        val invoice = wallet().also { it.wallet.addMint(url) }.wallet.createMintQuote(url, 50).invoice!!
        val preview = alice.wallet.previewMelt(url, invoice)
        aliceApi.dropResponseOn = "melt"
        expect(WalletError.PaymentPending) { alice.wallet.melt(preview, "melt-1") }
        alice.restart().recover()
        val entry = alice.wallet.state.value.history.first { it.id == "melt-1" }
        assertEquals(HistoryStatus.COMPLETED, entry.status)
        assertTrue(alice.balance() in (300 - preview.maxTotal)..250)
    }

    @Test
    fun `app killed mid-send finishes the token on restart`() = runBlocking {
        val api = api()
        val alice = wallet(api)
        alice.fundLive(api, 100)
        api.dropResponseOn = "swap"
        expect(WalletError.OutcomeUnknown) { alice.wallet.send(url, 21, historyId = "send-1") }
        alice.restart().recover()
        val entry = alice.wallet.state.value.history.first { it.id == "send-1" }
        assertEquals(HistoryStatus.PENDING, entry.status)
        assertNotNull(entry.token)
        assertEquals(21L, TokenCodec.decode(entry.token!!).amount - 1) // includes the claim fee we cover
    }

    @Test
    fun `mint offline queues the claim and completes it when testnut is back`() = runBlocking {
        val aliceApi = api()
        val alice = wallet(aliceApi)
        alice.fundLive(aliceApi, 64)
        val token = alice.wallet.send(url, 16).token!!
        val bobApi = api()
        val bob = wallet(bobApi)
        bob.wallet.addMint(url)
        bobApi.offline = true
        expect(WalletError.MintUnreachable) { bob.wallet.receive(token) }
        assertEquals(HistoryNote.WAITING_FOR_MINT, bob.wallet.state.value.history.single().note)
        bobApi.offline = false
        bob.wallet.recover()
        assertEquals(16L, bob.balance())
    }

    @Test
    fun `double spend, unknown mint and malformed tokens are refused`() = runBlocking {
        val aliceApi = api()
        val alice = wallet(aliceApi)
        alice.fundLive(aliceApi, 64)
        val token = alice.wallet.send(url, 8).token!!
        val bob = wallet()
        expect(WalletError.UnknownMint) { bob.wallet.receive(token) }
        bob.wallet.addMint(url)
        bob.wallet.receive(token)
        expect(WalletError.TokenAlreadyClaimed) { bob.wallet.receive(token) }
        val carol = wallet().also { it.wallet.addMint(url) }
        expect(WalletError.TokenAlreadySpent) { carol.wallet.receive(token) }
        expect(WalletError.TokenMalformed) { carol.wallet.receive("cashuBnot-a-token") }
    }

    @Test
    fun `partially double-spent token claims the unspent remainder on testnut`() = runBlocking {
        val aliceApi = api()
        val alice = wallet(aliceApi)
        alice.fundLive(aliceApi, 13) // 8 + 4 + 1
        val coins = alice.store.snapshot().proofs.map { it.proof }
        val full = TokenCodec.encodeV4(Token(url, "sat", coins))
        val carol = wallet().also { it.wallet.addMint(url) }
        carol.wallet.receive(TokenCodec.encodeV4(Token(url, "sat", coins.filter { it.amount == 8L })))
        val bob = wallet().also { it.wallet.addMint(url) }
        val entry = bob.wallet.receive(full)
        assertEquals(HistoryNote.PARTIAL, entry.note)
        assertEquals(4L, bob.balance()) // 4 + 1 unspent, minus 1 sat fee for two inputs
    }

    @Test
    fun `unit mismatch, dust and zero amounts are refused`() = runBlocking {
        val aliceApi = api()
        val alice = wallet(aliceApi)
        alice.fundLive(aliceApi, 100, unit = "usd")
        val usd = TokenCodec.decode(alice.wallet.send(url, 10, unit = "usd").token!!)
        val bob = wallet().also { it.wallet.addMint(url) }
        expect(WalletError.UnitUnsupported) { bob.wallet.receive(TokenCodec.encodeV4(usd.copy(unit = "sat"))) }

        val dustApi = api()
        val dustOwner = wallet(dustApi)
        dustOwner.fundLive(dustApi, 1)
        val coin = dustOwner.store.snapshot().proofs.single().proof
        // testnut charges 100 ppk per input: a lone 1 sat coin nets nothing.
        val error = try {
            bob.wallet.receive(TokenCodec.encodeV4(Token(url, "sat", listOf(coin))))
            null
        } catch (e: WalletException) {
            e.error
        }
        assertTrue("expected fees to exceed the amount, got $error", error is WalletError.FeesExceedAmount)

        expect(WalletError.InvalidAmount) { bob.wallet.createMintQuote(url, 0) }
        expect(WalletError.InvalidAmount) { bob.wallet.previewSend(url, 0) }
    }

    @Test
    fun `expired melt quote is refused before anything is spent`() = runBlocking {
        val api = api()
        val alice = wallet(api)
        alice.fundLive(api, 100)
        val invoice = wallet().also { it.wallet.addMint(url) }.wallet.createMintQuote(url, 10).invoice!!
        val preview = alice.wallet.previewMelt(url, invoice)
        clock.advanceSeconds(24 * 3600)
        expect(WalletError.QuoteExpired) { alice.wallet.melt(preview) }
        assertEquals(100L, alice.balance())
    }

    @Test
    fun `double tap creates a single token`() = runBlocking {
        val api = api()
        val alice = wallet(api)
        alice.fundLive(api, 100)
        val first = alice.wallet.send(url, 10, historyId = "tap")
        val second = alice.wallet.send(url, 10, historyId = "tap")
        assertEquals(first, second)
        assertEquals(1, alice.wallet.state.value.history.count { it.id == "tap" })
        assertTrue(alice.balance() >= 100 - 13)
    }

    @Test
    fun `wrong seed and partial seed restore recover nothing`() = runBlocking {
        val api = api()
        val alice = wallet(api)
        alice.fundLive(api, 32)
        val stranger = wallet()
        val result = stranger.wallet.restore(listOf(url)).single()
        assertTrue(result.recovered.isEmpty())
        assertEquals(0L, stranger.balance())
        val partial = Bip39.generate().dropLast(1)
        assertTrue(Bip39.validate(partial) is Bip39.Validation.WrongLength)
    }
}
