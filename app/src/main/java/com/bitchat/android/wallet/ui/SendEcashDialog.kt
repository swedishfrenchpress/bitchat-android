package com.bitchat.android.wallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.parsing.CashuTokenParser
import com.bitchat.android.wallet.ui.WalletUtils
import com.bitchat.android.wallet.viewmodel.WalletViewModel
import com.bitchat.android.ui.walletcomponents.BitchatButton
import com.bitchat.android.ui.walletcomponents.BitchatButtonStyle

/**
 * Ecash (Cashu) token send dialog content
 */
@Composable
fun SendEcashDialog(
    viewModel: WalletViewModel,
    generatedToken: String?,
    isLoading: Boolean,
    maxAmount: Long
) {
    var amount by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    
    if (generatedToken != null) {
        // Parse token to extract information
        val parser = remember { CashuTokenParser() }
        val parsedToken = remember(generatedToken) { parser.parseToken(generatedToken) }
        
        // Show generated token
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
                    // Success icon
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Token Created Successfully",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Amount display
                    parsedToken?.let { token ->
                        Text(
                            text = "${token.amount.toLong()}​₿",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        
                        if (!token.memo.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "\"${token.memo}\"",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "From: ${token.mintUrl}",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // Token display (collapsible)
            var showFullToken by remember { mutableStateOf(false) }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cashu Token",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        
                        IconButton(
                            onClick = { showFullToken = !showFullToken }
                        ) {
                            Icon(
                                imageVector = if (showFullToken) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (showFullToken) "Hide" else "Show",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    if (showFullToken) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SelectionContainer {
                            Text(
                                text = generatedToken,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${generatedToken.take(50)}...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Copy button
            BitchatButton(
                text = "Copy Token",
                onClick = {
                    clipboardManager.setText(AnnotatedString(generatedToken))
                },
                style = BitchatButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        // Show input form
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Request focus on the amount input when dialog opens
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            
            // Amount input
            OutlinedTextField(
                value = amount,
                onValueChange = { if (!isLoading) amount = it },
                enabled = !isLoading,
                label = {
                    Text(
                        text = "Amount (₿)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                    .padding(bottom = 16.dp)
                    .focusRequester(focusRequester),
                shape = RoundedCornerShape(16.dp)
            )
            
            // Memo input
            OutlinedTextField(
                value = memo,
                onValueChange = { if (!isLoading) memo = it },
                enabled = !isLoading,
                label = {
                    Text(
                        text = "Memo (optional)",
                        style = MaterialTheme.typography.bodyMedium
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
                    .padding(bottom = 32.dp),
                shape = RoundedCornerShape(16.dp)
            )
            
            // Create token button
            BitchatButton(
                text = if (isLoading) "Creating..." else "Create Token",
                onClick = {
                    amount.toLongOrNull()?.let { amountSats ->
                        viewModel.createCashuToken(amountSats, memo.ifEmpty { null })
                    }
                },
                enabled = !isLoading && amount.toLongOrNull()?.let { it > 0 && it <= maxAmount } == true,
                style = BitchatButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
} 