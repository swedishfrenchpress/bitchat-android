package com.bitchat.android.wallet.ui

import com.bitchat.android.R
import com.bitchat.android.wallet.engine.HistoryEntry
import com.bitchat.android.wallet.engine.HistoryKind
import com.bitchat.android.wallet.engine.HistoryNote
import com.bitchat.android.wallet.engine.HistoryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryTimelineTest {
    private fun entry(kind: HistoryKind, status: HistoryStatus, note: HistoryNote = HistoryNote.NONE, token: String? = null) =
        HistoryEntry("h", kind, status, 1, 0, "sat", "https://m", 0, 0, note = note, token = token)

    @Test
    fun `pending send highlights the claim wait`() {
        val steps = timelineFor(entry(HistoryKind.ECASH_SEND, HistoryStatus.PENDING, token = "cashuB…"))
        assertEquals(listOf(StepState.Done, StepState.Current, StepState.Next), steps.map { it.state })
    }

    @Test
    fun `reclaimed send ends in taken back`() {
        val steps = timelineFor(entry(HistoryKind.ECASH_SEND, HistoryStatus.RECLAIMED))
        assertEquals(R.string.wallet_step_taken_back, steps.last().label)
    }

    @Test
    fun `lightning receive separates payment from issuance`() {
        val waiting = timelineFor(entry(HistoryKind.LIGHTNING_RECEIVE, HistoryStatus.PENDING, HistoryNote.WAITING_FOR_PAYMENT))
        assertEquals(StepState.Current, waiting[1].state)
        val minting = timelineFor(entry(HistoryKind.LIGHTNING_RECEIVE, HistoryStatus.PENDING, HistoryNote.WAITING_FOR_MINT))
        assertEquals(listOf(StepState.Done, StepState.Done, StepState.Current), minting.map { it.state })
        assertEquals(StepState.Failed, timelineFor(entry(HistoryKind.LIGHTNING_RECEIVE, HistoryStatus.EXPIRED)).last().state)
    }

    @Test
    fun `failed payment and spent claim end in a failed step`() {
        assertEquals(StepState.Failed, timelineFor(entry(HistoryKind.LIGHTNING_SEND, HistoryStatus.FAILED)).last().state)
        assertEquals(StepState.Failed, timelineFor(entry(HistoryKind.ECASH_RECEIVE, HistoryStatus.FAILED)).last().state)
    }
}
