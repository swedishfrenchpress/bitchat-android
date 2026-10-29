package com.bitchat.android.wallet.cashu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class TokenCodecTest {

    private val v3Vector =
        "cashuAeyJ0b2tlbiI6W3sibWludCI6Imh0dHBzOi8vODMzMy5zcGFjZTozMzM4IiwicHJvb2ZzIjpbeyJhbW91bnQiOjIsImlkIjoiMDA5YTFmMjkzMjUzZTQxZSIsInNlY3JldCI6IjQwNzkxNWJjMjEyYmU2MWE3N2UzZTZkMmFlYjRjNzI3OTgwYmRhNTFjZDA2YTZhZmMyOWUyODYxNzY4YTc4MzciLCJDIjoiMDJiYzkwOTc5OTdkODFhZmIyY2M3MzQ2YjVlNDM0NWE5MzQ2YmQyYTUwNmViNzk1ODU5OGE3MmYwY2Y4NTE2M2VhIn0seyJhbW91bnQiOjgsImlkIjoiMDA5YTFmMjkzMjUzZTQxZSIsInNlY3JldCI6ImZlMTUxMDkzMTRlNjFkNzc1NmIwZjhlZTBmMjNhNjI0YWNhYTNmNGUwNDJmNjE0MzNjNzI4YzcwNTdiOTMxYmUiLCJDIjoiMDI5ZThlNTA1MGI4OTBhN2Q2YzA5NjhkYjE2YmMxZDVkNWZhMDQwZWExZGUyODRmNmVjNjlkNjEyOTlmNjcxMDU5In1dfV0sInVuaXQiOiJzYXQiLCJtZW1vIjoiVGhhbmsgeW91LiJ9"

    private val v4SingleVector =
        "cashuBpGF0gaJhaUgArSaMTR9YJmFwgaNhYQFhc3hAOWE2ZGJiODQ3YmQyMzJiYTc2ZGIwZGYxOTcyMTZiMjlkM2I4Y2MxNDU1M2NkMjc4MjdmYzFjYzk0MmZlZGI0ZWFjWCEDhhhUP_trhpXfStS6vN6So0qWvc2X3O4NfM-Y1HISZ5JhZGlUaGFuayB5b3VhbXVodHRwOi8vbG9jYWxob3N0OjMzMzhhdWNzYXQ="

    private val v4MultiVector =
        "cashuBo2F0gqJhaUgA_9SLj17PgGFwgaNhYQFhc3hAYWNjMTI0MzVlN2I4NDg0YzNjZjE4NTAxNDkyMThhZjkwZjcxNmE1MmJmNGE1ZWQzNDdlNDhlY2MxM2Y3NzM4OGFjWCECRFODGd5IXVW-07KaZCvuWHk3WrnnpiDhHki6SCQh88-iYWlIAK0mjE0fWCZhcIKjYWECYXN4QDEzMjNkM2Q0NzA3YTU4YWQyZTIzYWRhNGU5ZjFmNDlmNWE1YjRhYzdiNzA4ZWIwZDYxZjczOGY0ODMwN2U4ZWVhY1ghAjRWqhENhLSsdHrr2Cw7AFrKUL9Ffr1XN6RBT6w659lNo2FhAWFzeEA1NmJjYmNiYjdjYzY0MDZiM2ZhNWQ1N2QyMTc0ZjRlZmY4YjQ0MDJiMTc2OTI2ZDNhNTdkM2MzZGNiYjU5ZDU3YWNYIQJzEpxXGeWZN5qXSmJjY8MzxWyvwObQGr5G1YCCgHicY2FtdWh0dHA6Ly9sb2NhbGhvc3Q6MzMzOGF1Y3NhdA"

    @Test
    fun `v3 vector decodes every proof field`() {
        val token = TokenCodec.decode(v3Vector)
        assertEquals("https://8333.space:3338", token.mint)
        assertEquals("sat", token.unit)
        assertEquals("Thank you.", token.memo)
        assertEquals(10L, token.amount)
        assertEquals(
            "407915bc212be61a77e3e6d2aeb4c727980bda51cd06a6afc29e2861768a7837",
            token.proofs[0].secret
        )
        assertEquals("009a1f293253e41e", token.proofs[1].keysetId)
    }

    @Test
    fun `v3 decodes with or without padding and rejects bad prefixes`() {
        assertEquals(TokenCodec.decode(v3Vector), TokenCodec.decode("$v3Vector=="))
        assertThrows(TokenDecodeException::class.java) { TokenCodec.decode("cas" + "s" + v3Vector.drop(3)) }
        assertThrows(TokenDecodeException::class.java) { TokenCodec.decode(v3Vector.drop(6)) }
        assertEquals(10L, TokenCodec.decode("cashu:$v3Vector").amount)
    }

    @Test
    fun `v4 encoding reproduces the golden vector byte for byte`() {
        val token = Token(
            mint = "http://localhost:3338",
            unit = "sat",
            memo = "Thank you",
            proofs = listOf(
                Proof(
                    amount = 1,
                    keysetId = "00ad268c4d1f5826",
                    secret = "9a6dbb847bd232ba76db0df197216b29d3b8cc14553cd27827fc1cc942fedb4e",
                    c = "038618543ffb6b8695df4ad4babcde92a34a96bdcd97dcee0d7ccf98d472126792",
                )
            ),
        )
        assertEquals(v4SingleVector.trimEnd('='), TokenCodec.encodeV4(token))
    }

    @Test
    fun `v4 multi-keyset vector round trips`() {
        val token = TokenCodec.decode(v4MultiVector)
        assertEquals(4L, token.amount)
        assertEquals(listOf("00ffd48b8f5ecf80", "00ad268c4d1f5826", "00ad268c4d1f5826"), token.proofs.map { it.keysetId })
        assertEquals(v4MultiVector, TokenCodec.encodeV4(token))
        assertEquals(token, TokenCodec.decode(TokenCodec.encodeV4(token)))
    }

    @Test
    fun `v4 keeps DLEQ and witness`() {
        val proof = Proof(
            amount = 8,
            keysetId = "015ba18a8adcd02e715a58358eb618da4a4b3791151a4bee5e968bb88406ccf76a",
            secret = """["P2PK",{"nonce":"00","data":"02aa","tags":[]}]""",
            c = "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
            dleq = DleqProof("11".repeat(32), "22".repeat(32), "33".repeat(32)),
            witness = """{"signatures":["ab"]}""",
        )
        val token = Token("https://mint.example", "sat", listOf(proof))
        assertEquals(token, TokenCodec.decode(TokenCodec.encodeV4(token)))
    }

    @Test
    fun `multi-mint v3 tokens are rejected explicitly`() {
        val json = """{"token":[
            {"mint":"https://a.example","proofs":[{"amount":1,"id":"00aa","secret":"s1","C":"02aa"}]},
            {"mint":"https://b.example","proofs":[{"amount":1,"id":"00aa","secret":"s2","C":"02aa"}]}]}"""
        val raw = "cashuA" + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        assertThrows(TokenDecodeException.MultipleMints::class.java) { TokenCodec.decode(raw) }
    }

    @Test
    fun `zero, negative and overflowing amounts are rejected`() {
        fun v3(amounts: List<String>): String {
            val proofs = amounts.joinToString(",") { """{"amount":$it,"id":"00aa","secret":"s$it","C":"02aa"}""" }
            val json = """{"token":[{"mint":"https://a.example","proofs":[$proofs]}],"unit":"sat"}"""
            return "cashuA" + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        }
        assertThrows(TokenDecodeException.AmountOutOfRange::class.java) { TokenCodec.decode(v3(listOf("0"))) }
        assertThrows(TokenDecodeException.AmountOutOfRange::class.java) { TokenCodec.decode(v3(listOf("-4"))) }
        assertThrows(TokenDecodeException.AmountOutOfRange::class.java) {
            TokenCodec.decode(v3(listOf("2000000000000000", "2000000000000000")))
        }
        assertThrows(TokenDecodeException::class.java) { TokenCodec.decode(v3(listOf("1.5"))) }
        assertThrows(TokenDecodeException::class.java) { TokenCodec.decode(v3(listOf("99999999999999999999"))) }
    }

    @Test
    fun `truncated and garbage CBOR fails closed`() {
        val payload = TokenCodec.base64UrlDecode(v4MultiVector.drop(6))!!
        for (cut in listOf(1, 10, payload.size / 2, payload.size - 1)) {
            val raw = "cashuB" + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.copyOf(cut))
            assertNull("cut at $cut", TokenCodec.decodeOrNull(raw))
        }
        // Deeply nested arrays must hit the depth bound, not the stack.
        val nested = ByteArray(5_000) { 0x81.toByte() } + byteArrayOf(0x00)
        assertNull(TokenCodec.decodeOrNull("cashuB" + Base64.getUrlEncoder().withoutPadding().encodeToString(nested)))
    }

    @Test
    fun `mint urls normalize and insecure schemes are refused`() {
        assertEquals("https://mint.example.com", MintUrl.normalize("https://MINT.example.com/"))
        assertEquals("https://mint.example.com/cashu", MintUrl.normalize("mint.example.com/cashu///"))
        assertEquals("http://abcdefghijklmnop.onion", MintUrl.normalize("http://abcdefghijklmnop.onion"))
        assertNull(MintUrl.normalizeOrNull("http://mint.example.com"))
        assertNull(MintUrl.normalizeOrNull("ftp://mint.example.com"))
        assertNull(MintUrl.normalizeOrNull("https://user:pw@mint.example.com"))
        assertTrue(MintUrl.normalizeOrNull("https://") == null)
    }
}
