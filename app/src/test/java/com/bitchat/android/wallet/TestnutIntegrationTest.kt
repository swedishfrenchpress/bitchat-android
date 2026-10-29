package com.bitchat.android.wallet

import com.bitchat.android.wallet.cashu.P2pk
import com.bitchat.android.wallet.cashu.Secp256k1
import com.bitchat.android.wallet.engine.CashuWallet
import com.bitchat.android.wallet.engine.HistoryStatus
import com.bitchat.android.wallet.engine.WalletError
import com.bitchat.android.wallet.engine.WalletException
import com.bitchat.android.wallet.mint.HttpMintApi
import com.bitchat.android.wallet.testsupport.TestClock
import com.bitchat.android.wallet.testsupport.WalletHarness
import com.bitchat.android.wallet.cashu.Bip39
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigInteger

/**
 * Live checks against testnut.cashu.space, a public test mint with fake sats that marks
 * every invoice paid. Opt-in only (`CASHU_TESTNUT=1`), because the default suite must not
 * touch the internet (see docs/testing-conventions.md). Each run uses fresh random seeds,
 * so nothing here is tied to a person or a device.
 */
@RunWith(RobolectricTestRunner::class)
class TestnutIntegrationTest {
    private val url = "https://testnut.cashu.space"
    private val clock = TestClock(System.currentTimeMillis())
    private val http = OkHttpClient()
    private val api = HttpMintApi(url) { http }

    @Before
    fun requireOptIn() {
        assumeTrue("set CASHU_TESTNUT=1 to run live testnut checks", System.getenv("CASHU_TESTNUT") == "1")
    }

    private fun wallet(keys: List<BigInteger> = emptyList()): WalletHarness {
        clock.nowMs = System.currentTimeMillis()
        return WalletHarness(Bip39.generate(), clock, mapOf(url to api), extraKeys = keys)
    }

    private suspend fun WalletHarness.fundLive(amount: Long, unit: String = "sat") {
        if (wallet.mint(url) == null) wallet.addMint(url)
        val entry = wallet.createMintQuote(url, amount, unit)
        // testnut marks invoices paid a moment after creation; poll with a bounded timeout.
        var latest = entry
        for (attempt in 0 until 30) {
            latest = wallet.checkMintQuote(entry.id)
            if (!latest.isPending) break
            delay(1_000)
        }
        assertEquals(HistoryStatus.COMPLETED, latest.status)
    }

    @Test
    fun `add mint verifies real v2 keysets and fees`() = runBlocking {
        val alice = wallet()
        val record = alice.wallet.addMint(url)
        assertTrue(record.keysets.any { it.active && it.unit == "sat" })
        assertTrue(record.keysets.all { it.id.startsWith("01") || it.id.startsWith("00") })
    }

    @Test
    fun `mint, send, receive, double spend, reclaim against testnut`() = runBlocking {
        val alice = wallet()
        val bob = wallet()
        alice.fundLive(200)
        assertEquals(200L, alice.balance())

        val preview = alice.wallet.previewSend(url, 21)
        val sent = alice.wallet.send(url, 21)
        assertEquals(200L - preview.total, alice.balance())

        bob.wallet.addMint(url)
        val inspection = bob.wallet.inspect(sent.token!!)
        assertEquals(CashuWallet.DleqStatus.VERIFIED, inspection.dleq)
        assertEquals(21L, bob.wallet.receive(sent.token!!).amount)
        assertEquals(21L, bob.balance())

        val carol = wallet()
        carol.wallet.addMint(url)
        try {
            carol.wallet.receive(sent.token!!)
            throw AssertionError("double spend accepted")
        } catch (e: WalletException) {
            assertEquals(WalletError.TokenAlreadySpent, e.error)
        }

        assertEquals(1, alice.wallet.checkPendingSends())
        val unclaimed = alice.wallet.send(url, 13)
        assertEquals(HistoryStatus.RECLAIMED, alice.wallet.reclaim(unclaimed.id).status)
    }

    @Test
    fun `lightning melt pays a testnut invoice with change`() = runBlocking {
        val alice = wallet()
        val bob = wallet()
        alice.fundLive(300)
        bob.wallet.addMint(url)
        // Any testnut invoice is "paid" by its fake backend; use one Bob asks for.
        val invoice = bob.wallet.createMintQuote(url, 50).invoice!!
        val preview = alice.wallet.previewMelt(url, invoice)
        assertEquals(50L, preview.quote.amount)
        val paid = alice.wallet.melt(preview)
        assertEquals(HistoryStatus.COMPLETED, paid.status)
        assertTrue(alice.balance() >= 300 - preview.maxTotal)
        assertTrue(alice.balance() <= 250)
    }

    @Test
    fun `P2PK lock to a peer key is enforced by testnut`() = runBlocking {
        val bobKey = Secp256k1.randomScalar()
        val alice = wallet()
        val bob = wallet(listOf(bobKey))
        val carol = wallet()
        alice.fundLive(100)
        val locked = alice.wallet.send(
            url, 10, lock = CashuWallet.LockRequest(P2pk.compressedPublicKey(bobKey), clock.seconds + 7 * 86_400),
        )
        carol.wallet.addMint(url)
        try {
            carol.wallet.receive(locked.token!!)
            throw AssertionError("locked token claimed by the wrong key")
        } catch (e: WalletException) {
            assertEquals(WalletError.TokenLockedToSomeoneElse, e.error)
        }
        bob.wallet.addMint(url)
        assertEquals(10L, bob.wallet.receive(locked.token!!).amount)
    }

    @Test
    fun `seed restore recovers the balance from testnut`() = runBlocking {
        val words = Bip39.generate()
        val alice = WalletHarness(words, clock, mapOf(url to api))
        alice.fundLive(128)
        alice.wallet.send(url, 40)
        // The unclaimed send is still unspent at the mint, so a restore rightly brings it back.
        val expected = alice.balance() + alice.wallet.state.value.pendingOut("sat")
        val fresh = WalletHarness(words, clock, mapOf(url to api))
        val result = fresh.wallet.restore(listOf(url)).single()
        assertNull(result.error)
        assertEquals(expected, fresh.balance())
    }

    @Test
    fun `usd unit is minted and kept apart from sats`() = runBlocking {
        val alice = wallet()
        alice.fundLive(150, unit = "usd")
        assertEquals(150L, alice.balance("usd"))
        assertEquals(0L, alice.balance("sat"))
    }
}
