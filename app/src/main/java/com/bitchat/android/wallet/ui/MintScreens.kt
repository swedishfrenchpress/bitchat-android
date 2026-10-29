package com.bitchat.android.wallet.ui

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.R
import com.bitchat.android.ui.AboutSectionLabel
import com.bitchat.android.ui.theme.BitchatFontFamily
import com.bitchat.android.ui.theme.BitchatMotion
import com.bitchat.android.wallet.WalletService
import com.bitchat.android.wallet.cashu.MintInfo
import com.bitchat.android.wallet.engine.CashuWallet
import com.bitchat.android.wallet.engine.WalletError
import com.bitchat.android.wallet.engine.WalletException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Composable
internal fun MintsScreen(vm: WalletViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val unreachable by vm.unreachable.collectAsStateWithLifecycle()
    WalletPage {
        Spacer(Modifier.height(8.dp))
        WalletCard {
            state.mints.forEachIndexed { i, mint ->
                if (i > 0) WalletCardDivider()
                MintRow(mint, hidden = false, unreachable = mint.mint.url in unreachable) { vm.push(WalletRoute.MintDetail(mint.mint.url)) }
            }
            if (state.mints.isNotEmpty()) WalletCardDivider()
            WalletRow(title = stringResource(R.string.wallet_add_mint), icon = Icons.Filled.Add, titleColor = MaterialTheme.colorScheme.primary, onClick = { vm.startAddMint() })
        }
    }
}

/** Add a mint: type a URL, see what the mint says about itself, then confirm with the warning. */
class AddMintFlow(
    private val app: Application,
    private val wallet: CashuWallet,
    private val scope: CoroutineScope,
    prefill: String?,
    private val onAdded: () -> Unit,
) {
    sealed interface Step {
        data class Input(val text: String, val checking: Boolean, val error: ErrorCopy?) : Step
        data class Preview(val preview: CashuWallet.MintPreview, val adding: Boolean) : Step
    }

    private val _step = MutableStateFlow<Step>(Step.Input(prefill.orEmpty(), checking = false, error = null))
    val step: StateFlow<Step> = _step.asStateFlow()

    init {
        if (prefill != null) check()
    }

    fun update(text: String) {
        _step.value = Step.Input(text, checking = false, error = null)
    }

    fun check() {
        val input = _step.value as? Step.Input ?: return
        if (input.checking) return
        _step.value = input.copy(checking = true, error = null)
        scope.launch {
            _step.value = try {
                Step.Preview(wallet.previewMint(input.text), adding = false)
            } catch (e: WalletException) {
                input.copy(checking = false, error = errorCopy(app, if (e.error is WalletError.MintRejected) WalletError.MintNotFound else e.error))
            }
        }
    }

    fun confirm() {
        val preview = _step.value as? Step.Preview ?: return
        if (preview.adding) return
        _step.value = preview.copy(adding = true)
        scope.launch {
            runCatching { wallet.addMint(preview.preview) }
            onAdded()
        }
    }

    fun edit() {
        val preview = _step.value as? Step.Preview ?: return
        _step.value = Step.Input(preview.preview.url, checking = false, error = null)
    }
}

@Composable
internal fun AddMintScreen(vm: WalletViewModel, flow: AddMintFlow) {
    val step by flow.step.collectAsStateWithLifecycle()
    AnimatedContent(step, contentKey = { it::class }, transitionSpec = { fadeIn(tween(BitchatMotion.EMPHASIZED_MS)) togetherWith fadeOut(tween(BitchatMotion.STANDARD_MS)) }, label = "addMint") { s ->
        when (s) {
            is AddMintFlow.Step.Input -> WalletPage(bottom = {
                WalletButton(stringResource(R.string.wallet_add_mint_check), onClick = flow::check, enabled = s.text.isNotBlank(), loading = s.checking)
            }) {
                Spacer(Modifier.height(12.dp))
                WalletTextField(
                    value = s.text, onValueChange = flow::update,
                    placeholder = stringResource(R.string.wallet_add_mint_hint), singleLine = true, isError = s.error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Go),
                )
                if (WalletService.suggestTestMint && s.text.isBlank()) {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        WalletTextAction(stringResource(R.string.wallet_add_mint_suggest_test), onClick = { flow.update(WalletService.TEST_MINT_URL); flow.check() })
                    }
                }
                s.error?.let {
                    Spacer(Modifier.height(8.dp))
                    WalletNotice(it.title + ". " + it.body, tone = NoticeTone.Danger)
                }
                Spacer(Modifier.height(12.dp))
                WalletNotice(stringResource(R.string.wallet_add_mint_warning), tone = NoticeTone.Warning, icon = Icons.Filled.WarningAmber)
            }
            is AddMintFlow.Step.Preview -> WalletPage(bottom = {
                WalletButton(stringResource(R.string.wallet_add_mint_confirm), onClick = flow::confirm, loading = s.adding)
                WalletButton(stringResource(R.string.wallet_cancel), onClick = flow::edit, tone = WalletButtonTone.Neutral)
            }) {
                MintSummary(s.preview.url, s.preview.info, s.preview.keysets.filter { it.active }.maxOfOrNull { it.inputFeePpk } ?: 0, s.preview.units)
                Spacer(Modifier.height(12.dp))
                WalletNotice(stringResource(R.string.wallet_add_mint_warning), tone = NoticeTone.Warning, icon = Icons.Filled.WarningAmber)
            }
        }
    }
}

@Composable
private fun MintSummary(url: String, info: MintInfo?, feePpk: Long, units: Set<String>) {
    val colorScheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Icon(painterResource(R.drawable.ic_spec_ecash), contentDescription = null, tint = colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
        Text(info?.name?.take(40) ?: com.bitchat.android.wallet.cashu.MintUrl.host(url), fontFamily = BitchatFontFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
        Text(com.bitchat.android.wallet.cashu.MintUrl.host(url), fontFamily = BitchatFontFamily, fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
        info?.description?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it.take(200), fontFamily = BitchatFontFamily, fontSize = 13.sp, color = colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 28.dp))
        }
    }
    AboutSectionLabel(stringResource(R.string.wallet_mint_supports))
    val yesText = stringResource(R.string.wallet_yes)
    val noText = stringResource(R.string.wallet_no)
    WalletCard {
        fun yes(b: Boolean) = if (b) yesText else noText
        WalletValueRow(stringResource(R.string.wallet_mint_feature_lightning), yes(info?.mintMethods?.any { it.method == "bolt11" } == true))
        WalletCardDivider()
        WalletValueRow(stringResource(R.string.wallet_mint_feature_restore), yes(info?.supports(9) == true))
        WalletCardDivider()
        WalletValueRow(stringResource(R.string.wallet_mint_feature_lock), yes(info?.supports(11) == true))
        WalletCardDivider()
        WalletValueRow(stringResource(R.string.wallet_mint_feature_offline), yes(info?.supports(12) == true))
        WalletCardDivider()
        WalletValueRow(stringResource(R.string.wallet_mint_fee), if (feePpk == 0L) stringResource(R.string.wallet_mint_fee_none) else stringResource(R.string.wallet_mint_fee_value, feePpk.toString()))
        WalletCardDivider()
        WalletValueRow(stringResource(R.string.wallet_mint_units), units.sorted().joinToString(", "))
        info?.version?.let {
            WalletCardDivider()
            WalletValueRow(stringResource(R.string.wallet_mint_software), it.take(40))
        }
    }
    info?.motd?.takeIf { it.isNotBlank() }?.let {
        AboutSectionLabel(stringResource(R.string.wallet_mint_motd))
        // Mint-supplied text: shown as plain, bounded text, never as a link or markup.
        WalletNotice(it.take(280))
    }
}

@Composable
internal fun MintDetailScreen(vm: WalletViewModel, url: String) {
    val state by vm.state.collectAsStateWithLifecycle()
    val balance = state.mint(url) ?: return
    val mint = balance.mint
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    val funds = balance.available.entries.filter { it.value > 0 } + balance.pendingOut.entries.filter { it.value > 0 }
    WalletPage(bottom = {
        WalletButton(stringResource(R.string.wallet_mint_check_coins), loading = checking, tone = WalletButtonTone.Neutral, onClick = {
            checking = true
            scope.launch {
                checkResult = try {
                    val removed = vm.wallet.checkProofStates(url)
                    if (removed == 0) context.getString(R.string.wallet_mint_checked_none) else context.getString(R.string.wallet_mint_checked, removed)
                } catch (e: WalletException) {
                    errorCopy(context, e.error).let { it.title + ". " + it.body }
                }
                checking = false
            }
        })
        WalletButton(stringResource(R.string.wallet_mint_remove), onClick = { confirmRemove = true }, tone = WalletButtonTone.Destructive)
    }) {
        MintSummary(url, mint.info, mint.keysets.filter { it.active }.maxOfOrNull { it.inputFeePpk } ?: 0, mint.units)
        AboutSectionLabel(stringResource(R.string.wallet_mint_balance))
        WalletCard {
            val rows = balance.available.entries.filter { it.value > 0 }.ifEmpty { listOf(java.util.AbstractMap.SimpleEntry("sat", 0L)) }
            rows.forEachIndexed { i, (unit, amount) ->
                if (i > 0) WalletCardDivider()
                WalletValueRow(unit, formatAmount(amount, unit), emphasized = true)
            }
            balance.pendingOut.entries.filter { it.value > 0 }.forEach { (unit, amount) ->
                WalletCardDivider()
                WalletValueRow(stringResource(R.string.wallet_note_waiting_claim), formatAmount(amount, unit))
            }
        }
        checkResult?.let {
            Spacer(Modifier.height(12.dp))
            WalletNotice(it)
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.wallet_mint_remove_title, mint.displayName), fontFamily = BitchatFontFamily) },
            text = {
                Text(
                    if (funds.isEmpty()) stringResource(R.string.wallet_mint_remove_body)
                    else stringResource(R.string.wallet_mint_remove_body_funds, funds.joinToString(" · ") { formatAmount(it.value, it.key) }),
                    fontFamily = BitchatFontFamily,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        try {
                            vm.wallet.removeMint(url, force = true)
                            vm.back()
                        } catch (e: WalletException) {
                            checkResult = errorCopy(context, e.error).let { it.title + ". " + it.body }
                        }
                    }
                }) { Text(stringResource(R.string.wallet_mint_remove_confirm), fontFamily = BitchatFontFamily, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.wallet_cancel), fontFamily = BitchatFontFamily) } },
        )
    }
}
