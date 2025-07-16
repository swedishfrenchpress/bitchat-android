package com.bitchat.android.ui.walletcomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitchat.android.ui.theme.BitchatTheme

enum class TransactionStatus { Pending, Complete }

@Composable
fun TransactionItem(
    label: String,
    date: String,
    amount: String,
    status: TransactionStatus,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val borderColor = if (enabled) colorScheme.primary else colorScheme.primary.copy(alpha = 0.5f)
    val textAlpha = if (enabled) 1f else 0.5f
    val shape = RoundedCornerShape(4.dp)
    val statusIcon = when (status) {
        TransactionStatus.Pending -> Icons.Filled.Schedule
        TransactionStatus.Complete -> Icons.Filled.CheckCircle
    }
    val statusIconTint = when (status) {
        TransactionStatus.Pending -> colorScheme.onSurface.copy(alpha = 0.7f * textAlpha)
        TransactionStatus.Complete -> colorScheme.primary.copy(alpha = textAlpha)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color = colorScheme.surface, shape = shape)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Transaction label and date
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.primary.copy(alpha = textAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface.copy(alpha = 0.7f * textAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            // Amount and status icon
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = amount,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.primary.copy(alpha = textAlpha),
                    maxLines = 1
                )
                if (status == TransactionStatus.Pending) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = "Pending",
                        tint = colorScheme.onSurface.copy(alpha = 0.7f * textAlpha),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TransactionItemPendingPreview() {
    BitchatTheme {
        TransactionItem(
            label = "Sent to Alice",
            date = "2025-07-16 14:23",
            amount = "-0.002 ₿",
            status = TransactionStatus.Pending,
            enabled = true
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TransactionItemCompletePreview() {
    BitchatTheme {
        TransactionItem(
            label = "Received from Bob",
            date = "2025-07-15 09:10",
            amount = "+0.005 ₿",
            status = TransactionStatus.Complete,
            enabled = true
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TransactionItemPendingDarkPreview() {
    BitchatTheme {
        TransactionItem(
            label = "Sent to Alice",
            date = "2025-07-16 14:23",
            amount = "-0.002 ₿",
            status = TransactionStatus.Pending,
            enabled = true
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TransactionItemCompleteDarkPreview() {
    BitchatTheme {
        TransactionItem(
            label = "Received from Bob",
            date = "2025-07-15 09:10",
            amount = "+0.005 ₿",
            status = TransactionStatus.Complete,
            enabled = true
        )
    }
} 