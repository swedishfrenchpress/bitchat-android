package com.bitchat.android.wallet.ui

import com.bitchat.android.wallet.engine.HistoryEntry
import com.bitchat.android.wallet.engine.HistoryKind
import com.bitchat.android.wallet.engine.HistoryStatus
import com.bitchat.android.wallet.engine.WalletState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class WalletFormatTest {
    @Test
    fun `amounts format like the rest of Bitchat`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            assertEquals("21 sat", formatAmount(21, "sat"))
            assertEquals("12,345 sat", formatAmount(12_345, "sat"))
            assertEquals("1.25 usd", formatAmount(125, "usd"))
            assertEquals("901 jpy", formatAmount(901, "jpy"))
            assertEquals("0 sat", formatAmount(0, "sat"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `countdowns stay short`() {
        assertEquals("45s", formatCountdown(45))
        assertEquals("4m 12s", formatCountdown(252))
        assertEquals("1h 5m", formatCountdown(3_900))
        assertEquals("0s", formatCountdown(-3))
    }

    @Test
    fun `unclaimed matches what the pending send rows show`() {
        fun send(id: String, amount: Long, status: HistoryStatus, mint: String = "https://a.example") = HistoryEntry(
            id = id, kind = HistoryKind.ECASH_SEND, status = status, amount = amount, fee = 1,
            unit = "sat", mintUrl = mint, createdAt = 0, updatedAt = 0,
        )
        val state = WalletState(
            history = listOf(
                send("a", 21, HistoryStatus.PENDING),
                send("b", 5, HistoryStatus.PENDING, mint = "https://b.example"),
                send("c", 99, HistoryStatus.COMPLETED),
                send("d", 7, HistoryStatus.RECLAIMED),
            ),
        )
        assertEquals(mapOf("sat" to 26L), state.unclaimed())
        assertEquals(mapOf("sat" to 21L), state.unclaimed("https://a.example"))
        assertEquals(emptyMap<String, Long>(), WalletState().unclaimed())
    }
}
