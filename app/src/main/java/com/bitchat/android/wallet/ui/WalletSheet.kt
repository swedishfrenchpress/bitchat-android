package com.bitchat.android.wallet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bitchat.android.R
import com.bitchat.android.core.ui.component.sheet.BitchatBottomSheet
import com.bitchat.android.core.ui.component.sheet.BitchatSheetTitle
import com.bitchat.android.core.ui.component.sheet.BitchatSheetTopBar
import com.bitchat.android.ui.AboutHorizontalPadding
import com.bitchat.android.ui.theme.BitchatMotion

/**
 * The wallet: one full-height Bitchat sheet hosting a small page stack. Pushes slide in from
 * the trailing edge, pops from the leading edge, both with a fade at Bitchat's emphasized
 * duration. The close button always closes the whole sheet; back walks the stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSheet(launch: WalletLaunch, onDismiss: () -> Unit) {
    val vm: WalletViewModel = viewModel()
    LaunchedEffect(launch) { vm.open(launch) }
    DisposableEffect(Unit) { onDispose { vm.closed() } }
    val stack by vm.stack.collectAsStateWithLifecycle()

    BitchatBottomSheet(onDismissRequest = onDismiss) {
        BackHandler(enabled = stack.size > 1) { vm.back() }
        Column(Modifier.fillMaxSize()) {
            BitchatSheetTopBar(
                onClose = onDismiss,
                title = { BitchatSheetTitle(routeTitle(stack.last())) },
                navigationIcon = if (stack.size > 1) {
                    { WalletBackIcon { vm.back() } }
                } else null,
            )
            val distance = with(LocalDensity.current) { 28.dp.roundToPx() }
            AnimatedContent(
                targetState = stack,
                contentKey = { it.last() },
                transitionSpec = {
                    val forward = targetState.size >= initialState.size
                    val enter = slideInHorizontally(tween(BitchatMotion.EMPHASIZED_MS, easing = FastOutSlowInEasing)) {
                        if (forward) distance else -distance
                    } + fadeIn(tween(BitchatMotion.EMPHASIZED_MS))
                    val exit = slideOutHorizontally(tween(BitchatMotion.EMPHASIZED_MS, easing = FastOutSlowInEasing)) {
                        if (forward) -distance else distance
                    } + fadeOut(tween(BitchatMotion.STANDARD_MS))
                    enter togetherWith exit
                },
                label = "walletPage",
                modifier = Modifier.fillMaxSize(),
            ) { pages ->
                RouteContent(pages.last(), vm, onDismiss)
            }
        }
    }
}

@Composable
private fun RouteContent(route: WalletRoute, vm: WalletViewModel, onDismiss: () -> Unit) {
    when (route) {
        WalletRoute.Onboarding -> OnboardingScreen(vm)
        WalletRoute.Home -> HomeScreen(vm)
        WalletRoute.ReceiveChoice -> ReceiveChoiceScreen(vm)
        WalletRoute.SendChoice -> SendChoiceScreen(vm)
        WalletRoute.Mints -> MintsScreen(vm)
        WalletRoute.History -> HistoryScreen(vm)
        WalletRoute.Settings -> SettingsScreen(vm)
        is WalletRoute.MintDetail -> MintDetailScreen(vm, route.url)
        is WalletRoute.HistoryDetail -> HistoryDetailScreen(vm, route.id)
        is WalletRoute.Backup -> BackupScreen(vm, route.flow)
        is WalletRoute.Restore -> RestoreScreen(vm, route.flow)
        is WalletRoute.ReceiveLightning -> ReceiveLightningScreen(vm, route.flow, onDismiss)
        is WalletRoute.ReceiveEcash -> ReceiveEcashScreen(vm, route.flow, onDismiss)
        is WalletRoute.SendEcash -> SendEcashScreen(vm, route.flow, onDismiss)
        is WalletRoute.SendLightning -> SendLightningScreen(vm, route.flow, onDismiss)
        is WalletRoute.AddMint -> AddMintScreen(vm, route.flow)
    }
}

@Composable
private fun routeTitle(route: WalletRoute): String = stringResource(
    when (route) {
        WalletRoute.Onboarding, WalletRoute.Home -> R.string.wallet_title
        WalletRoute.ReceiveChoice, is WalletRoute.ReceiveLightning -> R.string.wallet_receive_title
        is WalletRoute.ReceiveEcash -> R.string.wallet_token_input_title
        WalletRoute.SendChoice, is WalletRoute.SendEcash -> R.string.wallet_send_title
        is WalletRoute.SendLightning -> R.string.wallet_melt_input_title
        WalletRoute.Mints, is WalletRoute.MintDetail -> R.string.wallet_mints_title
        is WalletRoute.AddMint -> R.string.wallet_add_mint_title
        WalletRoute.History -> R.string.wallet_history_title
        is WalletRoute.HistoryDetail -> R.string.wallet_detail_title
        WalletRoute.Settings -> R.string.wallet_settings_title
        is WalletRoute.Backup -> R.string.wallet_words_title
        is WalletRoute.Restore -> R.string.wallet_restore_title
    }
)

/**
 * Page scaffold: scrolling content, with primary actions pinned at the bottom above the
 * keyboard and navigation bar, so the main button sits in the same place on every step.
 */
@Composable
internal fun WalletPage(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(top = 8.dp, bottom = 16.dp),
            content = content,
        )
        if (bottom != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = AboutHorizontalPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = bottom,
            )
        } else {
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
}
