package com.bitchat.android.wallet.ui

import android.content.Context
import android.text.format.DateUtils
import com.bitchat.android.R
import com.bitchat.android.wallet.engine.HistoryEntry
import com.bitchat.android.wallet.engine.HistoryKind
import com.bitchat.android.wallet.engine.HistoryNote
import com.bitchat.android.wallet.engine.HistoryStatus
import com.bitchat.android.wallet.engine.WalletError
import com.bitchat.android.wallet.engine.WalletException

/** Calm, specific copy for an error: what happened, whether funds are safe, what next. */
data class ErrorCopy(
    val title: String,
    val body: String,
    /** Whether "Try again" can help, or only "Close". */
    val retryable: Boolean,
    /** Outcome not known yet; shown as pending rather than failed. */
    val pending: Boolean = false,
)

/** The error as one status line ("can't reach the mint · nothing was spent · …"). */
internal fun ErrorCopy.line(): String = title.replaceFirstChar { it.lowercase() } + " · " + body

internal fun Throwable.walletError(): WalletError = (this as? WalletException)?.error ?: WalletError.Internal

internal fun errorCopy(context: Context, error: WalletError, unit: String = "sat"): ErrorCopy {
    fun s(id: Int, vararg args: Any) = context.getString(id, *args)
    return when (error) {
        WalletError.MintUnreachable -> ErrorCopy(s(R.string.wallet_error_unreachable_title), s(R.string.wallet_error_unreachable_body), true)
        WalletError.OutcomeUnknown -> ErrorCopy(s(R.string.wallet_error_unknown_title), s(R.string.wallet_error_unknown_body), false, pending = true)
        is WalletError.MintRejected -> ErrorCopy(s(R.string.wallet_error_rejected_title), s(R.string.wallet_error_rejected_body), true)
        is WalletError.InsufficientBalance -> ErrorCopy(
            s(R.string.wallet_error_insufficient_title),
            s(R.string.wallet_error_insufficient_body, formatAmount(error.needed, unit), formatAmount(error.available, unit)),
            false,
        )
        is WalletError.AmountOutOfRange -> ErrorCopy(s(R.string.wallet_error_amount_title), s(R.string.wallet_error_amount_body), false)
        WalletError.InvalidAmount -> ErrorCopy(s(R.string.wallet_error_amount_title), s(R.string.wallet_error_amount_body), false)
        is WalletError.FeesExceedAmount -> ErrorCopy(s(R.string.wallet_error_fees_title), s(R.string.wallet_error_fees_body, formatAmount(error.fee, unit)), false)
        WalletError.TokenMalformed -> ErrorCopy(s(R.string.wallet_error_token_malformed_title), s(R.string.wallet_error_token_malformed_body), false)
        WalletError.TokenMultipleMints -> ErrorCopy(s(R.string.wallet_error_token_multimint_title), s(R.string.wallet_error_token_multimint_body), false)
        WalletError.TokenAlreadySpent -> ErrorCopy(s(R.string.wallet_error_spent_title), s(R.string.wallet_error_spent_body), false)
        WalletError.TokenAlreadyClaimed -> ErrorCopy(s(R.string.wallet_error_claimed_title), s(R.string.wallet_error_claimed_body), false)
        WalletError.TokenLockedToSomeoneElse -> ErrorCopy(s(R.string.wallet_error_locked_title), s(R.string.wallet_error_locked_body), false)
        is WalletError.TokenLockNotExpired -> ErrorCopy(
            s(R.string.wallet_error_lock_wait_title),
            s(R.string.wallet_error_lock_wait_body, formatDateTime(context, error.unlocksAt * 1000)),
            false,
        )
        WalletError.TokenLockUnsupported -> ErrorCopy(s(R.string.wallet_error_lock_unsupported_title), s(R.string.wallet_error_lock_unsupported_body), false)
        WalletError.TokenSignatureInvalid -> ErrorCopy(s(R.string.wallet_error_signature_title), s(R.string.wallet_error_signature_body), false)
        WalletError.TokenPending -> ErrorCopy(s(R.string.wallet_error_unknown_title), s(R.string.wallet_error_unknown_body), false, pending = true)
        WalletError.UnitUnsupported -> ErrorCopy(s(R.string.wallet_error_unit_title), s(R.string.wallet_error_unit_body), false)
        WalletError.UnknownMint -> ErrorCopy(s(R.string.wallet_unknown_mint_title), s(R.string.wallet_error_mint_not_found_body), false)
        WalletError.MintKeysInvalid -> ErrorCopy(s(R.string.wallet_error_mint_keys_title), s(R.string.wallet_error_mint_keys_body), false)
        WalletError.MintNotFound -> ErrorCopy(s(R.string.wallet_error_mint_not_found_title), s(R.string.wallet_error_mint_not_found_body), true)
        WalletError.MintHasFunds -> ErrorCopy(s(R.string.wallet_error_mint_funds_title), s(R.string.wallet_error_mint_funds_body), false)
        WalletError.MintFeatureMissing -> ErrorCopy(s(R.string.wallet_error_feature_title), s(R.string.wallet_error_feature_body), false)
        WalletError.MintingDisabled -> ErrorCopy(s(R.string.wallet_error_minting_disabled_title), s(R.string.wallet_error_minting_disabled_body), true)
        WalletError.InvoiceInvalid -> ErrorCopy(s(R.string.wallet_error_invoice_title), s(R.string.wallet_error_invoice_body), false)
        WalletError.InvoiceAmountless -> ErrorCopy(s(R.string.wallet_error_invoice_title), s(R.string.wallet_error_invoice_amountless_body), false)
        WalletError.InvoiceAlreadyPaid -> ErrorCopy(s(R.string.wallet_error_invoice_paid_title), s(R.string.wallet_error_invoice_paid_body), false)
        WalletError.QuoteExpired -> ErrorCopy(s(R.string.wallet_error_quote_expired_title), s(R.string.wallet_error_quote_expired_body), true)
        WalletError.PaymentFailed -> ErrorCopy(s(R.string.wallet_error_payment_failed_title), s(R.string.wallet_error_payment_failed_body), true)
        WalletError.PaymentPending -> ErrorCopy(s(R.string.wallet_payment_pending_title), s(R.string.wallet_payment_pending_body), false, pending = true)
        WalletError.OperationInProgress -> ErrorCopy(s(R.string.wallet_error_busy_title), s(R.string.wallet_error_busy_body), false, pending = true)
        WalletError.WalletNotReady -> ErrorCopy(s(R.string.wallet_error_not_ready_title), s(R.string.wallet_error_not_ready_body), false)
        WalletError.NothingToReclaim -> ErrorCopy(s(R.string.wallet_error_nothing_title), s(R.string.wallet_error_nothing_body), false)
        WalletError.CountersResynced -> ErrorCopy(s(R.string.wallet_error_resync_title), s(R.string.wallet_error_resync_body), true)
        WalletError.Internal -> ErrorCopy(s(R.string.wallet_error_internal_title), s(R.string.wallet_error_internal_body), true)
    }
}

internal fun historyTitle(context: Context, entry: HistoryEntry): String = context.getString(
    when {
        entry.note == HistoryNote.RESTORED -> R.string.wallet_kind_restore
        else -> when (entry.kind) {
            HistoryKind.LIGHTNING_RECEIVE -> R.string.wallet_kind_lightning_receive
            HistoryKind.ECASH_RECEIVE -> R.string.wallet_kind_ecash_receive
            HistoryKind.ECASH_SEND -> R.string.wallet_kind_ecash_send
            HistoryKind.LIGHTNING_SEND -> R.string.wallet_kind_lightning_send
        }
    }
)

internal fun historyStatus(context: Context, entry: HistoryEntry): String {
    val note = when (entry.note) {
        HistoryNote.WAITING_FOR_PAYMENT -> R.string.wallet_note_waiting_payment
        HistoryNote.WAITING_FOR_MINT -> R.string.wallet_note_waiting_mint
        HistoryNote.WAITING_FOR_CLAIM -> R.string.wallet_note_waiting_claim
        HistoryNote.CLAIMED -> R.string.wallet_note_claimed
        HistoryNote.ALREADY_SPENT -> R.string.wallet_note_spent
        HistoryNote.PAYMENT_FAILED -> R.string.wallet_note_payment_failed
        HistoryNote.PAYMENT_PENDING -> R.string.wallet_note_payment_pending
        HistoryNote.QUOTE_EXPIRED -> R.string.wallet_note_quote_expired
        HistoryNote.MINT_REJECTED -> R.string.wallet_note_rejected
        HistoryNote.INVOICE_ALREADY_PAID -> R.string.wallet_note_invoice_paid
        HistoryNote.PARTIAL -> R.string.wallet_note_partial
        else -> null
    }
    val status = when (entry.status) {
        HistoryStatus.PENDING -> R.string.wallet_status_pending
        HistoryStatus.COMPLETED -> R.string.wallet_status_completed
        HistoryStatus.FAILED -> R.string.wallet_status_failed
        HistoryStatus.EXPIRED -> R.string.wallet_status_expired
        HistoryStatus.RECLAIMED -> R.string.wallet_status_reclaimed
    }
    return context.getString(note ?: status)
}

/** Whether the entry moved money into (true) or out of (false) the wallet, for the sign. */
internal fun HistoryEntry.isIncoming(): Boolean =
    kind == HistoryKind.LIGHTNING_RECEIVE || kind == HistoryKind.ECASH_RECEIVE

internal fun formatDateTime(context: Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)

internal fun formatRelative(millis: Long, now: Long = System.currentTimeMillis()): String =
    DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

/**
 * Runs [block] but takes at least [millis], so a fast mint does not flash a spinner and a
 * result in the same instant (the loading state stays readable).
 */
internal suspend fun <T> atLeast(millis: Long = 500, block: suspend () -> T): T {
    val started = System.currentTimeMillis()
    try {
        return block()
    } finally {
        val elapsed = System.currentTimeMillis() - started
        if (elapsed < millis) kotlinx.coroutines.delay(millis - elapsed)
    }
}

/** "4m 12s" style countdown for invoice and quote expiry. */
internal fun formatCountdown(seconds: Long): String = when {
    seconds <= 0 -> "0s"
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}
