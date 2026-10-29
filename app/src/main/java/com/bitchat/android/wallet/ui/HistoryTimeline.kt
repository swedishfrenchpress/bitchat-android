package com.bitchat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R
import com.bitchat.android.ui.theme.BitchatFontFamily
import com.bitchat.android.ui.theme.LocalBitchatPalette
import com.bitchat.android.wallet.engine.HistoryEntry
import com.bitchat.android.wallet.engine.HistoryKind
import com.bitchat.android.wallet.engine.HistoryNote
import com.bitchat.android.wallet.engine.HistoryStatus

internal enum class StepState { Done, Current, Failed, Next }

internal data class TimelineStep(val label: Int, val state: StepState)

/**
 * Where an entry is in its life, as three steps. Pending entries show exactly which step is
 * outstanding, which is what someone waiting on a payment wants to know.
 */
internal fun timelineFor(entry: HistoryEntry): List<TimelineStep> {
    val done = StepState.Done
    return when (entry.kind) {
        HistoryKind.ECASH_SEND -> when (entry.status) {
            HistoryStatus.COMPLETED -> listOf(TimelineStep(R.string.wallet_step_token_created, done), TimelineStep(R.string.wallet_step_waiting_claim, done), TimelineStep(R.string.wallet_step_claimed, done))
            HistoryStatus.RECLAIMED -> listOf(TimelineStep(R.string.wallet_step_token_created, done), TimelineStep(R.string.wallet_step_waiting_claim, done), TimelineStep(R.string.wallet_step_taken_back, done))
            HistoryStatus.PENDING -> listOf(TimelineStep(R.string.wallet_step_token_created, if (entry.token != null) done else StepState.Current), TimelineStep(R.string.wallet_step_waiting_claim, if (entry.token != null) StepState.Current else StepState.Next), TimelineStep(R.string.wallet_step_claimed, StepState.Next))
            else -> listOf(TimelineStep(R.string.wallet_step_token_created, StepState.Failed))
        }
        HistoryKind.LIGHTNING_SEND -> when (entry.status) {
            HistoryStatus.COMPLETED -> listOf(TimelineStep(R.string.wallet_step_quoted, done), TimelineStep(R.string.wallet_step_paying, done), TimelineStep(R.string.wallet_step_paid, done))
            HistoryStatus.PENDING -> listOf(TimelineStep(R.string.wallet_step_quoted, done), TimelineStep(R.string.wallet_step_paying, StepState.Current), TimelineStep(R.string.wallet_step_paid, StepState.Next))
            else -> listOf(TimelineStep(R.string.wallet_step_quoted, done), TimelineStep(R.string.wallet_step_failed, StepState.Failed))
        }
        HistoryKind.LIGHTNING_RECEIVE -> when (entry.status) {
            HistoryStatus.COMPLETED -> listOf(TimelineStep(R.string.wallet_step_invoice, done), TimelineStep(R.string.wallet_step_payment, done), TimelineStep(R.string.wallet_step_minted, done))
            HistoryStatus.PENDING -> {
                val paid = entry.note != HistoryNote.WAITING_FOR_PAYMENT
                listOf(TimelineStep(R.string.wallet_step_invoice, done), TimelineStep(R.string.wallet_step_payment, if (paid) done else StepState.Current), TimelineStep(R.string.wallet_step_minted, if (paid) StepState.Current else StepState.Next))
            }
            HistoryStatus.EXPIRED -> listOf(TimelineStep(R.string.wallet_step_invoice, done), TimelineStep(R.string.wallet_step_expired, StepState.Failed))
            else -> listOf(TimelineStep(R.string.wallet_step_invoice, done), TimelineStep(R.string.wallet_step_failed, StepState.Failed))
        }
        HistoryKind.ECASH_RECEIVE -> when (entry.status) {
            HistoryStatus.COMPLETED -> listOf(TimelineStep(R.string.wallet_step_token_received, done), TimelineStep(R.string.wallet_step_swapped, done))
            HistoryStatus.PENDING -> listOf(TimelineStep(R.string.wallet_step_token_received, done), TimelineStep(R.string.wallet_step_swapped, StepState.Current))
            else -> listOf(TimelineStep(R.string.wallet_step_token_received, done), TimelineStep(R.string.wallet_step_spent, StepState.Failed))
        }
    }
}

@Composable
internal fun HistoryTimeline(entry: HistoryEntry, modifier: Modifier = Modifier) {
    if (entry.note == HistoryNote.RESTORED) return
    val steps = timelineFor(entry)
    val colorScheme = MaterialTheme.colorScheme
    val palette = LocalBitchatPalette.current
    WalletCard(modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            steps.forEachIndexed { i, step ->
                val tint: Color = when (step.state) {
                    StepState.Done -> colorScheme.primary
                    StepState.Current -> palette.accentOrange
                    StepState.Failed -> colorScheme.error
                    StepState.Next -> palette.textTertiary
                }
                val label = stringResource(step.label)
                val stateText = stringResource(
                    when (step.state) {
                        StepState.Done -> R.string.wallet_step_state_done
                        StepState.Current -> R.string.wallet_step_state_current
                        StepState.Failed -> R.string.wallet_step_state_failed
                        StepState.Next -> R.string.wallet_step_state_next
                    }
                )
                val description = stringResource(R.string.wallet_cd_step, label, stateText)
                Row(
                    Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(22.dp)) {
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .size(10.dp)
                                .background(if (step.state == StepState.Next) tint.copy(alpha = 0.35f) else tint, CircleShape),
                        )
                        if (i < steps.lastIndex) {
                            Box(Modifier.padding(vertical = 2.dp).width(1.dp).height(18.dp).background(palette.textTertiary.copy(alpha = 0.4f)))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        label,
                        fontFamily = BitchatFontFamily,
                        fontSize = 13.sp,
                        fontWeight = if (step.state == StepState.Current) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (step.state == StepState.Next) palette.textTertiary else colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
