package com.bitchat.android.wallet.ui

import android.app.Application
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.R
import com.bitchat.android.ui.AboutHorizontalPadding
import com.bitchat.android.ui.theme.BitchatFontFamily
import com.bitchat.android.ui.theme.BitchatMotion
import com.bitchat.android.ui.theme.LocalBitchatPalette
import com.bitchat.android.wallet.WalletService
import com.bitchat.android.wallet.engine.Bolt11
import com.bitchat.android.wallet.engine.CashuWallet
import com.bitchat.android.wallet.engine.HistoryEntry
import com.bitchat.android.wallet.engine.WalletError
import com.bitchat.android.wallet.engine.WalletException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val pageFade = fadeIn(tween(BitchatMotion.EMPHASIZED_MS)) togetherWith fadeOut(tween(BitchatMotion.STANDARD_MS))

// MARK: - Ecash send

class SendEcashFlow(
    private val app: Application,
    private val wallet: CashuWallet,
    private val scope: CoroutineScope,
    initialMint: String?,
    val chat: WalletLaunch.SendToChat?,
    private val unreachable: Set<String>,
) {
    sealed interface Step {
        data object Amount : Step
        data class Review(val preview: CashuWallet.SendPreview, val lock: Boolean, val creating: Boolean) : Step
        data class Token(val entry: HistoryEntry, val sentToChat: Boolean) : Step
        data class Failed(val copy: ErrorCopy, val offlineHint: Pair<Long?, Long?>? = null) : Step
    }

    private val _step = MutableStateFlow<Step>(Step.Amount)
    val step: StateFlow<Step> = _step.asStateFlow()
    val amount = MutableStateFlow(AmountDigits())
    val mint = MutableStateFlow(initialMint)
    val memo = MutableStateFlow("")
    val problem = MutableStateFlow<ErrorCopy?>(null)
    private val historyId = CashuWallet.newId()

    val mintOffline: Boolean get() = mint.value in unreachable

    /** Locking is offered only when we know the peer's key and the mint enforces locks. */
    fun lockAvailable(): Boolean =
        chat?.lockPubkey != null && wallet.mint(mint.value ?: "")?.info?.supports(11) == true && !mintOffline

    private fun lockRequest(on: Boolean) = if (on && lockAvailable()) {
        CashuWallet.LockRequest(chat!!.lockPubkey!!, System.currentTimeMillis() / 1000 + LOCK_SECONDS)
    } else null

    /**
     * Locking starts off: a recipient whose app cannot sign with their Nostr key (iOS Bitchat
     * today, most other wallets) could not claim it for a week (decisions.md D-020).
     */
    fun review(lock: Boolean = (_step.value as? Step.Review)?.lock ?: false) {
        val url = mint.value ?: return
        problem.value = null
        try {
            val preview = wallet.previewSend(url, amount.value.value, lock = lockRequest(lock))
            if (mintOffline && !preview.offline) {
                _step.value = Step.Failed(errorCopy(app, WalletError.MintUnreachable), wallet.offlineAmountsNear(url, "sat", amount.value.value))
                return
            }
            _step.value = Step.Review(preview, lock = lock && lockAvailable(), creating = false)
        } catch (e: WalletException) {
            problem.value = errorCopy(app, e.error)
        }
    }

    fun create() {
        val review = _step.value as? Step.Review ?: return
        if (review.creating) return
        _step.value = review.copy(creating = true)
        scope.launch {
            try {
                val entry = atLeast {
                    wallet.send(
                        review.preview.mintUrl, review.preview.amount,
                        lock = lockRequest(review.lock),
                        memo = memo.value,
                        viaChat = chat != null,
                        historyId = historyId,
                    )
                }
                wallet.putSetting(WalletService.SETTING_LAST_MINT, review.preview.mintUrl)
                _step.value = Step.Token(entry, sentToChat = false)
            } catch (e: WalletException) {
                val hint = if (e.error == WalletError.MintUnreachable || e.error == WalletError.OutcomeUnknown) {
                    wallet.offlineAmountsNear(review.preview.mintUrl, "sat", review.preview.amount)
                } else null
                _step.value = Step.Failed(errorCopy(app, e.error), hint?.takeIf { it.first != null || it.second != null })
            }
        }
    }

    fun deliverToChat() {
        val token = _step.value as? Step.Token ?: return
        val text = token.entry.token ?: return
        chat?.deliver?.invoke(text)
        _step.value = token.copy(sentToChat = true)
    }

    fun stepBack(): Boolean = when (val s = _step.value) {
        is Step.Review -> if (s.creating) true else { _step.value = Step.Amount; true }
        is Step.Failed -> { _step.value = Step.Amount; true }
        else -> false
    }

    fun backToAmount(suggested: Long? = null) {
        suggested?.let { amount.value = AmountDigits.of(it) }
        _step.value = Step.Amount
    }

    companion object {
        /** Seven days: long enough for someone offline over a weekend (decisions.md D-013). */
        const val LOCK_SECONDS = 7 * 24 * 3600L
    }
}

@Composable
internal fun SendEcashScreen(vm: WalletViewModel, flow: SendEcashFlow, onDismiss: () -> Unit) {
    AdoptDefaultMint(vm, flow.mint)
    val step by flow.step.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    AnimatedContent(step, contentKey = { it::class }, transitionSpec = { pageFade }, label = "ecashSend") { s ->
        when (s) {
            SendEcashFlow.Step.Amount -> {
                val amount by flow.amount.collectAsStateWithLifecycle()
                val mintUrl by flow.mint.collectAsStateWithLifecycle()
                val problem by flow.problem.collectAsStateWithLifecycle()
                val available = state.mint(mintUrl ?: "")?.available?.get("sat") ?: 0
                val over = amount.value > available
                AmountStep(
                    amount = amount, unit = "sat",
                    contextLine = when {
                        over -> stringResource(R.string.wallet_amount_over_balance, formatAmount(available, "sat"))
                        problem != null -> problem!!.body
                        else -> stringResource(R.string.wallet_amount_available, formatAmount(available, "sat"))
                    },
                    contextIsError = over || problem != null,
                    mints = state.mints.filter { (it.available["sat"] ?: 0) > 0 },
                    selectedMint = mintUrl,
                    onMint = { flow.mint.value = it; flow.problem.value = null },
                    onChange = { flow.amount.value = it; flow.problem.value = null },
                    continueLabel = stringResource(R.string.wallet_continue),
                    onContinue = { flow.review() },
                    continueEnabled = amount.value > 0 && !over && mintUrl != null,
                    maxAmount = available,
                )
            }
            is SendEcashFlow.Step.Review -> SendReview(s, flow)
            is SendEcashFlow.Step.Token -> TokenStep(s, flow, onDone = { if (flow.chat != null) onDismiss() else vm.home() })
            is SendEcashFlow.Step.Failed -> {
                val context = LocalContext.current
                val (lower, higher) = s.offlineHint ?: (null to null)
                WalletPage(centered = true, terminal = true, bottom = {
                    if (lower != null) WalletButton(formatAmount(lower, "sat"), onClick = { flow.backToAmount(lower); flow.review() })
                    if (higher != null) WalletButton(formatAmount(higher, "sat"), onClick = { flow.backToAmount(higher); flow.review() }, tone = WalletButtonTone.Neutral)
                    WalletButton(stringResource(R.string.wallet_done), onClick = { flow.backToAmount() }, tone = WalletButtonTone.Neutral)
                }) {
                    WalletStatusPanel(if (s.copy.pending) StatusKind.Pending else StatusKind.Error, s.copy.title, s.copy.body)
                    val hint = when {
                        lower != null && higher != null -> stringResource(R.string.wallet_offline_suggest, formatAmount(lower, "sat"), formatAmount(higher, "sat"))
                        lower != null || higher != null -> stringResource(R.string.wallet_offline_suggest_one, formatAmount((lower ?: higher)!!, "sat"))
                        else -> null
                    }
                    if (hint != null) WalletNotice(hint, icon = Icons.Filled.CloudOff, centered = true)
                }
            }
        }
    }
}

@Composable
private fun SendReview(step: SendEcashFlow.Step.Review, flow: SendEcashFlow) {
    val p = step.preview
    val memo by flow.memo.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme
    val peer = flow.chat?.peerLabel
    WalletPage(bottom = {
        WalletButton(stringResource(R.string.wallet_review_create), onClick = flow::create, loading = step.creating)
    }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AmountHero(p.amount, p.unit)
        }
        WalletCard {
            WalletValueRow(stringResource(R.string.wallet_review_mint), flow.mint.value?.let { com.bitchat.android.wallet.cashu.MintUrl.host(it) } ?: "")
            WalletCardDivider(plain = true)
            WalletValueRow(stringResource(R.string.wallet_review_fees), formatAmount(p.fee, p.unit))
            WalletCardDivider(plain = true)
            WalletValueRow(stringResource(R.string.wallet_review_total), formatAmount(p.total, p.unit), emphasized = true)
            if (flow.lockAvailable()) {
                WalletCardDivider()
                WalletRow(
                    title = stringResource(R.string.wallet_review_lock, peer ?: stringResource(R.string.wallet_peer_fallback)),
                    subtitle = stringResource(R.string.wallet_review_lock_desc),
                    icon = if (step.lock) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    onClick = { flow.review(!step.lock) },
                    trailing = {
                        Switch(
                            checked = step.lock, onCheckedChange = { flow.review(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colorScheme.primary, uncheckedThumbColor = Color.White, uncheckedTrackColor = colorScheme.surfaceVariant),
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        WalletTextField(
            value = memo, onValueChange = { flow.memo.value = it.take(80) },
            placeholder = stringResource(R.string.wallet_review_memo_hint), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        // Only worth a line when the mint is down and this still works.
        if (flow.mintOffline && p.offline) {
            Spacer(Modifier.height(8.dp))
            WalletNotice(stringResource(R.string.wallet_review_offline_ready), icon = Icons.Filled.CloudOff)
        }
    }
}

@Composable
private fun TokenStep(step: SendEcashFlow.Step.Token, flow: SendEcashFlow, onDone: () -> Unit) {
    val token = step.entry.token ?: return
    var confirmPublic by remember { mutableStateOf(false) }
    WalletPage(centered = true, terminal = true, bottom = {
        if (flow.chat != null && !step.sentToChat) {
            WalletButton(stringResource(R.string.wallet_token_send_chat), onClick = {
                if (flow.chat.isPublic && !step.entry.lockedToPeer) confirmPublic = true else flow.deliverToChat()
            })
        }
        WalletButton(
            stringResource(if (step.sentToChat) R.string.wallet_done else R.string.wallet_done),
            onClick = onDone,
            tone = if (flow.chat != null && !step.sentToChat) WalletButtonTone.Neutral else WalletButtonTone.Primary,
        )
    }) {
        WalletStatusPanel(
            StatusKind.Success,
            stringResource(if (step.sentToChat) R.string.wallet_token_sent_chat else R.string.wallet_token_title),
            if (step.entry.lockedToPeer) stringResource(R.string.wallet_token_body_locked, flow.chat?.peerLabel ?: stringResource(R.string.wallet_peer_fallback))
            else stringResource(R.string.wallet_token_body_bearer),
        )
        TokenShareBlock(token)
    }
    if (confirmPublic) {
        AlertDialog(
            onDismissRequest = { confirmPublic = false },
            title = { Text(stringResource(R.string.wallet_public_warning_title), fontFamily = BitchatFontFamily) },
            text = { Text(stringResource(R.string.wallet_public_warning_body), fontFamily = BitchatFontFamily) },
            confirmButton = { TextButton(onClick = { confirmPublic = false; flow.deliverToChat() }) { Text(stringResource(R.string.wallet_public_warning_confirm), fontFamily = BitchatFontFamily, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmPublic = false }) { Text(stringResource(R.string.wallet_cancel), fontFamily = BitchatFontFamily) } },
        )
    }
}

/** QR (when it fits), plus copy and the system share sheet. */
@Composable
internal fun TokenShareBlock(token: String) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (token.length <= MAX_QR_LENGTH) {
            WalletQrCode(token, stringResource(R.string.wallet_cd_token_qr), size = 220.dp)
        } else {
            WalletNotice(stringResource(R.string.wallet_token_too_long_qr), centered = true)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = AboutHorizontalPadding, vertical = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            WalletButton(stringResource(R.string.wallet_token_copy), onClick = { copyToClipboard(context, "Cashu token", token) }, tone = WalletButtonTone.Neutral, modifier = Modifier.weight(1f))
            WalletButton(stringResource(R.string.wallet_share), onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, token)
                runCatching { context.startActivity(Intent.createChooser(send, null)) }
            }, tone = WalletButtonTone.Neutral, modifier = Modifier.weight(1f))
        }
    }
}

// MARK: - Lightning send

class SendLightningFlow(
    private val app: Application,
    private val wallet: CashuWallet,
    private val scope: CoroutineScope,
    initialMint: String?,
) {
    sealed interface Step {
        data class Input(val text: String, val invalid: Boolean, val scanning: Boolean, val quoting: Boolean) : Step
        data class Review(val preview: CashuWallet.MeltPreview) : Step
        data class Paying(val slow: Boolean) : Step
        data class Paid(val entry: HistoryEntry) : Step
        data object Pending : Step
        data class Failed(val copy: ErrorCopy, val requote: Boolean) : Step
    }

    private val _step = MutableStateFlow<Step>(Step.Input("", invalid = false, scanning = false, quoting = false))
    val step: StateFlow<Step> = _step.asStateFlow()
    val mint = MutableStateFlow(initialMint)
    private var invoice: String = ""
    private var historyId = CashuWallet.newId()

    fun update(text: String) {
        _step.value = Step.Input(text, invalid = false, scanning = false, quoting = false)
    }

    fun stepBack(): Boolean = when (val s = _step.value) {
        is Step.Review -> { update(invoice); true }
        is Step.Input -> if (s.scanning) { scan(false); true } else false
        else -> false
    }

    fun scan(on: Boolean) {
        (_step.value as? Step.Input)?.let { _step.value = it.copy(scanning = on) }
    }

    fun submit(text: String = (_step.value as? Step.Input)?.text.orEmpty()) {
        val parsed = Bolt11.parse(text)
        if (parsed == null) {
            _step.value = Step.Input(text, invalid = true, scanning = false, quoting = false)
            return
        }
        invoice = parsed.raw
        quote()
    }

    fun quote() {
        val url = mint.value ?: return
        _step.value = Step.Input(invoice, invalid = false, scanning = false, quoting = true)
        scope.launch {
            _step.value = try {
                Step.Review(atLeast { wallet.previewMelt(url, invoice) })
            } catch (e: WalletException) {
                Step.Failed(errorCopy(app, e.error), requote = e.error == WalletError.MintUnreachable)
            }
        }
    }

    fun pay() {
        val review = _step.value as? Step.Review ?: return
        _step.value = Step.Paying(slow = false)
        scope.launch {
            val slowTimer = launch {
                delay(3_000)
                if (_step.value is Step.Paying) _step.value = Step.Paying(slow = true)
            }
            _step.value = try {
                Step.Paid(atLeast(800) { wallet.melt(review.preview, historyId) })
            } catch (e: WalletException) {
                when (e.error) {
                    WalletError.PaymentPending, WalletError.OutcomeUnknown -> Step.Pending
                    WalletError.QuoteExpired -> Step.Failed(errorCopy(app, e.error), requote = true)
                    // A failed payment gets a fresh id so a retry is a new attempt, not a replay.
                    else -> Step.Failed(errorCopy(app, e.error), requote = errorCopy(app, e.error).retryable).also { historyId = CashuWallet.newId() }
                }
            } finally {
                slowTimer.cancel()
            }
        }
    }
}

@Composable
internal fun SendLightningScreen(vm: WalletViewModel, flow: SendLightningFlow, onDismiss: () -> Unit) {
    AdoptDefaultMint(vm, flow.mint)
    val step by flow.step.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    AnimatedContent(step, contentKey = { it::class }, transitionSpec = { pageFade }, label = "lnSend") { s ->
        when (s) {
            is SendLightningFlow.Step.Input -> {
                val mintUrl by flow.mint.collectAsStateWithLifecycle()
                WalletPage(bottom = {
                    WalletButton(stringResource(R.string.wallet_continue), onClick = { flow.submit() }, enabled = s.text.isNotBlank() && mintUrl != null, loading = s.quoting)
                }) {
                    Spacer(Modifier.height(12.dp))
                    MintPicker(state.mints.filter { (it.available["sat"] ?: 0) > 0 }, mintUrl, "sat") { flow.mint.value = it }
                    Spacer(Modifier.height(12.dp))
                    if (s.scanning) {
                        WalletScanner(onResult = { flow.submit(it) })
                        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            WalletTextAction(stringResource(R.string.wallet_cancel), onClick = { flow.scan(false) })
                        }
                    } else {
                        PasteOrScanField(s.text, flow::update, stringResource(R.string.wallet_melt_input_hint), s.invalid, onScan = { flow.scan(true) })
                        if (s.invalid) WalletNotice(stringResource(R.string.wallet_melt_invalid), tone = NoticeTone.Danger)
                    }
                }
            }
            is SendLightningFlow.Step.Review -> MeltReview(s.preview, flow)
            is SendLightningFlow.Step.Paying -> WalletPage(centered = true) {
                WalletStatusPanel(StatusKind.Loading, stringResource(R.string.wallet_paying), if (s.slow) stringResource(R.string.wallet_paying_slow) else null)
            }
            is SendLightningFlow.Step.Paid -> WalletPage(centered = true, terminal = true, bottom = { WalletButton(stringResource(R.string.wallet_done), onClick = { vm.home() }) }) {
                WalletStatusPanel(
                    StatusKind.Success, stringResource(R.string.wallet_paid_title),
                    stringResource(R.string.wallet_paid_body, formatAmount(s.entry.amount, s.entry.unit), formatAmount(s.entry.fee, s.entry.unit)),
                )
            }
            SendLightningFlow.Step.Pending -> WalletPage(centered = true, terminal = true, bottom = { WalletButton(stringResource(R.string.wallet_done), onClick = { vm.home() }) }) {
                WalletStatusPanel(StatusKind.Pending, stringResource(R.string.wallet_payment_pending_title), stringResource(R.string.wallet_payment_pending_body))
            }
            is SendLightningFlow.Step.Failed -> ErrorStep(s.copy, onRetry = if (s.requote) flow::quote else null, onClose = { vm.home() })
        }
    }
}

@Composable
private fun MeltReview(preview: CashuWallet.MeltPreview, flow: SendLightningFlow) {
    val q = preview.quote
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis() / 1000
        }
    }
    val remaining = q.expiry?.let { it - now }
    val expired = remaining != null && remaining <= 5
    WalletPage(bottom = {
        if (expired) WalletButton(stringResource(R.string.wallet_melt_quote_refresh), onClick = flow::quote)
        else WalletButton(stringResource(R.string.wallet_melt_pay), onClick = flow::pay)
    }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AmountHero(q.amount, q.unit)
        }
        WalletCard {
            WalletValueRow(stringResource(R.string.wallet_melt_amount), formatAmount(q.amount, q.unit))
            WalletCardDivider(plain = true)
            WalletValueRow(stringResource(R.string.wallet_melt_fee_reserve), formatAmount(q.feeReserve, q.unit))
            if (preview.inputFee > 0) {
                WalletCardDivider(plain = true)
                WalletValueRow(stringResource(R.string.wallet_melt_mint_fee), formatAmount(preview.inputFee, q.unit))
            }
            WalletCardDivider(plain = true)
            WalletValueRow(stringResource(R.string.wallet_melt_max_total), formatAmount(preview.maxTotal, q.unit), emphasized = true)
        }
        if (remaining != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                if (expired) stringResource(R.string.wallet_error_quote_expired_title) else stringResource(R.string.wallet_melt_quote_expires, formatCountdown(remaining)),
                fontFamily = BitchatFontFamily, fontSize = 12.sp, textAlign = TextAlign.Center,
                color = if (expired) MaterialTheme.colorScheme.error else LocalBitchatPalette.current.textTertiary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
