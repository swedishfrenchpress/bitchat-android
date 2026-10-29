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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.R
import com.bitchat.android.ui.CashuTokenDecoder
import com.bitchat.android.ui.theme.BitchatFontFamily
import com.bitchat.android.ui.theme.BitchatMotion
import com.bitchat.android.ui.theme.LocalBitchatPalette
import com.bitchat.android.wallet.WalletService
import com.bitchat.android.wallet.cashu.MintUrl
import com.bitchat.android.wallet.engine.CashuWallet
import com.bitchat.android.wallet.engine.HistoryEntry
import com.bitchat.android.wallet.engine.HistoryStatus
import com.bitchat.android.wallet.engine.WalletError
import com.bitchat.android.wallet.engine.WalletException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val fadeSpec = fadeIn(tween(BitchatMotion.EMPHASIZED_MS)) togetherWith fadeOut(tween(BitchatMotion.STANDARD_MS))

// MARK: - Lightning receive

class ReceiveLightningFlow(
    private val app: Application,
    private val wallet: CashuWallet,
    private val scope: CoroutineScope,
    initialMint: String?,
) {
    sealed interface Step {
        data object Amount : Step
        data class Invoice(val entryId: String) : Step
        data class Done(val entry: HistoryEntry) : Step
        data object Expired : Step
        data class Failed(val copy: ErrorCopy) : Step
    }

    private val _step = MutableStateFlow<Step>(Step.Amount)
    val step: StateFlow<Step> = _step.asStateFlow()
    val amount = MutableStateFlow(AmountDigits())
    val mint = MutableStateFlow(initialMint)
    val busy = MutableStateFlow(false)
    private val historyId = CashuWallet.newId()
    private var poll: Job? = null

    fun submit() {
        val url = mint.value ?: return
        if (busy.value) return
        busy.value = true
        scope.launch {
            try {
                val entry = wallet.createMintQuote(url, amount.value.value, historyId = historyId)
                wallet.putSetting(WalletService.SETTING_LAST_MINT, url)
                _step.value = Step.Invoice(entry.id)
                startPolling(entry)
            } catch (e: WalletException) {
                _step.value = Step.Failed(errorCopy(app, e.error))
            } finally {
                busy.value = false
            }
        }
    }

    /**
     * Polls until paid or expired. Lives in the view model's scope, so it keeps going after
     * the sheet closes, bounded by the invoice expiry.
     */
    private fun startPolling(entry: HistoryEntry) {
        poll?.cancel()
        poll = scope.launch {
            val deadline = (entry.quoteExpiry ?: (System.currentTimeMillis() / 1000 + 600)) + 60
            var interval = 2_000L
            while (System.currentTimeMillis() / 1000 < deadline) {
                delay(interval)
                interval = (interval * 5 / 4).coerceAtMost(8_000)
                val latest = runCatching { wallet.checkMintQuote(entry.id) }.getOrNull() ?: continue
                when (latest.status) {
                    HistoryStatus.COMPLETED -> { _step.value = Step.Done(latest); return@launch }
                    HistoryStatus.EXPIRED -> { _step.value = Step.Expired; return@launch }
                    HistoryStatus.FAILED -> { _step.value = Step.Failed(errorCopy(app, WalletError.MintRejected(null))); return@launch }
                    else -> Unit
                }
            }
            _step.value = Step.Expired
        }
    }

    fun restart() {
        poll?.cancel()
        amount.value = AmountDigits()
        _step.value = Step.Amount
    }
}

@Composable
internal fun ReceiveLightningScreen(vm: WalletViewModel, flow: ReceiveLightningFlow, onDismiss: () -> Unit) {
    AdoptDefaultMint(vm, flow.mint)
    val step by flow.step.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AnimatedContent(step, contentKey = { it::class }, transitionSpec = { fadeSpec }, label = "lnReceive") { s ->
        when (s) {
            ReceiveLightningFlow.Step.Amount -> {
                val amount by flow.amount.collectAsStateWithLifecycle()
                val mintUrl by flow.mint.collectAsStateWithLifecycle()
                val busy by flow.busy.collectAsStateWithLifecycle()
                val setting = state.mint(mintUrl ?: "")?.mint?.info?.mintSetting("bolt11", "sat")
                val problem = when {
                    amount.value == 0L -> null
                    setting?.minAmount != null && amount.value < setting.minAmount -> stringResource(R.string.wallet_amount_below_min, formatAmount(setting.minAmount, "sat"))
                    setting?.maxAmount != null && amount.value > setting.maxAmount -> stringResource(R.string.wallet_amount_above_max, formatAmount(setting.maxAmount, "sat"))
                    else -> null
                }
                AmountStep(
                    amount = amount, unit = "sat",
                    contextLine = problem,
                    contextIsError = problem != null,
                    mints = state.mints, selectedMint = mintUrl, onMint = { flow.mint.value = it },
                    onChange = { flow.amount.value = it },
                    continueLabel = stringResource(R.string.wallet_continue),
                    onContinue = flow::submit,
                    continueEnabled = amount.value > 0 && problem == null && mintUrl != null,
                    loading = busy,
                )
            }
            is ReceiveLightningFlow.Step.Invoice -> {
                val entry = state.history.firstOrNull { it.id == s.entryId } ?: return@AnimatedContent
                InvoiceStep(entry)
            }
            is ReceiveLightningFlow.Step.Done -> WalletPage(centered = true, terminal = true, bottom = { WalletButton(stringResource(R.string.wallet_done), onClick = { vm.home() }) }) {
                WalletStatusPanel(StatusKind.Success, stringResource(R.string.wallet_received_title), stringResource(R.string.wallet_received_body, formatAmount(s.entry.amount, s.entry.unit)))
            }
            ReceiveLightningFlow.Step.Expired -> WalletPage(centered = true, terminal = true, bottom = {
                WalletButton(stringResource(R.string.wallet_invoice_new), onClick = flow::restart)
                WalletButton(stringResource(R.string.wallet_done), onClick = { vm.home() }, tone = WalletButtonTone.Neutral)
            }) {
                WalletStatusPanel(StatusKind.Pending, stringResource(R.string.wallet_invoice_expired_title), stringResource(R.string.wallet_invoice_expired_body))
            }
            is ReceiveLightningFlow.Step.Failed -> ErrorStep(s.copy, onRetry = flow::restart, onClose = { vm.home() })
        }
    }
}

@Composable
private fun InvoiceStep(entry: HistoryEntry) {
    val context = LocalContext.current
    val invoice = entry.invoice ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis() / 1000
        }
    }
    val remaining = entry.quoteExpiry?.let { it - now }
    WalletPage(bottom = {
        WalletButton(stringResource(R.string.wallet_invoice_copy), onClick = { copyToClipboard(context, "invoice", invoice) }, tone = WalletButtonTone.Neutral)
    }) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AmountHero(entry.amount, entry.unit)
            Spacer(Modifier.height(16.dp))
            WalletQrCode("lightning:${invoice.uppercase()}", stringResource(R.string.wallet_cd_invoice_qr))
            Spacer(Modifier.height(16.dp))
            val waiting = stringResource(R.string.wallet_invoice_waiting)
            Text(
                if (remaining != null) waiting + " · " + stringResource(R.string.wallet_invoice_expires, formatCountdown(remaining)) else waiting,
                fontFamily = BitchatFontFamily, fontSize = 12.sp, color = LocalBitchatPalette.current.textTertiary,
            )
        }
    }
}

@Composable
internal fun ErrorStep(copy: ErrorCopy, onRetry: (() -> Unit)?, onClose: () -> Unit) {
    WalletPage(centered = true, terminal = true, bottom = {
        if (copy.retryable && onRetry != null) WalletButton(stringResource(R.string.wallet_retry), onClick = onRetry)
        WalletButton(stringResource(R.string.wallet_done), onClick = onClose, tone = WalletButtonTone.Neutral)
    }) {
        WalletStatusPanel(if (copy.pending) StatusKind.Pending else StatusKind.Error, copy.title, copy.body)
    }
}

// MARK: - Ecash claim

class ReceiveEcashFlow(
    private val app: Application,
    private val wallet: CashuWallet,
    private val scope: CoroutineScope,
    initialToken: String?,
    private val viaChat: Boolean,
) {
    sealed interface Step {
        data class Input(val text: String, val invalid: Boolean, val scanning: Boolean) : Step
        data class Review(val inspection: CashuWallet.TokenInspection, val claiming: Boolean) : Step
        data class Claimed(val entry: HistoryEntry) : Step
        data object Queued : Step
        data class Failed(val copy: ErrorCopy, val retry: Boolean) : Step
    }

    private val _step = MutableStateFlow<Step>(Step.Input("", invalid = false, scanning = false))
    val step: StateFlow<Step> = _step.asStateFlow()
    private val historyId = CashuWallet.newId()
    private var raw: String? = null

    init {
        initialToken?.let(::submit)
    }

    fun update(text: String) {
        _step.value = Step.Input(text, invalid = false, scanning = false)
    }

    fun stepBack(): Boolean = when (val s = _step.value) {
        is Step.Review -> if (s.claiming || viaChat) s.claiming else { update(raw.orEmpty()); true }
        is Step.Input -> if (s.scanning) { scan(false); true } else false
        else -> false
    }

    fun scan(on: Boolean) {
        (_step.value as? Step.Input)?.let { _step.value = it.copy(scanning = on) }
    }

    fun submit(text: String = (_step.value as? Step.Input)?.text.orEmpty()) {
        val bare = CashuTokenDecoder.bareToken(text)
        if (bare == null) {
            _step.value = Step.Input(text, invalid = true, scanning = false)
            return
        }
        raw = bare
        _step.value = try {
            Step.Review(wallet.inspect(bare), claiming = false)
        } catch (e: WalletException) {
            Step.Failed(errorCopy(app, e.error), retry = false)
        }
    }

    fun claim() {
        val review = _step.value as? Step.Review ?: return
        if (review.claiming) return
        val token = raw ?: return
        _step.value = review.copy(claiming = true)
        scope.launch {
            try {
                // Adding an unknown mint was confirmed on the review screen.
                val entry = atLeast {
                    if (!review.inspection.mintKnown) wallet.addMint(review.inspection.token.mint)
                    wallet.receive(token, historyId = historyId, viaChat = viaChat)
                }
                _step.value = Step.Claimed(entry)
            } catch (e: WalletException) {
                _step.value = when (e.error) {
                    WalletError.MintUnreachable, WalletError.OutcomeUnknown ->
                        if (review.inspection.mintKnown) Step.Queued else Step.Failed(errorCopy(app, e.error, review.inspection.token.unit), retry = true)
                    else -> Step.Failed(errorCopy(app, e.error, review.inspection.token.unit), retry = errorCopy(app, e.error).retryable)
                }
            }
        }
    }

    fun retry() {
        raw?.let(::submit) ?: update("")
    }

    /** A claim started from a chat card closes the sheet when done; from the wallet, go home. */
    fun finish(vm: WalletViewModel, close: () -> Unit) = if (viaChat) close() else vm.home()
}

@Composable
internal fun ReceiveEcashScreen(vm: WalletViewModel, flow: ReceiveEcashFlow, onDismiss: () -> Unit) {
    val step by flow.step.collectAsStateWithLifecycle()
    AnimatedContent(step, contentKey = { it::class }, transitionSpec = { fadeSpec }, label = "ecashReceive") { s ->
        when (s) {
            is ReceiveEcashFlow.Step.Input -> WalletPage(bottom = {
                WalletButton(stringResource(R.string.wallet_continue), onClick = { flow.submit() }, enabled = s.text.isNotBlank())
            }) {
                Spacer(Modifier.height(12.dp))
                if (s.scanning) {
                    WalletScanner(onResult = { flow.submit(it) })
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        WalletTextAction(stringResource(R.string.wallet_cancel), onClick = { flow.scan(false) })
                    }
                } else {
                    PasteOrScanField(
                        value = s.text, onValueChange = flow::update,
                        placeholder = stringResource(R.string.wallet_token_input_hint),
                        isError = s.invalid, onScan = { flow.scan(true) },
                    )
                    if (s.invalid) WalletNotice(stringResource(R.string.wallet_token_input_invalid), tone = NoticeTone.Danger)
                }
            }
            is ReceiveEcashFlow.Step.Review -> ClaimReview(s, flow)
            is ReceiveEcashFlow.Step.Claimed -> WalletPage(centered = true, terminal = true, bottom = { WalletButton(stringResource(R.string.wallet_done), onClick = { flow.finish(vm, onDismiss) }) }) {
                val reclaimed = s.entry.status == HistoryStatus.RECLAIMED
                WalletStatusPanel(
                    StatusKind.Success,
                    stringResource(if (reclaimed) R.string.wallet_taken_back_title else R.string.wallet_claimed_title),
                    stringResource(if (reclaimed) R.string.wallet_taken_back_body else R.string.wallet_claimed_body, formatAmount(s.entry.amount, s.entry.unit)),
                )
            }
            ReceiveEcashFlow.Step.Queued -> WalletPage(centered = true, terminal = true, bottom = { WalletButton(stringResource(R.string.wallet_done), onClick = { flow.finish(vm, onDismiss) }) }) {
                WalletStatusPanel(StatusKind.Pending, stringResource(R.string.wallet_claim_queued_title), stringResource(R.string.wallet_claim_queued_body))
            }
            is ReceiveEcashFlow.Step.Failed -> ErrorStep(s.copy, onRetry = if (s.retry) flow::retry else null, onClose = { flow.finish(vm, onDismiss) })
        }
    }
}

@Composable
private fun ClaimReview(step: ReceiveEcashFlow.Step.Review, flow: ReceiveEcashFlow) {
    val context = LocalContext.current
    val i = step.inspection
    val token = i.token
    val colorScheme = MaterialTheme.colorScheme
    val host = MintUrl.host(token.mint)
    val ownSend = i.isOurPendingSend
    val canClaim = !i.alreadyClaimed && i.dleq != CashuWallet.DleqStatus.INVALID &&
        i.lock !is CashuWallet.LockStatus.ToSomeoneElse && i.lock !is CashuWallet.LockStatus.Unsupported &&
        i.lock !is CashuWallet.LockStatus.RefundableByUs
    WalletPage(bottom = {
        if (canClaim) {
            WalletButton(
                stringResource(
                    when {
                        ownSend -> R.string.wallet_review_take_back
                        !i.mintKnown -> R.string.wallet_unknown_mint_continue
                        else -> R.string.wallet_review_claim
                    }
                ),
                onClick = flow::claim,
                loading = step.claiming,
            )
        }
    }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AmountHero(i.receiveAmount ?: token.amount, token.unit)
            Spacer(Modifier.height(4.dp))
            WalletNotice(
                listOfNotNull(host, i.fee?.takeIf { it > 0 }?.let { stringResource(R.string.wallet_fee_amount, formatAmount(it, token.unit)) }).joinToString(" · "),
                centered = true,
            )
            token.memo?.let { WalletNotice(it, centered = true) }
        }
        Spacer(Modifier.height(8.dp))
        TrustNotes(i)
        if (!i.mintKnown && canClaim) {
            WalletNotice(stringResource(R.string.wallet_unknown_mint_body), tone = NoticeTone.Warning, icon = Icons.Filled.WarningAmber, centered = true)
        }
    }
}

/**
 * The honest part of a claim, in one line: who can spend this right now. Never says
 * "received" for a token that has not been swapped yet. A signature that fails the offline
 * check is the only verification result worth a line; a passing one is the quiet default.
 */
@Composable
internal fun TrustNotes(i: CashuWallet.TokenInspection) {
    val context = LocalContext.current
    if (i.dleq == CashuWallet.DleqStatus.INVALID) {
        WalletNotice(stringResource(R.string.wallet_error_signature_body), tone = NoticeTone.Danger, icon = Icons.Filled.WarningAmber, centered = true)
        return
    }
    when {
        i.alreadyClaimed -> WalletNotice(stringResource(R.string.wallet_trust_already_claimed), tone = NoticeTone.Positive, centered = true)
        i.isOurPendingSend -> WalletNotice(stringResource(R.string.wallet_trust_own_send), centered = true)
        else -> when (val lock = i.lock) {
            CashuWallet.LockStatus.None -> WalletNotice(stringResource(R.string.wallet_trust_bearer), tone = NoticeTone.Warning, icon = Icons.Filled.LockOpen, centered = true)
            is CashuWallet.LockStatus.ToUs -> WalletNotice(
                if (lock.locktime != null) stringResource(R.string.wallet_trust_locked_you_until, formatDateTime(context, lock.locktime * 1000))
                else stringResource(R.string.wallet_trust_locked_you),
                tone = NoticeTone.Positive, icon = Icons.Filled.Lock, centered = true,
            )
            is CashuWallet.LockStatus.RefundableByUs -> WalletNotice(stringResource(R.string.wallet_trust_refundable, formatDateTime(context, lock.locktime * 1000)), icon = Icons.Filled.Lock, centered = true)
            is CashuWallet.LockStatus.ToSomeoneElse -> WalletNotice(stringResource(R.string.wallet_trust_locked_other), tone = NoticeTone.Danger, icon = Icons.Filled.Lock, centered = true)
            CashuWallet.LockStatus.Unsupported -> WalletNotice(stringResource(R.string.wallet_trust_unsupported), tone = NoticeTone.Danger, centered = true)
        }
    }
}

