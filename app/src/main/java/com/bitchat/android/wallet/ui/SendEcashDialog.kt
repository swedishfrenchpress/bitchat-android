package com.bitchat.android.wallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import com.bitchat.android.wallet.ui.BitchatButton
import com.bitchat.android.wallet.ui.BitchatButtonStyle
import com.bitchat.android.ui.TerminalInputField

/**
 * Ecash (Cashu) token send dialog content
 * Following TopUpScreen.kt UI design patterns
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
        
        // Show generated token (following TopUpScreen pattern)
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            parsedToken?.let { token ->
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
                            text = "₿${token.amount.toLong()}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.8f
                            )
                        )
                        
                        // USD equivalent (simple conversion like TopUpScreen)
                        val usdAmount = token.amount.toLong() * 0.001
                        Text(
                            text = String.format("%.2f USD", usdAmount),
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodyLarge
                        )
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Success",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column {
                                Text(
                                    text = "Token Created Successfully",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Ready to send",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        if (!token.memo.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Memo: \"${token.memo}\"",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "From: ${token.mintUrl}",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Token section (following TopUpScreen pattern)
            Text(
                text = "CASHU TOKEN",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Token display (collapsible)
            var showFullToken by remember { mutableStateOf(false) }
            
            // Centered token display (following TopUpScreen layout)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
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
                                text = "Token Ready",
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
        // Show input form (following TopUpScreen pattern)
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Request focus on the amount input when dialog opens
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            
            // Amount section header
            Text(
                text = "AMOUNT",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Amount input field
            TerminalInputField(
                value = amount,
                onValueChange = { if (!isLoading) amount = it },
                placeholder = "Enter amount in sats...",
                enabled = !isLoading,
                keyboardType = KeyboardType.Number,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
                    .focusRequester(focusRequester)
            )
            
            // Memo section header
            Text(
                text = "MEMO",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Memo input field
            TerminalInputField(
                value = memo,
                onValueChange = { if (!isLoading) memo = it },
                placeholder = "Memo (optional)",
                enabled = !isLoading,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
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