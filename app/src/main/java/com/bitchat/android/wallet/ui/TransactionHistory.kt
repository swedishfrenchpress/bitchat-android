package com.bitchat.android.wallet.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.wallet.data.WalletTransaction
import com.bitchat.android.wallet.data.TransactionType
import com.bitchat.android.wallet.data.TransactionStatus
import com.bitchat.android.wallet.ui.TransactionItem
import com.bitchat.android.wallet.ui.TransactionStatus as UITransactionStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistory(
    transactions: List<WalletTransaction>,
    modifier: Modifier = Modifier,
    onTransactionClick: (WalletTransaction) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "TRANSACTION HISTORY",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (transactions.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Receipt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No transactions yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your transaction history will appear here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Transaction list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions) { transaction ->
                    TransactionItem(
                        label = transaction.description ?: getDefaultDescription(transaction.type),
                        date = formatDateTime(transaction.timestamp),
                        amount = formatTransactionAmount(transaction),
                        status = mapTransactionStatus(transaction.status),
                        enabled = true
                    )
                }
                
                // Bottom padding
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

// Helper functions

private fun getDefaultDescription(type: TransactionType): String {
    return when (type) {
        TransactionType.CASHU_SEND -> "Sent eCash"
        TransactionType.CASHU_RECEIVE -> "Received eCash"
        TransactionType.LIGHTNING_SEND -> "Lightning Payment"
        TransactionType.LIGHTNING_RECEIVE -> "Lightning Received"
        TransactionType.MINT -> "Ecash Minted"
        TransactionType.MELT -> "Ecash Melted"
    }
}

private fun formatDateTime(date: Date): String {
    val now = Date()
    val diffMillis = now.time - date.time
    val diffMinutes = diffMillis / (1000 * 60)
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24
    
    return when {
        diffMinutes < 1 -> "Just now"
        diffMinutes < 60 -> "${diffMinutes}m ago"
        diffHours < 24 -> "${diffHours}h ago"
        diffDays < 7 -> "${diffDays}d ago"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
    }
}

private fun formatTransactionAmount(transaction: WalletTransaction): String {
    val isIncoming = transaction.type in listOf(
        TransactionType.CASHU_RECEIVE,
        TransactionType.LIGHTNING_RECEIVE,
        TransactionType.MINT
    )
    val sign = if (isIncoming) "+" else "-"
    val amount = when {
        transaction.amount.toLong() >= 1000 -> String.format("₿%,d", transaction.amount.toLong())
        else -> "₿${transaction.amount.toLong()}"
    }
    return "$sign$amount"
}

private fun mapTransactionStatus(status: TransactionStatus): UITransactionStatus {
    return when (status) {
        TransactionStatus.PENDING -> UITransactionStatus.Pending
        TransactionStatus.CONFIRMED -> UITransactionStatus.Complete
        TransactionStatus.FAILED -> UITransactionStatus.Complete // Treating failed as complete for UI
        TransactionStatus.EXPIRED -> UITransactionStatus.Pending
    }
}
