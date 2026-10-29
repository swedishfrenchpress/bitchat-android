package com.bitchat.android.wallet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bitchat.android.R
import com.bitchat.android.ui.theme.LocalBitchatPalette
import com.bitchat.android.wallet.WalletService
import com.bitchat.android.wallet.engine.CashuWallet

/** The status line under a payment card in chat, and its tint. */
data class CashuCardLine(val text: String, val color: Color, val status: CashuWallet.CardStatus?)

/**
 * Local-only status for a token shown in chat. Without a wallet every token reads "Tap to
 * claim" (tapping walks through setup first). Never contacts a mint.
 */
@Composable
fun rememberCashuCardLine(token: String): CashuCardLine {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val palette = LocalBitchatPalette.current
    val hasWallet by WalletService.hasWallet.collectAsState()
    val status = if (hasWallet || WalletService.walletExists(context)) {
        val wallet = remember { WalletService.wallet(context) }
        val state by wallet.state.collectAsState()
        remember(token, state) { wallet.cardStatus(token) }
    } else null
    val (res, color) = when (status) {
        null, CashuWallet.CardStatus.CLAIMABLE -> R.string.cashu_card_claim to colorScheme.primary
        CashuWallet.CardStatus.LOCKED_TO_US -> R.string.cashu_card_locked_you to colorScheme.primary
        CashuWallet.CardStatus.LOCKED_TO_OTHER -> R.string.cashu_card_locked_other to palette.textTertiary
        CashuWallet.CardStatus.CLAIMED_BY_US -> R.string.cashu_card_claimed to palette.textTertiary
        CashuWallet.CardStatus.CLAIM_QUEUED -> R.string.cashu_card_queued to palette.accentOrange
        CashuWallet.CardStatus.SPENT -> R.string.cashu_card_spent to palette.textTertiary
        CashuWallet.CardStatus.OUR_PENDING_SEND -> R.string.cashu_card_yours_pending to palette.accentOrange
        CashuWallet.CardStatus.OUR_SEND_CLAIMED -> R.string.cashu_card_sent_claimed to palette.textTertiary
        CashuWallet.CardStatus.OUR_SEND_RECLAIMED -> R.string.cashu_card_reclaimed to palette.textTertiary
        CashuWallet.CardStatus.UNREADABLE -> R.string.cashu_card_claim to colorScheme.primary
    }
    return CashuCardLine(stringResource(res), color, status)
}

/** Claimed or dead tokens are drawn muted so live money stands out in a busy chat. */
val CashuCardLine.isSettled: Boolean
    get() = status == CashuWallet.CardStatus.CLAIMED_BY_US || status == CashuWallet.CardStatus.SPENT ||
        status == CashuWallet.CardStatus.OUR_SEND_CLAIMED || status == CashuWallet.CardStatus.OUR_SEND_RECLAIMED ||
        status == CashuWallet.CardStatus.LOCKED_TO_OTHER
