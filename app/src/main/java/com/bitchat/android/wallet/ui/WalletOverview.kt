package com.bitchat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.wallet.data.WalletTransaction
import com.bitchat.android.wallet.data.TransactionType
import com.bitchat.android.wallet.data.TransactionStatus
import com.bitchat.android.wallet.service.CashuService
import com.bitchat.android.wallet.viewmodel.WalletViewModel
import com.bitchat.android.ui.walletcomponents.TotalBalance
import com.bitchat.android.ui.walletcomponents.TopUpButton
import com.bitchat.android.ui.walletcomponents.WithdrawButton
import com.bitchat.android.ui.walletcomponents.TransactionItem
import com.bitchat.android.ui.walletcomponents.TransactionStatus as UITransactionStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main wallet overview screen showing balance and recent transactions
 */
@Composable
fun WalletOverview(
    viewModel: WalletViewModel,
    onBackToChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val balance by viewModel.balance.observeAsState(0L)
    val transactions by viewModel.transactions.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.errorMessage.observeAsState()
    val activeMint by viewModel.activeMint.observeAsState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Back to Chat button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            TextButton(
                onClick = onBackToChat,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back to Chat",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Chat",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // Balance Section
        Text(
            text = "TOTAL BALANCE",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        TotalBalance(
            bitcoinAmount = formatBitcoinAmount(balance),
            dollarAmount = formatDollarAmount(balance),
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        // Action Buttons
        Text(
            text = "ACTIONS",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TopUpButton(
                onClick = { viewModel.showReceiveDialog() },
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            )
            WithdrawButton(
                onClick = { viewModel.showSendDialog() },
                modifier = Modifier.weight(1f),
                enabled = !isLoading && balance > 0
            )
        }
        
        // Recent Transactions
        Text(
            text = "RECENT TRANSACTIONS",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        if (transactions.isEmpty()) {
            EmptyTransactionsCard()
        } else {
            TransactionsList(transactions = transactions)
        }
        
        // Error message
        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(16.dp))
            ErrorCard(
                message = message,
                onDismiss = { viewModel.clearError() }
            )
        }
    }
}

@Composable
private fun TransactionsList(transactions: List<WalletTransaction>) {
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

@Composable
private fun EmptyTransactionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "No transactions",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No transactions yet",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Start by sending or receiving some sats!",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Formatting functions for new components

private fun formatBitcoinAmount(sats: Long): String {
    return when {
        sats >= 100_000_000 -> String.format("%.8f​₿", sats / 100_000_000.0)
        sats >= 1000 -> String.format("%,d​₿", sats)
        else -> "$sats​₿"
    }
}

private fun formatDollarAmount(sats: Long): String {
    // Simple conversion: 1 sat = $0.001 USD (approximation)
    val usdAmount = sats * 0.001
    return if (usdAmount > 0) String.format("$%.2f", usdAmount) else "$0.00"
}

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

// Utility functions

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

