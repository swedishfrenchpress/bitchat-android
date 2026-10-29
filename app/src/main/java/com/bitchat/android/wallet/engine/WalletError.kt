package com.bitchat.android.wallet.engine

/**
 * Everything that can go wrong in a wallet operation, phrased by outcome rather than by
 * cause so the UI can tell the person whether their funds are safe. Raw exceptions and
 * mint-supplied text never reach the screen.
 */
sealed class WalletError {
    /** The mint could not be reached. Nothing happened, or it will finish automatically. */
    data object MintUnreachable : WalletError()

    /** We asked; the mint's answer was lost. Funds are being verified automatically. */
    data object OutcomeUnknown : WalletError()

    data class MintRejected(val code: Int?) : WalletError()
    data class InsufficientBalance(val available: Long, val needed: Long) : WalletError()
    data class AmountOutOfRange(val min: Long?, val max: Long?) : WalletError()
    data object InvalidAmount : WalletError()
    data class FeesExceedAmount(val fee: Long) : WalletError()

    data object TokenMalformed : WalletError()
    data object TokenMultipleMints : WalletError()
    data object TokenAlreadySpent : WalletError()
    data object TokenAlreadyClaimed : WalletError()
    data object TokenLockedToSomeoneElse : WalletError()
    data class TokenLockNotExpired(val unlocksAt: Long) : WalletError()
    data object TokenLockUnsupported : WalletError()
    data object TokenSignatureInvalid : WalletError()
    data object TokenPending : WalletError()

    data object UnitUnsupported : WalletError()
    data object UnknownMint : WalletError()
    /** The mint's keys do not match its keyset IDs, or reuse another mint's IDs. */
    data object MintKeysInvalid : WalletError()
    data object MintNotFound : WalletError()
    data object MintHasFunds : WalletError()
    data object MintFeatureMissing : WalletError()
    data object MintingDisabled : WalletError()

    data object InvoiceInvalid : WalletError()
    data object InvoiceAmountless : WalletError()
    data object InvoiceAlreadyPaid : WalletError()
    data object QuoteExpired : WalletError()
    /** Lightning failed; inputs were verified unspent and returned. */
    data object PaymentFailed : WalletError()
    data object PaymentPending : WalletError()

    data object OperationInProgress : WalletError()
    data object WalletNotReady : WalletError()
    data object NothingToReclaim : WalletError()
    /** Counters collided with old outputs and were moved forward; nothing was spent. Retry. */
    data object CountersResynced : WalletError()
    data object Internal : WalletError()
}

class WalletException(val error: WalletError, cause: Throwable? = null) :
    Exception(error.toString(), cause)
