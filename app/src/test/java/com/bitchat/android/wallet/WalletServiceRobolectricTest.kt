package com.bitchat.android.wallet

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * WalletService lifecycle against the real encrypted vault (EncryptedSharedPreferences works
 * under the default Robolectric config, as in EncryptionServiceTest).
 */
@RunWith(RobolectricTestRunner::class)
class WalletServiceRobolectricTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    init {
        // Robolectric has no JCA AndroidKeyStore for the sealed store; the on-device wipe is
        // covered by WalletPanicWipeInstrumentedTest.
        WalletService.storeFactory = { com.bitchat.android.wallet.engine.InMemoryWalletStore() }
    }

    @Test
    fun `wallet marker tracks stored words, not the encrypted file`() = runTest {
        assertTrue(WalletService.panicWipe(context))
        assertFalse(WalletService.walletExists(context))
        // Opening the sheet creates the encrypted prefs file before any words exist.
        WalletService.vault(context)
        assertFalse(WalletService.walletExists(context))
        WalletService.createWallet(context)
        assertTrue(WalletService.walletExists(context))
        val stages = WalletService.panicWipeStages(context)
        assertTrue(stages.toString(), stages.values.all { it })
        assertFalse(WalletService.walletExists(context))
        assertFalse(WalletService.hasWallet(context))
    }
}
