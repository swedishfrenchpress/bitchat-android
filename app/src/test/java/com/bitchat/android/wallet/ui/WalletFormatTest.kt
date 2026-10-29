package com.bitchat.android.wallet.ui

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
}
