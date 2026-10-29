package com.bitchat.android.wallet.mint

import com.bitchat.android.wallet.cashu.BlindedMessage
import com.bitchat.android.wallet.cashu.MeltQuoteState
import com.bitchat.android.wallet.cashu.MintQuoteState
import com.bitchat.android.wallet.cashu.Proof
import com.bitchat.android.wallet.cashu.ProofState
import com.bitchat.android.wallet.cashu.Secp256k1
import com.bitchat.android.wallet.testsupport.FakeMint
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigInteger

/** Robolectric because OkHttp 5's Android artifact probes the Android platform. */
@RunWith(RobolectricTestRunner::class)
class HttpMintApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: HttpMintApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HttpMintApi(server.url("/").toString().trimEnd('/'), { OkHttpClient() })
    }

    @After
    fun tearDown() = server.close()

    private fun json(body: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private suspend inline fun <reified T : MintException> expect(crossinline block: suspend () -> Unit): T {
        try {
            block()
        } catch (e: MintException) {
            if (e is T) return e
            fail("expected ${T::class.simpleName}, got $e")
        }
        fail("expected ${T::class.simpleName}")
        error("unreachable")
    }

    @Test
    fun `info parses supported nuts and method limits, ignoring unknown fields`() = runTest {
        json("""{"name":"Test","version":"x/1","motd":"hi","unknown":{"a":1},"nuts":{
            "4":{"methods":[{"method":"bolt11","unit":"sat","min_amount":1,"max_amount":500000}],"disabled":false},
            "5":{"methods":[{"method":"bolt11","unit":"sat"}],"disabled":false},
            "7":{"supported":true},"11":{"supported":false},"17":{"supported":[{"method":"bolt11"}]}}}""")
        val info = api.info()
        assertEquals("Test", info.name)
        assertTrue(info.supports(4) && info.supports(7) && info.supports(17))
        assertFalse(info.supports(11))
        assertEquals(500_000L, info.mintSetting("bolt11", "sat")!!.maxAmount)
        assertEquals("/v1/info", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `mint quote handles new accounting fields and legacy state`() = runTest {
        json("""{"quote":"q1","request":"lnbc10n1x","amount":1,"unit":"sat","state":"PAID","expiry":99,"amount_paid":1,"amount_issued":0}""")
        val quote = api.mintQuote(1, "sat")
        assertEquals(MintQuoteState.PAID, quote.state)
        assertEquals(1L, quote.mintable)
        val body = JsonParser.parseString(server.takeRequest().body!!.utf8()).asJsonObject
        assertEquals(1, body.get("amount").asInt)

        json("""{"quote":"q2","request":"lnbc10n1x","paid":true,"expiry":null}""")
        assertEquals(MintQuoteState.PAID, api.checkMintQuote("q2").state)
    }

    @Test
    fun `melt sends inputs without dleq and reads change`() = runTest {
        json("""{"quote":"m1","amount":10,"fee_reserve":2,"state":"PAID","payment_preimage":"aa",
            "change":[{"amount":1,"id":"01ab","C_":"02cc","dleq":{"e":"ee","s":"ss"}}]}""")
        val proof = Proof(4, "01ab", "secret", "02dd", dleq = com.bitchat.android.wallet.cashu.DleqProof("e", "s", "r"))
        val result = api.melt("m1", listOf(proof.forMint()), listOf(BlindedMessage(0, "01ab", "02bb")))
        assertEquals(MeltQuoteState.PAID, result.state)
        assertEquals(1, result.change.size)
        val sent = JsonParser.parseString(server.takeRequest().body!!.utf8()).asJsonObject
        assertNull(sent.getAsJsonArray("inputs")[0].asJsonObject.get("dleq"))
        assertEquals(0, sent.getAsJsonArray("outputs")[0].asJsonObject.get("amount").asInt)
    }

    @Test
    fun `check state is reordered to match the request`() = runTest {
        json("""{"states":[{"Y":"02bb","state":"SPENT"},{"Y":"02aa","state":"UNSPENT","witness":null}]}""")
        val states = api.checkState(listOf("02aa", "02bb"))
        assertEquals(listOf(ProofState.UNSPENT, ProofState.SPENT), states.map { it.state })
    }

    @Test
    fun `errors map to rejected, server error and rate limit`() = runTest {
        json("""{"detail":"Token already spent.","code":11001}""", code = 400)
        val rejected = expect<MintException.Rejected> { api.swap(emptyList(), emptyList()) }
        assertEquals(CashuErrorCode.PROOFS_SPENT, rejected.code)

        json("oops", code = 502)
        assertEquals(502, expect<MintException.ServerError> { api.keysets() }.status)

        json("{}", code = 429)
        expect<MintException.RateLimited> { api.keysets() }

        json("not json")
        expect<MintException.Malformed> { api.keysets() }

        json("""{"signatures":[{"amount":1}]}""")
        expect<MintException.Malformed> { api.swap(emptyList(), emptyList()) }
    }

    @Test
    fun `dropped connection on a swap is unreachable and is not retried silently`() = runTest {
        server.enqueue(MockResponse.Builder().onResponseStart(mockwebserver3.SocketEffect.ShutdownConnection).build())
        expect<MintException.Unreachable> { api.swap(emptyList(), emptyList()) }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `restore pairs signatures with our own outputs`() = runTest {
        json("""{"outputs":[{"amount":8,"id":"01ab","B_":"02BB"}],"signatures":[{"amount":8,"id":"01ab","C_":"02cc"}]}""")
        val ours = BlindedMessage(1, "01ab", "02bb")
        val pairs = api.restore(listOf(ours, BlindedMessage(1, "01ab", "02dd")))
        assertEquals(ours, pairs.single().first)
        assertEquals(8L, pairs.single().second.amount)
    }

    @Test
    fun `plain http is refused for public hosts`() {
        try {
            HttpMintApi("http://mint.example.com", { OkHttpClient() })
            fail("expected refusal")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `fake mint DLEQ reproduces the NUT-12 deterministic nonce vector`() {
        val a = BigInteger.TWO
        val b = Secp256k1.decodePoint("02a9acc1e48c25eeeb9289b5031cc57da9fe72f3fe2861d264bdc074209b107ba2")
        val c = Secp256k1.decodePoint("0244eccfc7a348274458bb38044c7f3c389b3c2086c7ec18b5812d2877ab937787")
        val (e, s) = FakeMint.dleq(a, b, c)
        assertEquals("2a16ffee280aff3c429045607f9b8e0bf8b35910c44c1b20b9dfaf01b263d7b3", e)
        assertEquals("9df27731238334718d120d4f74611a7c668233f988e687ac3fb188f0a34a2dab", s)
    }
}
