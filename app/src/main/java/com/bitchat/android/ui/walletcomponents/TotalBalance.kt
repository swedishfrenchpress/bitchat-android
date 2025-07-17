package com.bitchat.android.ui.walletcomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitchat.android.ui.theme.BitchatTheme

/**
 * TotalBalance component - displays Bitcoin and dollar amounts in a dark box
 * Based on Figma design using existing codebase colors and typography
 */

@Composable
fun TotalBalance(
    bitcoinAmount: String = "0​₿",
    dollarAmount: String = "$0",
    modifier: Modifier = Modifier
) {
    // Using theme surface color with consistent border radius and green border
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 0.25.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Bitcoin amount - using theme typography and colors
            Text(
                text = bitcoinAmount,
                color = MaterialTheme.colorScheme.primary, // Using theme primary color (green)
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.8f // Larger for prominence
                )
            )
            
            // Dollar amount - using theme typography and colors
            Text(
                text = dollarAmount,
                color = MaterialTheme.colorScheme.secondary, // Using theme secondary color
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

// Preview functions for testing
@Preview(showBackground = true)
@Composable
fun TotalBalancePreview() {
    BitchatTheme {
        TotalBalance()
    }
}

@Preview(showBackground = true)
@Composable
fun TotalBalanceWithDataPreview() {
    BitchatTheme {
        TotalBalance(
            bitcoinAmount = "0.00234​₿",
            dollarAmount = "$156.78"
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TotalBalanceDarkPreview() {
    BitchatTheme {
        TotalBalance(
            bitcoinAmount = "1.5​₿",
            dollarAmount = "$67,890"
        )
    }
} 