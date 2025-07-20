package com.bitchat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.util.Log
import com.bitchat.android.ui.theme.BitchatTheme
import com.bitchat.android.ui.TerminalInputField
import com.bitchat.android.wallet.viewmodel.WalletViewModel
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.*

/**
 * Unified TopUpScreen - Consolidates all receive functionality (Lightning & Cashu)
 * Addresses UI consistency issues and provides seamless navigation
 */

enum class TopUpMethod {
    LIGHTNING, CASHU
}

@Composable
fun TopUpScreen(
    viewModel: WalletViewModel,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTokenReceived: () -> Unit = {}, // Callback to return to Wallet Overview after receiving token
    onSuccessAnimationComplete: () -> Unit = {}, // Callback when success animation completes
    modifier: Modifier = Modifier
) {
    var selectedMethod by remember { mutableStateOf(TopUpMethod.LIGHTNING) }
    var amountSats by remember { mutableStateOf("") }
    var amountFiat by remember { mutableStateOf("") }
    var showSatsInput by remember { mutableStateOf(true) }
    var cashuToken by remember { mutableStateOf("") }
    
    val clipboardManager = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // ViewModel state
    val balance by viewModel.balance.observeAsState(0L)
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.errorMessage.observeAsState()
    val currentMintQuote by viewModel.currentMintQuote.observeAsState()
    val decodedToken by viewModel.decodedToken.observeAsState()
    val tokenInput by viewModel.tokenInput.observeAsState("")
    val activeMint by viewModel.activeMint.observeAsState()
    val showSuccessAnimation by viewModel.showSuccessAnimation.observeAsState(false)
    
    // Track if we've shown a success animation to avoid premature navigation
    var hasShownSuccessAnimation by remember { mutableStateOf(false) }
    
    // Handle success animation completion and navigation
    LaunchedEffect(showSuccessAnimation) {
        if (showSuccessAnimation) {
            // Success animation has started
            hasShownSuccessAnimation = true
        } else if (hasShownSuccessAnimation) {
            // Success animation has completed after being shown
            hasShownSuccessAnimation = false
            onSuccessAnimationComplete()
        }
    }
    
    // Immediate keyboard focus for Lightning method - with safety checks
    LaunchedEffect(selectedMethod) {
        if (selectedMethod == TopUpMethod.LIGHTNING) {
            try {
                // Add a small delay to ensure the composable is fully laid out
                delay(100)
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus errors - they can happen during navigation
                Log.w("TopUpScreen", "Error requesting focus: ${e.message}")
            }
        }
    }
    
    // Conversion calculations
    val satsAmount = amountSats.toLongOrNull() ?: 0L
    val fiatAmount = amountFiat.toDoubleOrNull() ?: 0.0
    val usdAmount = satsAmount * 0.001
    val calculatedSats = (fiatAmount / 0.001).toLong()
    
    // Format displays
    val formattedSats = if (satsAmount > 0) {
        NumberFormat.getNumberInstance(Locale.US).format(satsAmount)
    } else "0"
    
    val formattedUsd = if (usdAmount > 0) {
        String.format("%.2f", usdAmount)
    } else "0.00"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            }
    ) {
        // Header - exactly matching WalletOverview pattern
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Back button - positioned exactly like WalletOverview
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
                        text = "Top Up",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Settings button - positioned exactly like WalletOverview
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
        
        // Method Selection Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Lightning tab
            TopUpMethodTab(
                text = "Lightning",
                icon = Icons.Filled.Bolt,
                selected = selectedMethod == TopUpMethod.LIGHTNING,
                onClick = { 
                    selectedMethod = TopUpMethod.LIGHTNING
                    cashuToken = ""
                    viewModel.setTokenInput("")
                },
                modifier = Modifier.weight(1f)
            )
            
            // Ecash tab
            TopUpMethodTab(
                text = "Ecash",
                icon = Icons.Filled.AttachMoney,
                selected = selectedMethod == TopUpMethod.CASHU,
                onClick = { 
                    selectedMethod = TopUpMethod.CASHU
                    amountSats = ""
                    amountFiat = ""
                    // Clear current mint quote through hideReceiveDialog which calls clearCurrentMintQuote internally
                    viewModel.hideReceiveDialog()
                },
                modifier = Modifier.weight(1f)
            )
        }
        
        // Content based on selected method
        when (selectedMethod) {
            TopUpMethod.LIGHTNING -> {
                LightningContent(
                    amountSats = amountSats,
                    amountFiat = amountFiat,
                    showSatsInput = showSatsInput,
                    formattedSats = formattedSats,
                    formattedUsd = formattedUsd,
                    currentMintQuote = currentMintQuote,
                    isLoading = isLoading,
                    focusRequester = focusRequester,
                    focusManager = focusManager,
                    clipboardManager = clipboardManager,
                    onAmountSatsChange = { amountSats = it },
                    onAmountFiatChange = { amountFiat = it },
                    onShowSatsInputChange = { showSatsInput = it },
                    onSwapCurrency = {
                        showSatsInput = !showSatsInput
                        if (showSatsInput) {
                            amountSats = calculatedSats.toString()
                            amountFiat = ""
                        } else {
                            amountFiat = formattedUsd
                            amountSats = ""
                        }
                    },
                    onCreateInvoice = {
                        val finalAmount = if (showSatsInput) satsAmount else calculatedSats
                        if (finalAmount > 0) {
                            focusManager.clearFocus()
                            viewModel.createMintQuote(finalAmount, null)
                        }
                    }
                )
            }
            
            TopUpMethod.CASHU -> {
                CashuContent(
                    tokenInput = tokenInput,
                    decodedToken = decodedToken,
                    isLoading = isLoading,
                    clipboardManager = clipboardManager,
                    onTokenInputChange = { viewModel.setTokenInput(it) },
                    onReceiveToken = { 
                        viewModel.receiveCashuToken(it)
                        // Success animation will be shown by the ViewModel
                        // Navigation back to Wallet Overview will be handled after animation
                    },
                    onPasteFromClipboard = {
                        clipboardManager.getText()?.text?.let { clipText ->
                            if (clipText.startsWith("cashu")) {
                                viewModel.setTokenInput(clipText)
                            }
                        }
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
private fun TopUpMethodTab(
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
private fun LightningContent(
    amountSats: String,
    amountFiat: String,
    showSatsInput: Boolean,
    formattedSats: String,
    formattedUsd: String,
    currentMintQuote: com.bitchat.android.wallet.data.MintQuote?,
    isLoading: Boolean,
    focusRequester: FocusRequester,
    focusManager: FocusManager,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onAmountSatsChange: (String) -> Unit,
    onAmountFiatChange: (String) -> Unit,
    onShowSatsInputChange: (Boolean) -> Unit,
    onSwapCurrency: () -> Unit,
    onCreateInvoice: () -> Unit
) {
    if (currentMintQuote != null) {
        // Show invoice and QR code
        LightningInvoiceView(
            mintQuote = currentMintQuote,
            clipboardManager = clipboardManager
        )
    } else {
        // Show amount input
        Column {
            // Amount Input Section - visually matching TotalBalance.kt
            Text(
                text = "AMOUNT",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Amount input box - exactly matching TotalBalance styling
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
                    .clickable {
                        focusRequester.requestFocus()
                    }
                    .padding(24.dp), // Exact padding from TotalBalance
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Exact spacing from TotalBalance
                ) {
                    // Amount display row with swap arrow
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Main amount display
                        Text(
                            text = if (showSatsInput) "${formattedSats}​₿" else "$${formattedUsd}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.8f
                            )
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Swap arrow
                        IconButton(
                            onClick = onSwapCurrency,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapVert,
                                contentDescription = "Swap currency",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    // Secondary amount display
                    Text(
                        text = if (showSatsInput) "$${formattedUsd} USD" else "${formattedSats}​₿",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    // Hidden input field for keyboard
                    BasicTextField(
                        value = if (showSatsInput) amountSats else amountFiat,
                        onValueChange = { newValue ->
                            if (showSatsInput) {
                                if (newValue.all { it.isDigit() } && newValue.length <= 10) {
                                    onAmountSatsChange(newValue)
                                }
                            } else {
                                // Allow digits and decimal point for fiat
                                if (newValue.matches(Regex("^\\d*\\.?\\d*$")) && newValue.length <= 10) {
                                    onAmountFiatChange(newValue)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (showSatsInput) KeyboardType.Number else KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                            }
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.Transparent
                        ),
                        cursorBrush = SolidColor(Color.Transparent),
                        modifier = Modifier
                            .size(1.dp)
                            .focusRequester(focusRequester)
                    )
                }
            }
            
            // Spacer to push button to bottom
            Spacer(modifier = Modifier.weight(1f))
            
            // Create invoice button
            BitchatButton(
                text = if (isLoading) "Creating..." else "Create Invoice",
                onClick = onCreateInvoice,
                enabled = !isLoading && (amountSats.toLongOrNull() ?: 0L) > 0,
                style = BitchatButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LightningInvoiceView(
    mintQuote: com.bitchat.android.wallet.data.MintQuote,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Amount display
        Text(
            text = "AMOUNT",
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
                    text = "${mintQuote.amount.toLong()}​₿",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.8f
                    )
                )
                
                val usdAmount = mintQuote.amount.toLong() * 0.001
                Text(
                    text = String.format("%.2f USD", usdAmount),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // QR Code
        Text(
            text = "LIGHTNING INVOICE",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCode,
                        contentDescription = "QR Code",
                        tint = Color.Black,
                        modifier = Modifier.size(120.dp)
                    )
                    Text(
                        text = "QR CODE",
                        color = Color.Black,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Copy button
        BitchatButton(
            text = "Copy Invoice",
            onClick = {
                clipboardManager.setText(AnnotatedString(mintQuote.request))
            },
            style = BitchatButtonStyle.Primary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CashuContent(
    tokenInput: String,
    decodedToken: com.bitchat.android.wallet.data.CashuToken?,
    isLoading: Boolean,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onTokenInputChange: (String) -> Unit,
    onReceiveToken: (String) -> Unit,
    onPasteFromClipboard: () -> Unit
) {
    Column {
        Text(
            text = "ECASH TOKEN",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Ecash token input field - using standardized terminal input
        TerminalInputField(
            value = tokenInput,
            onValueChange = { if (!isLoading) onTokenInputChange(it) },
            placeholder = "cashuA...",
            enabled = !isLoading,
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Show token details below input if token is decoded
        if (decodedToken != null) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Token details section
            Text(
                text = "TOKEN DETAILS",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Simple token details display
            Column(
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
                    .padding(16.dp)
            ) {
                Text(
                    text = "Amount: ${decodedToken.amount.toLong()} ₿",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "From: ${decodedToken.mint}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (!decodedToken.memo.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Memo: ${decodedToken.memo}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Spacer to push button to bottom
        Spacer(modifier = Modifier.weight(1f))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BitchatButton(
                text = "Paste from Clipboard",
                onClick = onPasteFromClipboard,
                enabled = !isLoading,
                style = BitchatButtonStyle.Secondary,
                modifier = Modifier.weight(1f)
            )
            
            if (decodedToken != null) {
                BitchatButton(
                    text = if (isLoading) "Receiving..." else "Receive Token",
                    onClick = { onReceiveToken(decodedToken.token) },
                    enabled = !isLoading,
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

@Preview(showBackground = true)
@Composable
fun TopUpScreenPreview() {
    BitchatTheme {
        // TopUpScreen(
        //     viewModel = WalletViewModel(), // Can't preview with real viewModel
        //     onBackClick = {},
        //     onSettingsClick = {}
        // )
    }
} 