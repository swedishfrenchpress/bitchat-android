package com.bitchat.android.wallet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.wallet.data.CashuToken
import com.bitchat.android.wallet.viewmodel.WalletViewModel
import com.bitchat.android.ui.walletcomponents.BitchatButton
import com.bitchat.android.ui.walletcomponents.BitchatButtonStyle

/**
 * Ecash (Cashu) token receive dialog content
 */
@Composable
fun ReceiveEcashDialog(
    viewModel: WalletViewModel,
    decodedToken: CashuToken?,
    isLoading: Boolean
) {
    val token by viewModel.tokenInput.observeAsState("")
    val clipboardManager = LocalClipboardManager.current
    
    if (decodedToken != null) {
        // Show decoded token info and receive button
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Token info card
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
                    // Token icon
                    Icon(
                        imageVector = Icons.Filled.Toll,
                        contentDescription = "Token",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Cashu Token",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Amount
                    Text(
                        text = "${decodedToken.amount.toLong()}​₿",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "From: ${decodedToken.mint}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    
                    // Memo if present
                    if (!decodedToken.memo.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Memo: ${decodedToken.memo}",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // Receive button
            BitchatButton(
                text = if (isLoading) "Receiving..." else "Receive Token",
                onClick = {
                    viewModel.receiveCashuToken(decodedToken.token)
                },
                enabled = !isLoading,
                style = BitchatButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        // Show token input
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Token input field
            OutlinedTextField(
                value = token,
                onValueChange = { if (!isLoading) viewModel.setTokenInput(it) },
                enabled = !isLoading,
                label = {
                    Text(
                        text = "Cashu Token",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                placeholder = {
                    Text(
                        text = "cashuA...",
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
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (!isLoading) {
                                // TODO: Implement QR code scanning
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCode,
                            contentDescription = "Scan QR",
                            tint = if (isLoading) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
            
            // Paste button
            BitchatButton(
                text = "Paste from Clipboard",
                onClick = {
                    clipboardManager.getText()?.text?.let { clipText ->
                        if (clipText.startsWith("cashu")) {
                            viewModel.setTokenInput(clipText)
                        }
                    }
                },
                enabled = !isLoading,
                style = BitchatButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

 