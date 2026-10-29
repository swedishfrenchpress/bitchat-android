package com.bitchat.android.wallet

import androidx.test.platform.app.InstrumentationRegistry
import com.bitchat.android.wallet.data.SqliteWalletStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The panic wipe against the real Android Keystore, SQLite files and encrypted prefs. Plain
 * JUnit (no Espresso), so it runs on current emulator images too.
 */
class WalletPanicWipeInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun panicWipeRemovesWordsStoreAndMarkerAndConfirmsEveryStage() = runBlocking {
        WalletService.panicWipe(context)
        WalletService.createWallet(context)
        val wallet = WalletService.wallet(context)
        wallet.load()
        wallet.putSetting("probe", "value")
        assertTrue(context.getDatabasePath(SqliteWalletStore.DATABASE_NAME).exists())
        assertTrue(WalletService.walletExists(context))

        val stages = WalletService.panicWipeStages(context)
        assertTrue(stages.toString(), stages.values.all { it })
        assertFalse(context.getDatabasePath(SqliteWalletStore.DATABASE_NAME).exists())
        assertFalse(WalletService.walletExists(context))
        assertFalse(WalletService.hasWallet(context))

        // A straggling write on the old engine must not bring the database back.
        runCatching { wallet.putSetting("late", "write") }
        assertFalse(context.getDatabasePath(SqliteWalletStore.DATABASE_NAME).exists())
    }
}
