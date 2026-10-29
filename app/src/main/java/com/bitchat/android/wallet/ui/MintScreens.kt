package com.bitchat.android.wallet.ui

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
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
import com.bitchat.android.ui.theme.BitchatFontFamily
import com.bitchat.android.ui.theme.BitchatMotion
import com.bitchat.android.wallet.WalletService
import com.bitchat.android.wallet.cashu.MintInfo
import com.bitchat.android.wallet.cashu.MintUrl
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
                val copy = errorCopy(app, if (e.error is WalletError.MintRejected) WalletError.MintNotFound else e.error)
                // No money moves when adding a mint, so "nothing was spent" would only be noise.
                val body = if (e.error == WalletError.MintUnreachable) app.getString(R.string.wallet_error_mint_check_unreachable_body) else copy.body
                input.copy(checking = false, error = copy.copy(body = body))
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

    fun stepBack(): Boolean = when (val s = _step.value) {
        is Step.Preview -> if (s.adding) true else { edit(); true }
        else -> false
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
                    Spacer(Modifier.height(4.dp))
                    WalletNotice(it.line(), tone = NoticeTone.Danger)
                }
            }
            is AddMintFlow.Step.Preview -> WalletPage(bottom = {
                // Back (arrow or gesture) returns to the address; no separate Cancel.
                WalletButton(stringResource(R.string.wallet_add_mint_confirm), onClick = flow::confirm, loading = s.adding)
            }) {
                MintSummary(s.preview.url, s.preview.info)
                Spacer(Modifier.height(8.dp))
                WalletNotice(stringResource(R.string.wallet_add_mint_warning), tone = NoticeTone.Warning, icon = Icons.Filled.WarningAmber, centered = true)
            }
        }
    }
}

/** Who the mint is: name and address, nothing else. Mint-supplied text is bounded. */
@Composable
private fun MintSummary(url: String, info: MintInfo?) {
    val colorScheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Icon(painterResource(R.drawable.ic_spec_ecash), contentDescription = null, tint = colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
        Text(info?.name?.take(40) ?: MintUrl.host(url), fontFamily = BitchatFontFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
        WalletNotice(MintUrl.host(url), centered = true)
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
    val allUnspent = stringResource(R.string.wallet_mint_checked_none)
    val resources = androidx.compose.ui.platform.LocalResources.current
    WalletPage(bottom = {
        WalletButton(stringResource(R.string.wallet_mint_remove), onClick = { confirmRemove = true }, tone = WalletButtonTone.Destructive)
    }) {
        if (vm.wallet.isSuspect(url)) {
            WalletNotice(stringResource(R.string.wallet_mint_suspect), tone = NoticeTone.Danger, icon = Icons.Filled.WarningAmber, centered = true)
        }
        // The balance held here, who holds it, and anything the mint wants to say.
        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AmountHero(balance.available["sat"] ?: 0, "sat")
            balance.available.filter { it.key != "sat" && it.value > 0 }.forEach { (unit, amount) ->
                Text(formatAmount(amount, unit), fontFamily = BitchatFontFamily, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(mint.displayName, fontFamily = BitchatFontFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WalletNotice(mint.host, centered = true)
            state.unclaimed(url).forEach { (unit, amount) ->
                WalletNotice(stringResource(R.string.wallet_pending_out, formatAmount(amount, unit)), tone = NoticeTone.Warning, centered = true)
            }
            // Mint-supplied text: plain and bounded, never a link or markup.
            mint.info?.motd?.takeIf { it.isNotBlank() }?.let { WalletNotice(it.take(280), centered = true) }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            WalletTextAction(stringResource(R.string.wallet_mint_check_coins), enabled = !checking, onClick = {
                checking = true
                scope.launch {
                    checkResult = try {
                        val removed = vm.wallet.checkProofStates(url)
                        if (removed == 0) allUnspent else resources.getString(R.string.wallet_mint_checked, removed)
                    } catch (e: WalletException) {
                        errorCopy(context, e.error).line()
                    }
                    checking = false
                }
            })
        }
        checkResult?.let { WalletNotice(it, centered = true) }
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
                            vm.popRoute()
                        } catch (e: WalletException) {
                            checkResult = errorCopy(context, e.error).line()
                        }
                    }
                }) { Text(stringResource(R.string.wallet_mint_remove_confirm), fontFamily = BitchatFontFamily, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.wallet_cancel), fontFamily = BitchatFontFamily) } },
        )
    }
}
