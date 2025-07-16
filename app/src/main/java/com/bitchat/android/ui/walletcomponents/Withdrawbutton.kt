package com.bitchat.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitchat.android.ui.theme.BitchatTheme

/**
 * WithdrawButton component - styled button for withdraw functionality
 * Based on Figma design using existing codebase colors and typography
 */

@Composable
fun WithdrawButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 0.25.dp,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Down arrow icon (above text)
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Withdraw",
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Button text
        Text(
            text = "Withdraw",
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    }
}

// Preview functions for testing
@Preview(showBackground = true)
@Composable
fun WithdrawButtonPreview() {
    BitchatTheme {
        WithdrawButton(onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun WithdrawButtonDisabledPreview() {
    BitchatTheme {
        WithdrawButton(
            onClick = {},
            enabled = false
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun WithdrawButtonDarkPreview() {
    BitchatTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WithdrawButton(onClick = {})
            WithdrawButton(onClick = {}, enabled = false)
        }
    }
} 