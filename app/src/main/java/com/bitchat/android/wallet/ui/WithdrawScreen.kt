package com.bitchat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitchat.android.ui.theme.BitchatTheme
import com.bitchat.android.wallet.viewmodel.WalletViewModel

/**
 * WithdrawScreen - Unified withdraw functionality (Lightning & Ecash)
 * Matches TopUpScreen.kt design patterns and uses TotalBalance component
 */

enum class WithdrawMethod {
    LIGHTNING, ECASH
}

@Composable
fun WithdrawScreen(
    viewModel: WalletViewModel,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMethod by remember { mutableStateOf(WithdrawMethod.LIGHTNING) }
    var lightningInvoice by remember { mutableStateOf("") }
    var ecashToken by remember { mutableStateOf("") }
    
    val clipboardManager = LocalClipboardManager.current
    
    // ViewModel state
    val balance by viewModel.balance.observeAsState(0L)
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.errorMessage.observeAsState()
    val currentMeltQuote by viewModel.currentMeltQuote.observeAsState()
    val generatedToken by viewModel.generatedToken.observeAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header - exactly matching TopUpScreen pattern
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Back button - positioned exactly like TopUpScreen
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
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
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Withdraw",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Settings button - positioned exactly like TopUpScreen
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Wallet Settings",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // Balance Section - using TotalBalance component exactly as-is
        Text(
            text = "TOTAL BALANCE",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        TotalBalance(
            bitcoinAmount = formatBitcoinAmount(balance),
            dollarAmount = formatDollarAmount(balance),
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Method Selection Tabs - matching TopUpScreen exactly
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Lightning tab
            WithdrawMethodTab(
                text = "Lightning",
                icon = Icons.Filled.Bolt,
                selected = selectedMethod == WithdrawMethod.LIGHTNING,
                onClick = { 
                    selectedMethod = WithdrawMethod.LIGHTNING
                    ecashToken = ""
                },
                modifier = Modifier.weight(1f)
            )
            
            // Ecash tab
            WithdrawMethodTab(
                text = "Ecash",
                icon = Icons.Filled.AttachMoney,
                selected = selectedMethod == WithdrawMethod.ECASH,
                onClick = { 
                    selectedMethod = WithdrawMethod.ECASH
                    lightningInvoice = ""
                },
                modifier = Modifier.weight(1f)
            )
        }
        
        // Content based on selected method
        when (selectedMethod) {
            WithdrawMethod.LIGHTNING -> {
                LightningWithdrawContent(
                    lightningInvoice = lightningInvoice,
                    currentMeltQuote = currentMeltQuote,
                    isLoading = isLoading,
                    clipboardManager = clipboardManager,
                    onLightningInvoiceChange = { lightningInvoice = it },
                    onPayInvoice = {
                        if (lightningInvoice.isNotBlank()) {
                            viewModel.createMeltQuote(lightningInvoice)
                        }
                    }
                )
            }
            
            WithdrawMethod.ECASH -> {
                EcashWithdrawContent(
                    ecashToken = ecashToken,
                    generatedToken = generatedToken,
                    isLoading = isLoading,
                    clipboardManager = clipboardManager,
                    onEcashTokenChange = { ecashToken = it },
                    onCreateToken = {
                        // This would create a Cashu token for withdrawal
                        // For now, just show the generated token
                    }
                )
            }
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
private fun WithdrawMethodTab(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    
    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 0.25.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun LightningWithdrawContent(
    lightningInvoice: String,
    currentMeltQuote: com.bitchat.android.wallet.data.MeltQuote?,
    isLoading: Boolean,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onLightningInvoiceChange: (String) -> Unit,
    onPayInvoice: () -> Unit
) {
    if (currentMeltQuote != null) {
        // Show melt quote details
        Column {
            Text(
                text = "LIGHTNING PAYMENT",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
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
                    Text(
                        text = "${currentMeltQuote.amount.toLong()}​₿",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.8f
                        )
                    )
                    
                    val usdAmount = currentMeltQuote.amount.toLong() * 0.001
                    Text(
                        text = String.format("%.2f USD", usdAmount),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            BitchatButton(
                text = if (isLoading) "Processing..." else "Pay Invoice",
                onClick = onPayInvoice,
                enabled = !isLoading,
                style = BitchatButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        // Show invoice input
        Column {
            Text(
                text = "LIGHTNING INVOICE",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Invoice input box - matching TotalBalance dimensions
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
                OutlinedTextField(
                    value = lightningInvoice,
                    onValueChange = { if (!isLoading) onLightningInvoiceChange(it) },
                    enabled = !isLoading,
                    label = { Text("Lightning Invoice", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { 
                        Text(
                            "lnbc...", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        ) 
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(4.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BitchatButton(
                    text = "Paste from Clipboard",
                    onClick = {
                        clipboardManager.getText()?.text?.let { clipText ->
                            if (clipText.startsWith("lnbc")) {
                                onLightningInvoiceChange(clipText)
                            }
                        }
                    },
                    enabled = !isLoading,
                    style = BitchatButtonStyle.Secondary,
                    modifier = Modifier.weight(1f)
                )
                
                BitchatButton(
                    text = if (isLoading) "Processing..." else "Pay Invoice",
                    onClick = onPayInvoice,
                    enabled = !isLoading && lightningInvoice.isNotBlank(),
                    style = BitchatButtonStyle.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EcashWithdrawContent(
    ecashToken: String,
    generatedToken: String?,
    isLoading: Boolean,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onEcashTokenChange: (String) -> Unit,
    onCreateToken: () -> Unit
) {
    if (generatedToken != null) {
        // Show generated token
        Column {
            Text(
                text = "ECASH TOKEN",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
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
                    Text(
                        text = "Token Generated",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.8f
                        )
                    )
                    
                    Text(
                        text = "Ready to copy",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            BitchatButton(
                text = "Copy Token",
                onClick = {
                    generatedToken?.let { token ->
                        clipboardManager.setText(AnnotatedString(token))
                    }
                },
                style = BitchatButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        // Show token input
        Column {
            Text(
                text = "ECASH TOKEN",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Token input box - matching TotalBalance dimensions
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
                OutlinedTextField(
                    value = ecashToken,
                    onValueChange = { if (!isLoading) onEcashTokenChange(it) },
                    enabled = !isLoading,
                    label = { Text("Ecash Token", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { 
                        Text(
                            "cashuA...", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        ) 
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(4.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BitchatButton(
                    text = "Paste from Clipboard",
                    onClick = {
                        clipboardManager.getText()?.text?.let { clipText ->
                            if (clipText.startsWith("cashu")) {
                                onEcashTokenChange(clipText)
                            }
                        }
                    },
                    enabled = !isLoading,
                    style = BitchatButtonStyle.Secondary,
                    modifier = Modifier.weight(1f)
                )
                
                BitchatButton(
                    text = if (isLoading) "Creating..." else "Create Token",
                    onClick = onCreateToken,
                    enabled = !isLoading && ecashToken.isNotBlank(),
                    style = BitchatButtonStyle.Primary,
                    modifier = Modifier.weight(1f)
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

// Formatting functions - matching WalletOverview
private fun formatBitcoinAmount(sats: Long): String {
    return when {
        sats >= 100_000_000 -> String.format("%.8f​₿", sats / 100_000_000.0)
        sats >= 1000 -> String.format("%,d​₿", sats)
        else -> "$sats​₿"
    }
}

private fun formatDollarAmount(sats: Long): String {
    val usdAmount = sats * 0.001
    return if (usdAmount > 0) String.format("$%.2f", usdAmount) else "$0.00"
}

@Preview(showBackground = true)
@Composable
fun WithdrawScreenPreview() {
    BitchatTheme {
        // WithdrawScreen(
        //     viewModel = WalletViewModel(), // Can't preview with real viewModel
        //     onBackClick = {},
        //     onSettingsClick = {}
        // )
    }
} 