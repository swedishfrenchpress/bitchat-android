package com.bitchat.android.wallet.ui

import androidx.compose.foundation.BorderStroke
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

/**
 * Lightning payment send dialog content
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
        // Show quote and pay button
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quote info card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Lightning icon
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "Lightning",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Payment Quote",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Amount
                    Text(
                        text = "${currentMeltQuote.amount.toLong()}​₿",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    
                    // Fee if applicable
                    if (currentMeltQuote.feeReserve.toLong() > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Fee: ${currentMeltQuote.feeReserve.toLong()}​₿",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Quote expiry info
                    Text(
                        text = "Quote expires in 5 minutes",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
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
        // Show invoice input
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Invoice input field
            OutlinedTextField(
                value = invoice,
                onValueChange = { invoice = it },
                label = {
                    Text(
                        text = "Lightning Invoice",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                placeholder = {
                    Text(
                        text = "lnbc...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                minLines = 3,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            // TODO: Implement QR code scanning
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCode,
                            contentDescription = "Scan QR",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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