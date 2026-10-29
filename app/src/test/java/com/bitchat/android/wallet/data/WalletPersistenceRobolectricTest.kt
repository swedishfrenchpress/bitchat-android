package com.bitchat.android.wallet.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bitchat.android.services.InMemoryConversationStorageCipher
import com.bitchat.android.wallet.WalletService
import com.bitchat.android.wallet.backup.MintListBackup
import com.bitchat.android.wallet.backup.Nip44
import com.bitchat.android.wallet.cashu.Bip39
import com.bitchat.android.wallet.cashu.Hex
import com.bitchat.android.wallet.engine.CashuWallet
import com.bitchat.android.wallet.engine.HistoryStatus
import com.bitchat.android.wallet.testsupport.FakeMint
import com.bitchat.android.wallet.testsupport.TestClock
import com.bitchat.android.wallet.testsupport.TestSeeds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigInteger

@RunWith(RobolectricTestRunner::class)
class WalletPersistenceRobolectricTest {
    private lateinit var context: Context
    private val clock = TestClock()
    private val cipher = InMemoryConversationStorageCipher()
    private val seed = Bip39.toSeed(TestSeeds.ALICE)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("wallet-test.db")
    }

    private fun store() = SqliteWalletStore(context, "wallet-test.db", cipher, Dispatchers.Unconfined)

    private fun engine(mint: FakeMint, store: SqliteWalletStore) =
        CashuWallet(store, { mint }, { seed }, clock = { clock.nowMs })

    @Test
    fun `wallet state survives a restart from sqlite`() = runTest {
        val mint = FakeMint(now = { clock.seconds }, feePpk = 100)
        val first = engine(mint, store())
        first.load()
        first.addMint(mint.url)
        first.checkMintQuote(first.createMintQuote(mint.url, 500).id)
        val sent = first.send(mint.url, 42, memo = "lunch")
        val before = first.state.value

        val second = engine(mint, store())
        second.load()
        val after = second.state.value
        assertEquals(before.available("sat"), after.available("sat"))
        assertEquals(before.pendingOut("sat"), after.pendingOut("sat"))
        assertEquals(before.history, after.history)
        assertEquals(sent.token, after.history.first { it.id == sent.id }.token)
        assertEquals(before.mints.map { it.mint }, after.mints.map { it.mint })
        // Counters persisted: new coins after restart never reuse a secret.
        second.checkMintQuote(second.createMintQuote(mint.url, 64).id)
        assertEquals(before.available("sat") + 64, second.state.value.available("sat"))
    }

    @Test
    fun `nothing sensitive is stored in plaintext`() = runTest {
        val mint = FakeMint(url = "https://distinctive-mint.test", now = { clock.seconds })
        val wallet = engine(mint, store())
        wallet.load()
        wallet.addMint(mint.url)
        wallet.checkMintQuote(wallet.createMintQuote(mint.url, 64).id)
        val token = wallet.send(mint.url, 64).token!!
        val secret = com.bitchat.android.wallet.cashu.TokenCodec.decode(token).proofs.first().secret

        val files = listOf("", "-wal").map { context.getDatabasePath("wallet-test.db$it") }.filter { it.exists() }
        val raw = files.joinToString("") { String(it.readBytes(), Charsets.ISO_8859_1) }
        assertFalse(raw.contains("distinctive-mint"))
        assertFalse(raw.contains(secret))
        assertFalse(raw.contains(token.take(40)))
    }

    @Test
    fun `a record moved to another row is rejected`() = runTest {
        val s = store()
        val mint = FakeMint(now = { clock.seconds })
        val wallet = engine(mint, s)
        wallet.load()
        wallet.addMint(mint.url)
        wallet.checkMintQuote(wallet.createMintQuote(mint.url, 3).id)
        val db = android.database.sqlite.SQLiteDatabase.openDatabase(
            context.getDatabasePath("wallet-test.db").path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
        )
        db.rawQuery("SELECT id FROM records WHERE kind = 'proof'", null).use { c ->
            val ids = buildList { while (c.moveToNext()) add(c.getString(0)) }
            assertEquals(2, ids.size)
            // Copy the first proof's sealed payload over the second.
            db.execSQL("UPDATE records SET payload = (SELECT payload FROM records WHERE id = ?) WHERE id = ?", arrayOf(ids[0], ids[1]))
        }
        db.close()
        val reloaded = engine(mint, store())
        reloaded.load()
        // 3 sat was two coins (2 + 1); the overwritten row no longer decrypts and is dropped.
        val balance = reloaded.state.value.available("sat")
        assertTrue("balance $balance", balance == 1L || balance == 2L)
    }

    @Test
    fun `wipe deletes the database and makes residue unreadable`() = runTest {
        val s = store()
        val mint = FakeMint(now = { clock.seconds })
        val wallet = engine(mint, s)
        wallet.load()
        wallet.addMint(mint.url)
        wallet.checkMintQuote(wallet.createMintQuote(mint.url, 10).id)
        val sealed = cipher.encrypt("probe".toByteArray(), "aad".toByteArray())
        assertTrue(wallet.wipe())
        assertFalse(context.getDatabasePath("wallet-test.db").exists())
        try {
            cipher.decrypt(sealed, "aad".toByteArray())
            fail("old key must be gone")
        } catch (_: Exception) {
        }
    }

    @Test
    fun `vault stores only valid words and clears completely`() {
        val prefs = context.getSharedPreferences("vault-test", Context.MODE_PRIVATE)
        val vault = MnemonicVault(prefs)
        assertFalse(vault.hasWallet())
        try {
            vault.store(listOf("not", "a", "phrase"))
            fail("invalid words accepted")
        } catch (_: IllegalArgumentException) {
        }
        assertTrue(vault.store(TestSeeds.BOB))
        assertEquals(TestSeeds.BOB, vault.words())
        assertEquals(Hex.encode(Bip39.toSeed(TestSeeds.BOB)), Hex.encode(vault.seed()!!))
        vault.backedUp = true
        assertTrue(vault.clear())
        assertNull(vault.words())
        assertNull(vault.seed())
        assertFalse(vault.backedUp)
    }

    @Test
    fun `panic wipe with no wallet ever created reports success`() = runTest {
        assertTrue(WalletService.panicWipe(context))
    }

    @Test
    fun `NIP-44 v2 matches the official vectors`() {
        val sec1 = BigInteger.ONE
        val pub2 = com.bitchat.android.wallet.cashu.Secp256k1.xOnlyHex(
            com.bitchat.android.wallet.cashu.P2pk.compressedPublicKey(BigInteger.TWO)
        )
        val key = Nip44.conversationKey(sec1, pub2)
        assertEquals("c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d", Hex.encode(key))
        val nonce = Hex.decode("0000000000000000000000000000000000000000000000000000000000000001")
        val payload = "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb"
        assertEquals(payload, Nip44.encrypt("a", key, nonce))
        assertEquals("a", Nip44.decrypt(payload, key))

        val key3 = Hex.decode("3e2b52a63be47d34fe0a80e34e73d436d6963bc8f39827f327057a9986c20a45")
        val payload3 = "ArY1I2xC2yDwIbuNHN/1ynXdGgzHLqdCrXUPMwELJPc7s7JqlCMJBAIIjfkpHReBPXeoMCyuClwgbT419jUWU1PwaNl4FEQYKCDKVJz+97Mp3K+Q2YGa77B6gpxB/lr1QgoqpDf7wDVrDmOqGoiPjWDqy8KzLueKDcm9BVP8xeTJIxs="
        assertEquals("表ポあA鷗ŒéＢ逍Üßªąñ丂㐀𠀀", Nip44.decrypt(payload3, key3))

        listOf(16 to 32, 32 to 32, 33 to 64, 37 to 64, 45 to 64, 49 to 64, 64 to 64, 65 to 96, 100 to 128, 111 to 128, 200 to 224, 250 to 256)
            .forEach { (len, padded) -> assertEquals("len $len", padded, Nip44.paddedLength(len)) }
        try {
            Nip44.decrypt("#Atqupco0WyaOW2IGDKcshwxI9xO8HgD/P8Ddt46CbxDbrhdG8VmJdU0MIDf06CUvEvdnr1cp1fiMtlM/GrE92xAc1K5odTpCzUB+mjXgbaqtntBUbTToSUoT0ovrlPwzGjyp", key)
            fail("unknown version accepted")
        } catch (_: IllegalArgumentException) {
        }
        val tampered = payload.replaceRange(60, 61, if (payload[60] == 'A') "B" else "A")
        try {
            Nip44.decrypt(tampered, key)
            fail("tampered payload accepted")
        } catch (_: Exception) {
        }
    }

    @Test
    fun `NUT-27 backup round trips and ignores strangers and forgeries`() {
        val seed = Bip39.toSeed(TestSeeds.ALICE)
        val event = MintListBackup.buildEvent(seed, listOf("https://mint.a.test", "https://mint.b.test/"), 1_800_000_000)
        assertEquals(30078, event.kind)
        assertEquals(listOf(listOf("d", "mint-list")), event.tags)
        assertEquals(2, java.util.Base64.getDecoder().decode(event.content)[0].toInt())
        val backup = MintListBackup.newest(seed, listOf(event))
        assertNotNull(backup)
        assertEquals(listOf("https://mint.a.test", "https://mint.b.test"), backup!!.mints)

        assertNull(MintListBackup.newest(Bip39.toSeed(TestSeeds.BOB), listOf(event)))
        val forged = event.copy(content = MintListBackup.buildEvent(seed, listOf("https://evil.test"), 1_900_000_000).content, createdAt = 1_900_000_000)
        assertEquals(backup, MintListBackup.newest(seed, listOf(event, forged)))
        val newer = MintListBackup.buildEvent(seed, listOf("https://mint.c.test"), 1_800_000_100)
        assertEquals(listOf("https://mint.c.test"), MintListBackup.newest(seed, listOf(event, newer))!!.mints)
    }

    @Test
    fun `history status survives codec round trip for every state`() {
        HistoryStatus.entries.forEach { status ->
            val entry = com.bitchat.android.wallet.engine.HistoryEntry(
                id = "h-$status", kind = com.bitchat.android.wallet.engine.HistoryKind.ECASH_SEND, status = status,
                amount = 5, fee = 1, unit = "sat", mintUrl = "https://m.test", createdAt = 1, updatedAt = 2,
                lockedUntil = 99, lockedToPeer = true, viaChat = true, tokenFingerprint = "ab",
            )
            assertEquals(entry, WalletRecordCodec.decodeHistory(WalletRecordCodec.encodeHistory(entry)))
        }
    }
}
