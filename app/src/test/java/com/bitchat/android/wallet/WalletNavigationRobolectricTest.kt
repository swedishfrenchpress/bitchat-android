package com.bitchat.android.wallet

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.bitchat.android.wallet.engine.InMemoryWalletStore
import com.bitchat.android.wallet.testsupport.FakeMint
import com.bitchat.android.wallet.ui.AddMintFlow
import com.bitchat.android.wallet.ui.SendEcashFlow
import com.bitchat.android.wallet.ui.AmountDigits
import com.bitchat.android.wallet.ui.WalletLaunch
import com.bitchat.android.wallet.ui.WalletRoute
import com.bitchat.android.wallet.ui.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Page-stack behavior of the wallet sheet: back inside flows, flows that finish themselves. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WalletNavigationRobolectricTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val mint = FakeMint()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        WalletService.storeFactory = { InMemoryWalletStore() }
        WalletService.apiFactory = com.bitchat.android.wallet.mint.MintApiFactory { mint }
        runBlocking { WalletService.panicWipe(app) }
        WalletService.createWallet(app)
    }

    @After
    fun tearDown() {
        runBlocking { WalletService.panicWipe(app) }
        Dispatchers.resetMain()
    }

    private fun waitFor(condition: () -> Boolean) {
        // The engine runs on IO; bound the wait instead of sleeping blindly.
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition not met in time", condition())
    }

    @Test
    fun `adding a mint closes the add page once it is saved`() {
        val vm = WalletViewModel(app)
        vm.open(WalletLaunch.Home)
        vm.startAddMint(mint.url)
        val route = vm.stack.value.last() as WalletRoute.AddMint
        waitFor { route.flow.step.value is AddMintFlow.Step.Preview }
        route.flow.confirm()
        waitFor { vm.stack.value.last() == WalletRoute.Home }
        assertTrue(vm.wallet.mint(mint.url) != null)
    }

    @Test
    fun `back from review returns to the amount and keeps it`() {
        val vm = WalletViewModel(app)
        runBlocking {
            vm.wallet.addMint(mint.url)
            vm.wallet.checkMintQuote(vm.wallet.createMintQuote(mint.url, 64).id)
        }
        vm.open(WalletLaunch.Home)
        vm.startSendEcash()
        val flow = (vm.stack.value.last() as WalletRoute.SendEcash).flow
        flow.mint.value = mint.url
        flow.amount.value = AmountDigits.of(21)
        flow.review()
        assertTrue(flow.step.value is SendEcashFlow.Step.Review)
        assertTrue(vm.back())
        assertEquals(SendEcashFlow.Step.Amount, flow.step.value)
        assertEquals(21L, flow.amount.value.value)
        assertTrue(vm.stack.value.last() is WalletRoute.SendEcash)
        assertTrue(vm.back())
        assertEquals(WalletRoute.Home, vm.stack.value.last())
        assertFalse(vm.back())
    }

    @Test
    fun `claim launched from chat before setup resumes after the wallet exists`() {
        runBlocking { WalletService.panicWipe(app) }
        val vm = WalletViewModel(app)
        vm.open(WalletLaunch.Claim("cashuBo2F0", viaChat = true))
        assertEquals(WalletRoute.Onboarding, vm.stack.value.single())
        vm.createWallet()
        assertTrue(vm.stack.value.last() is WalletRoute.Backup)
        vm.skipBackup()
        assertTrue(vm.stack.value.last() is WalletRoute.ReceiveEcash)
    }
}
