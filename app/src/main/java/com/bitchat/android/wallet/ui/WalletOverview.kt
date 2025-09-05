package com.bitchat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import com.bitchat.android.wallet.data.WalletTransaction
import com.bitchat.android.wallet.data.TransactionType
import com.bitchat.android.wallet.data.TransactionStatus
import com.bitchat.android.wallet.service.CashuService
import com.bitchat.android.wallet.viewmodel.WalletViewModel
import com.bitchat.android.wallet.ui.TotalBalance
import com.bitchat.android.wallet.ui.TopUpButton
import com.bitchat.android.wallet.ui.WithdrawButton
import com.bitchat.android.wallet.ui.TransactionItem
import com.bitchat.android.wallet.ui.TransactionStatus as UITransactionStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main wallet overview screen showing balance and recent transactions
 */
@Composable
fun WalletOverview(
    viewModel: WalletViewModel,
    onBackToChat: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onTransactionHistoryClick: () -> Unit = {},
    onTopUpClick: () -> Unit = {},
    onWithdrawClick: () -> Unit = {},
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
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(16.dp)
    ) {
        // Wallet Header Navigation (ChatHeader style)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Back button - positioned with proper spacing
            Button(
                onClick = onBackToChat,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Wallet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Settings button - positioned on the right with proper spacing
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Wallet Settings",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
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
        
        // Recent Transaction (show single most recent transaction)
        if (transactions.isNotEmpty()) {
            Text(
                text = "RECENT TRANSACTION",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Show only the most recent transaction
            TransactionItem(
                label = transactions.first().description ?: getDefaultDescription(transactions.first().type),
                date = formatTimestamp(transactions.first().timestamp),
                amount = formatTransactionAmount(transactions.first()),
                status = mapTransactionStatus(transactions.first().status),
                enabled = true
            )
            
            // Show "All Transactions" link if there are multiple transactions
            if (transactions.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "All Transactions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTransactionHistoryClick() }
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        // Spacer to push action buttons to bottom
        Spacer(modifier = Modifier.weight(1f))
        
        // Action Buttons (in thumb zone at bottom)
        Text(
            text = "ACTIONS",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TopUpButton(
                onClick = onTopUpClick,
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            )
            WithdrawButton(
                onClick = onWithdrawClick,
                modifier = Modifier.weight(1f),
                enabled = !isLoading && balance > 0
            )
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
        sats >= 100_000_000 -> String.format("₿%.8f", sats / 100_000_000.0)
        sats >= 1000 -> String.format("₿%,d", sats)
        else -> "₿$sats"
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

