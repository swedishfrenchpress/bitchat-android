package com.bitchat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import com.bitchat.android.wallet.data.*
import com.bitchat.android.wallet.ui.BitchatButton
import com.bitchat.android.wallet.ui.BitchatButtonStyle
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.tooling.preview.Preview
import com.bitchat.android.ui.theme.BitchatTheme
import java.math.BigDecimal

/**
 * Detailed transaction view screen
 * Shows comprehensive information about a specific transaction
 */
@Composable
fun TransactionDetailScreen(
    transaction: WalletTransaction,
    onBackClick: () -> Unit = {},
    onReclaimToken: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val clipboardManager = LocalClipboardManager.current
    var showFullToken by remember { mutableStateOf(false) }
    var showFullInvoice by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(16.dp)
    ) {
        // Header Navigation (consistent with other wallet screens)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier.align(Alignment.CenterStart)
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
                        text = "Transaction Details",
                        style = typography.titleMedium,
                        color = colorScheme.onSurface
                    )
                }
            }
        }
        
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Transaction header
            TransactionDetailHeader(transaction = transaction)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Transaction details based on type
            when (transaction.type) {
                TransactionType.CASHU_SEND, TransactionType.CASHU_RECEIVE -> {
                    CashuTransactionDetails(
                        transaction = transaction,
                        showFullToken = showFullToken,
                        onShowFullTokenChange = { showFullToken = it },
                        onCopyToken = { 
                            transaction.token?.let { token ->
                                clipboardManager.setText(AnnotatedString(token))
                            }
                        },
                        onReclaimToken = onReclaimToken
                    )
                }
                TransactionType.LIGHTNING_SEND, TransactionType.LIGHTNING_RECEIVE -> {
                    LightningTransactionDetails(
                        transaction = transaction,
                        showFullInvoice = showFullInvoice,
                        onShowFullInvoiceChange = { showFullInvoice = it },
                        onCopyInvoice = {
                            transaction.quote?.let { quote ->
                                clipboardManager.setText(AnnotatedString(quote))
                            }
                        }
                    )
                }
                TransactionType.MINT, TransactionType.MELT -> {
                    MintMeltTransactionDetails(
                        transaction = transaction,
                        showFullInvoice = showFullInvoice,
                        onShowFullInvoiceChange = { showFullInvoice = it },
                        onCopyInvoice = {
                            transaction.quote?.let { quote ->
                                clipboardManager.setText(AnnotatedString(quote))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailHeader(transaction: WalletTransaction) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface, RoundedCornerShape(4.dp))
            .padding(16.dp)
    ) {
        // Status and amount row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status
            TransactionStatusBadge(status = transaction.status)
            
            // Amount
            Text(
                text = formatTransactionAmount(transaction),
                style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Transaction type and timestamp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getTransactionTypeLabel(transaction.type),
                style = typography.bodyMedium,
                color = colorScheme.onSurface
            )
            
            Text(
                text = formatDetailedTimestamp(transaction.timestamp),
                style = typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        // Description if available
        transaction.description?.let { description ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = typography.bodyMedium,
                color = colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun TransactionStatusBadge(status: com.bitchat.android.wallet.data.TransactionStatus) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    
    val (backgroundColor, textColor, text) = when (status) {
        com.bitchat.android.wallet.data.TransactionStatus.PENDING -> Triple(
            colorScheme.primary.copy(alpha = 0.1f),
            colorScheme.primary,
            "pending"
        )
        com.bitchat.android.wallet.data.TransactionStatus.CONFIRMED -> Triple(
            colorScheme.primary.copy(alpha = 0.1f),
            colorScheme.primary,
            "confirmed"
        )
        com.bitchat.android.wallet.data.TransactionStatus.FAILED -> Triple(
            colorScheme.error.copy(alpha = 0.1f),
            colorScheme.error,
            "failed"
        )
        com.bitchat.android.wallet.data.TransactionStatus.EXPIRED -> Triple(
            colorScheme.onSurface.copy(alpha = 0.1f),
            colorScheme.onSurface.copy(alpha = 0.7f),
            "expired"
        )
    }
    
    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = typography.labelSmall,
            color = textColor
        )
    }
}

@Composable
private fun CashuTransactionDetails(
    transaction: WalletTransaction,
    showFullToken: Boolean,
    onShowFullTokenChange: (Boolean) -> Unit,
    onCopyToken: () -> Unit,
    onReclaimToken: ((String) -> Unit)?
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface, RoundedCornerShape(4.dp))
            .padding(16.dp)
    ) {
        // Section header
        Text(
            text = "ecash details",
            style = typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // Mint information
        transaction.mint?.let { mint ->
            DetailRow(
                label = "mint",
                value = mint,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // Token (truncated with expand option)
        transaction.token?.let { token ->
            DetailRow(
                label = "token",
                value = if (showFullToken) token else "${token.take(32)}...",
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Expand/collapse button
            BitchatButton(
                text = if (showFullToken) "show less" else "show full token",
                onClick = { onShowFullTokenChange(!showFullToken) },
                style = BitchatButtonStyle.Secondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Copy token button
            BitchatButton(
                text = "copy token",
                onClick = onCopyToken,
                style = BitchatButtonStyle.Primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Reclaim token button (only for sent tokens that might be reclaimable)
            if (transaction.type == TransactionType.CASHU_SEND && onReclaimToken != null) {
                BitchatButton(
                    text = "reclaim token",
                    onClick = { onReclaimToken(token) },
                    style = BitchatButtonStyle.Primary,
                    enabled = transaction.status != com.bitchat.android.wallet.data.TransactionStatus.CONFIRMED,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun LightningTransactionDetails(
    transaction: WalletTransaction,
    showFullInvoice: Boolean,
    onShowFullInvoiceChange: (Boolean) -> Unit,
    onCopyInvoice: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface, RoundedCornerShape(4.dp))
            .padding(16.dp)
    ) {
        // Section header
        Text(
            text = "lightning details",
            style = typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // Mint information
        transaction.mint?.let { mint ->
            DetailRow(
                label = "mint",
                value = mint,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // Lightning invoice (truncated with expand option)
        transaction.quote?.let { invoice ->
            DetailRow(
                label = "invoice",
                value = if (showFullInvoice) invoice else "${invoice.take(32)}...",
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Expand/collapse button
            BitchatButton(
                text = if (showFullInvoice) "show less" else "show full invoice",
                onClick = { onShowFullInvoiceChange(!showFullInvoice) },
                style = BitchatButtonStyle.Secondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Copy invoice button
            BitchatButton(
                text = "copy invoice",
                onClick = onCopyInvoice,
                style = BitchatButtonStyle.Primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // Fee information if available
        transaction.fee?.let { fee ->
            DetailRow(
                label = "fee",
                value = "₿${fee.toLong()}",
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun MintMeltTransactionDetails(
    transaction: WalletTransaction,
    showFullInvoice: Boolean,
    onShowFullInvoiceChange: (Boolean) -> Unit,
    onCopyInvoice: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface, RoundedCornerShape(4.dp))
            .padding(16.dp)
    ) {
        // Section header
        Text(
            text = if (transaction.type == TransactionType.MINT) "mint details" else "melt details",
            style = typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // Mint information
        transaction.mint?.let { mint ->
            DetailRow(
                label = "mint",
                value = mint,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // Lightning invoice (truncated with expand option)
        transaction.quote?.let { invoice ->
            DetailRow(
                label = "invoice",
                value = if (showFullInvoice) invoice else "${invoice.take(32)}...",
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Expand/collapse button
            BitchatButton(
                text = if (showFullInvoice) "show less" else "show full invoice",
                onClick = { onShowFullInvoiceChange(!showFullInvoice) },
                style = BitchatButtonStyle.Secondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Copy invoice button
            BitchatButton(
                text = "copy invoice",
                onClick = onCopyInvoice,
                style = BitchatButtonStyle.Primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // Fee information if available
        transaction.fee?.let { fee ->
            DetailRow(
                label = "fee",
                value = "₿${fee.toLong()}",
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    
    Column(modifier = modifier) {
        Text(
            text = label,
            style = typography.labelSmall,
            color = colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = typography.bodyMedium,
            color = colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Utility functions
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

private fun getTransactionTypeLabel(type: TransactionType): String {
    return when (type) {
        TransactionType.CASHU_SEND -> "ecash sent"
        TransactionType.CASHU_RECEIVE -> "ecash received"
        TransactionType.LIGHTNING_SEND -> "lightning sent"
        TransactionType.LIGHTNING_RECEIVE -> "lightning received"
        TransactionType.MINT -> "ecash minted"
        TransactionType.MELT -> "ecash melted"
    }
}

private fun formatDetailedTimestamp(timestamp: Date): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm:ss", Locale.getDefault())
    return formatter.format(timestamp)
}

@Preview(showBackground = true)
@Composable
fun TransactionDetailScreenPreview() {
    BitchatTheme {
        val sampleTransaction = WalletTransaction(
            id = "1",
            type = TransactionType.CASHU_RECEIVE,
            amount = BigDecimal("21000"),
            unit = "sat",
            status = com.bitchat.android.wallet.data.TransactionStatus.CONFIRMED,
            timestamp = Date(),
            description = "Received from Alice",
            mint = "antifiat.cash",
            token = "cashuAeyJ0b2tlbiI6W3sicHJvb2ZzIjpbeyJpZCI6InRlc3QiLCJhbW91bnQiOjIxMDAwLCJzZWNyZXQiOiJ0ZXN0IiwicHJvb2YiOnsidGVzdCI6InRlc3QifX1dLCJtaW50IjoiYW50aWZpYXQuY2FzaCJ9XX0="
        )
        
        TransactionDetailScreen(
            transaction = sampleTransaction,
            onBackClick = {},
            onReclaimToken = {}
        )
    }
} 