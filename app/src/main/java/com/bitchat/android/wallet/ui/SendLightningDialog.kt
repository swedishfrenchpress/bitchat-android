package com.bitchat.android.wallet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.wallet.data.MeltQuote
import com.bitchat.android.wallet.viewmodel.WalletViewModel
import com.bitchat.android.wallet.ui.BitchatButton
import com.bitchat.android.wallet.ui.BitchatButtonStyle
import com.bitchat.android.ui.TerminalInputField

/**
 * Lightning payment send dialog content
 * Following TopUpScreen.kt UI design patterns
 */
@Composable
fun SendLightningDialog(
    viewModel: WalletViewModel,
    currentMeltQuote: MeltQuote?,
    isLoading: Boolean,
    maxAmount: Long
) {
    var invoice by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    
    if (currentMeltQuote != null) {
        // Show quote and pay button (following TopUpScreen pattern)
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Amount section header (following TopUpScreen pattern)
            Text(
                text = "AMOUNT",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Amount display box (following TopUpScreen pattern)
            Box(
                modifier = Modifier
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
                    // Main amount display - following TopUpScreen pattern
                    Text(
                        text = "₿${currentMeltQuote.amount.toLong()}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.8f
                        )
                    )
                    
                    // USD equivalent (simple conversion like TopUpScreen)
                    val usdAmount = currentMeltQuote.amount.toLong() * 0.001
                    Text(
                        text = String.format("%.2f USD", usdAmount),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    // Fee if applicable
                    if (currentMeltQuote.feeReserve.toLong() > 0) {
                        Text(
                            text = "Fee: ₿${currentMeltQuote.feeReserve.toLong()}",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Status section
            Text(
                text = "STATUS",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Status card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "Lightning",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = "Payment Quote Ready",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Quote expires in 5 minutes",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Pay button
            BitchatButton(
                text = if (isLoading) "Processing..." else "Pay Invoice",
                onClick = {
                    viewModel.payLightningInvoice(currentMeltQuote.id)
                },
                enabled = !isLoading,
                style = BitchatButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        // Show invoice input (following TopUpScreen pattern)
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Invoice section header
            Text(
                text = "LIGHTNING INVOICE",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Invoice input field
            TerminalInputField(
                value = invoice,
                onValueChange = { invoice = it },
                placeholder = "lnbc...",
                minLines = 3,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            )
            
            // Actions section header
            Text(
                text = "ACTIONS",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Paste button
            BitchatButton(
                text = "Paste from Clipboard",
                onClick = {
                    clipboardManager.getText()?.text?.let { clipText ->
                        if (clipText.startsWith("lnbc") || clipText.startsWith("lnbtb")) {
                            invoice = clipText
                        }
                    }
                },
                style = BitchatButtonStyle.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            
            // Get quote button
            BitchatButton(
                text = if (isLoading) "Getting Quote..." else "Get Quote",
                onClick = {
                    if (invoice.isNotEmpty()) {
                        viewModel.createMeltQuote(invoice)
                    }
                },
                enabled = !isLoading && invoice.isNotEmpty(),
                style = BitchatButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
} 