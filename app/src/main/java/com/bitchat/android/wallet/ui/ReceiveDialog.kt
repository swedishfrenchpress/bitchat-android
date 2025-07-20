package com.bitchat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitchat.android.wallet.viewmodel.WalletViewModel
import com.bitchat.android.ui.TerminalInputField
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.*
import com.bitchat.android.wallet.ui.BitchatButton
import com.bitchat.android.wallet.ui.BitchatButtonStyle
import com.bitchat.android.wallet.ui.MintDropDownSelector

/**
 * Top Up screen following Figma design exactly
 * Shows mint selection, amount input, and top up button
 */
@Composable
fun ReceiveView(
    viewModel: WalletViewModel,
    onNavigateBack: () -> Unit
) {
    val balance by viewModel.balance.observeAsState(0L)
    val isLoading by viewModel.isLoading.observeAsState(false)
    val currentMintQuote by viewModel.currentMintQuote.observeAsState()
    
    var amountSats by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // Amount calculations (following TopUpScreen pattern)
    val satsAmount = amountSats.toLongOrNull() ?: 0L
    val usdAmount = satsAmount * 0.001
    
    val formattedSats = if (satsAmount > 0) {
        NumberFormat.getNumberInstance(Locale.US).format(satsAmount)
    } else "0"
    
    val formattedUsd = if (usdAmount > 0) {
        String.format("%.2f", usdAmount)
    } else "0.00"
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp)
    ) {
        // Header - matching Figma design exactly  
        TopUpHeader(
            onBackClick = onNavigateBack
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // TOP UP title (following Figma design)
        Text(
            text = "TOP UP",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Mint Selection (following Figma design exactly)
        MintDropDownSelector(
            mintName = "Antifiat Fiat",
            mintUrl = "https://antifiat.cash",
            bitcoinAmount = "0 ₿", // Following Figma exactly
            onDropdownClick = { /* TODO: Implement mint selection */ },
            expanded = false,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Balance Display Area (following Figma design)
        var showAmountDialog by remember { mutableStateOf(false) }
        
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
                .clickable { showAmountDialog = true }
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Visual amount display (following Figma exactly)
                Text(
                    text = "$formattedSats ₿",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // USD conversion (following Figma exactly)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$$formattedUsd",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit amount",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        
        // Amount input dialog
        if (showAmountDialog) {
            // Auto-focus when dialog opens
            LaunchedEffect(Unit) {
                delay(300) // Small delay for dialog animation
                focusRequester.requestFocus()
            }
            
            AlertDialog(
                onDismissRequest = { showAmountDialog = false },
                title = {
                    Text(
                        text = "Enter Amount",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = {
                    TerminalInputField(
                        value = amountSats,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() } && newValue.length <= 10) {
                                amountSats = newValue
                            }
                        },
                        placeholder = "Enter amount in sats...",
                        keyboardType = KeyboardType.Number,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { showAmountDialog = false }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            amountSats = ""
                            showAmountDialog = false 
                        }
                    ) {
                        Text("Clear")
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Top Up Button (following Figma design exactly)
        BitchatButton(
            text = "Top Up",
            onClick = {
                if (satsAmount > 0) {
                    focusManager.clearFocus()
                    viewModel.createMintQuote(satsAmount, "Top up wallet")
                }
            },
            enabled = satsAmount > 0 && !isLoading,
            style = BitchatButtonStyle.Primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }
}

// Header component following Figma design exactly
@Composable
private fun TopUpHeader(
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        
        // Settings button (following Figma design)
        IconButton(
            onClick = { /* TODO: Navigate to settings */ },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}


