package com.bitchat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bitchat.android.wallet.data.WalletTransaction
import com.bitchat.android.wallet.data.TransactionType
import com.bitchat.android.wallet.data.TransactionStatus
import com.bitchat.android.wallet.viewmodel.WalletViewModel
import com.bitchat.android.wallet.ui.TransactionItem
import com.bitchat.android.wallet.ui.TransactionStatus as UITransactionStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * Transaction history screen showing all wallet transactions
 * Follows the same styling patterns as WalletOverview and WalletSettings
 */
@Composable
fun TransactionHistoryScreen(
    viewModel: WalletViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val transactions by viewModel.transactions.observeAsState(emptyList())
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header Navigation (ChatHeader style - consistent with other wallet screens)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Back button - positioned all the way to the left with minimal margin
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-8).dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(16.dp),
                        tint = colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "All Transactions",
                        style = typography.titleMedium,
                        color = colorScheme.onSurface
                    )
                }
            }
        }
        
        if (transactions.isEmpty()) {
            // Empty state (following consistent styling)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = "No transactions",
                        tint = colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No transactions yet",
                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                        style = typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Start by sending or receiving some sats!",
                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                        style = typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Transaction count header (following section header styling)
            Text(
                text = "ALL TRANSACTIONS (${transactions.size})",
                style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Scrollable list of all transactions
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions) { transaction ->
                    TransactionItem(
                        label = transaction.description ?: getDefaultDescription(transaction.type),
                        date = formatTimestamp(transaction.timestamp),
                        amount = formatTransactionAmount(transaction),
                        status = mapTransactionStatus(transaction.status),
                        enabled = true
                    )
                }
            }
        }
    }
}

// Utility functions (reused from WalletOverview.kt for consistency)

private fun formatTransactionAmount(transaction: WalletTransaction): String {
    val isIncoming = transaction.type in listOf(
        TransactionType.CASHU_RECEIVE,
        TransactionType.LIGHTNING_RECEIVE,
        TransactionType.MINT
    )
    val sign = if (isIncoming) "+" else "-"
    val amount = when {
        transaction.amount.toLong() >= 1000 -> String.format("%,d ₿", transaction.amount.toLong())
        else -> "${transaction.amount.toLong()} ₿"
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

private fun formatTimestamp(timestamp: Date): String {
    val now = Date()
    val diff = now.time - timestamp.time
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(timestamp)
    }
}

private fun getDefaultDescription(type: TransactionType): String {
    return when (type) {
        TransactionType.CASHU_SEND -> "Cashu token sent"
        TransactionType.CASHU_RECEIVE -> "Cashu token received"
        TransactionType.LIGHTNING_SEND -> "Lightning payment sent"
        TransactionType.LIGHTNING_RECEIVE -> "Lightning payment received"
        TransactionType.MINT -> "Ecash minted"
        TransactionType.MELT -> "Ecash melted"
    }
} 