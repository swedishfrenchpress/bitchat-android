package com.bitchat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.ui.theme.BitchatTheme
import com.bitchat.android.wallet.viewmodel.WalletViewModel
import java.text.NumberFormat
import java.util.*
import kotlinx.coroutines.delay
import android.util.Log

/**
 * WithdrawScreen - Unified withdraw functionality (Lightning & Ecash)
 * Matches TopUpScreen.kt design patterns and uses TotalBalance component
 */

enum class WithdrawMethod {
    LIGHTNING, ECASH
}

enum class PaymentState {
    IDLE, LOADING, SUCCESS
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
    var amountSats by remember { mutableStateOf("") }
    var amountFiat by remember { mutableStateOf("") }
    var showSatsInput by remember { mutableStateOf(true) }
    var paymentState by remember { mutableStateOf(PaymentState.IDLE) }
    var isParsingInvoice by remember { mutableStateOf(false) }
    
    val clipboardManager = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // ViewModel state
    val balance by viewModel.balance.observeAsState(0L)
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.errorMessage.observeAsState()
    val currentMeltQuote by viewModel.currentMeltQuote.observeAsState()
    val generatedToken by viewModel.generatedToken.observeAsState()
    
    // Immediate keyboard focus for amount input
    LaunchedEffect(selectedMethod) {
        focusRequester.requestFocus()
    }
    
    // Handle payment success and auto-navigation
    LaunchedEffect(paymentState) {
        if (paymentState == PaymentState.SUCCESS) {
            delay(2500) // 2.5 second delay
            paymentState = PaymentState.IDLE
            onBackClick() // Navigate back to wallet overview
        }
    }
    
    // Monitor melt quote creation and payment completion
    LaunchedEffect(currentMeltQuote) {
        if (currentMeltQuote != null && paymentState == PaymentState.LOADING) {
            Log.d("WithdrawScreen", "Melt quote created, proceeding with payment")
            // Melt quote was created, now we can proceed with payment
            // The payment will be triggered by the user clicking Pay in the melt quote UI
        }
    }
    
    // Monitor error messages
    LaunchedEffect(errorMessage) {
        if (errorMessage != null && paymentState == PaymentState.LOADING) {
            Log.e("WithdrawScreen", "Payment error: $errorMessage")
            paymentState = PaymentState.IDLE
            // Clear the error after a delay
            delay(5000)
            viewModel.clearError()
        }
    }
    
    // Handle Lightning invoice parsing
    LaunchedEffect(lightningInvoice) {
        if (lightningInvoice.isNotBlank() && (lightningInvoice.startsWith("lnbc") || lightningInvoice.startsWith("lnbtb"))) {
            isParsingInvoice = true
            delay(1000) // Show loading for 1 second
            
            val parsedAmount = parseLightningInvoiceAmount(lightningInvoice)
            if (parsedAmount > 0) {
                amountSats = parsedAmount.toString()
                amountFiat = ""
                showSatsInput = true
            }
            isParsingInvoice = false
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
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            }
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
                    amountSats = amountSats,
                    amountFiat = amountFiat,
                    showSatsInput = showSatsInput,
                    formattedSats = formattedSats,
                    formattedUsd = formattedUsd,
                    lightningInvoice = lightningInvoice,
                    currentMeltQuote = currentMeltQuote,
                    isLoading = isLoading,
                    paymentState = paymentState,
                    isParsingInvoice = isParsingInvoice,
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
                    onLightningInvoiceChange = { lightningInvoice = it },
                    onPayInvoice = {
                        val finalAmount = if (showSatsInput) satsAmount else calculatedSats
                        if (lightningInvoice.isNotBlank() && finalAmount > 0) {
                            Log.d("WithdrawScreen", "Creating melt quote for invoice: ${lightningInvoice.take(20)}...")
                            paymentState = PaymentState.LOADING
                            // First create melt quote, then pay it
                            viewModel.createMeltQuote(lightningInvoice)
                        }
                    },
                    onPaymentComplete = {
                        Log.d("WithdrawScreen", "Payment completed, setting success state")
                        paymentState = PaymentState.SUCCESS
                    },
                    viewModel = viewModel
                )
            }
            
            WithdrawMethod.ECASH -> {
                EcashWithdrawContent(
                    amountSats = amountSats,
                    amountFiat = amountFiat,
                    showSatsInput = showSatsInput,
                    formattedSats = formattedSats,
                    formattedUsd = formattedUsd,
                    ecashToken = ecashToken,
                    generatedToken = generatedToken,
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
                    onEcashTokenChange = { ecashToken = it },
                    onCreateToken = {
                        val finalAmount = if (showSatsInput) satsAmount else calculatedSats
                        if (finalAmount > 0) {
                            focusManager.clearFocus()
                            // Create real Cashu token using CDK
                            viewModel.createCashuToken(finalAmount, "Withdrawal token")
                        }
                    },
                    viewModel = viewModel
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

/**
 * Parse Lightning invoice to extract amount in satoshis
 * Supports lnbc (mainnet) and lnbtb (testnet) invoices
 * Simplified parser for demo purposes - in production use a proper bech32 decoder
 */
private fun parseLightningInvoiceAmount(invoice: String): Long {
    return try {
        // For demo purposes, we'll extract a simple amount
        // In production, you'd use a proper Lightning invoice decoder
        when {
            invoice.startsWith("lnbc") -> {
                // Extract amount from lnbc invoice format
                // Look for amount pattern: lnbc[amount][rest]
                // This is a simplified approach - real parsing is more complex
                val amountMatch = Regex("lnbc(\\d+)[a-zA-Z]").find(invoice)
                amountMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            }
            invoice.startsWith("lnbtb") -> {
                // Extract amount from lnbtb invoice format
                val amountMatch = Regex("lnbtb(\\d+)[a-zA-Z]").find(invoice)
                amountMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            }
            else -> 0L
        }
    } catch (e: Exception) {
        Log.e("WithdrawScreen", "Error parsing Lightning invoice: ${e.message}")
        0L
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
    amountSats: String,
    amountFiat: String,
    showSatsInput: Boolean,
    formattedSats: String,
    formattedUsd: String,
    lightningInvoice: String,
    currentMeltQuote: com.bitchat.android.wallet.data.MeltQuote?,
    isLoading: Boolean,
    paymentState: PaymentState,
    isParsingInvoice: Boolean,
    focusRequester: FocusRequester,
    focusManager: androidx.compose.ui.focus.FocusManager,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onAmountSatsChange: (String) -> Unit,
    onAmountFiatChange: (String) -> Unit,
    onShowSatsInputChange: (Boolean) -> Unit,
    onSwapCurrency: () -> Unit,
    onLightningInvoiceChange: (String) -> Unit,
    onPayInvoice: () -> Unit,
    onPaymentComplete: () -> Unit,
    viewModel: com.bitchat.android.wallet.viewmodel.WalletViewModel
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
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
            
            // Payment button with loading/success states
            when (paymentState) {
                PaymentState.LOADING -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Green spinner
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                color = colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                        }
                        
                        Text(
                            text = "Sending payment...",
                            style = typography.bodyMedium,
                            color = colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
                
                PaymentState.SUCCESS -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Success checkmark
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Payment successful",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Text(
                            text = "Payment sent!",
                            style = typography.bodyMedium,
                            color = colorScheme.primary
                        )
                    }
                }
                
                PaymentState.IDLE -> {
                    BitchatButton(
                        text = "Pay",
                        onClick = {
                            // Pay the Lightning invoice using the melt quote
                            Log.d("WithdrawScreen", "Starting payment for quote: ${currentMeltQuote.id}")
                            viewModel.payLightningInvoice(currentMeltQuote.id) {
                                Log.d("WithdrawScreen", "Payment completed via callback")
                                onPaymentComplete()
                            }
                        },
                        enabled = !isLoading,
                        style = BitchatButtonStyle.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    } else {
        // Show amount input and invoice input
        Column {
            // Amount Input Section - matching TopUpScreen pattern
            Text(
                text = "AMOUNT",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Amount input box - exactly matching TopUpScreen styling
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
                    .padding(24.dp), // Exact padding from TopUpScreen
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Exact spacing from TopUpScreen
                ) {
                    // Amount display row with swap arrow
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Main amount display or loading spinner
                        if (isParsingInvoice) {
                            // Loading spinner while parsing invoice
                            Box(
                                modifier = Modifier.size(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            // Normal amount display
                            Text(
                                text = if (showSatsInput) "${formattedSats}​₿" else "$${formattedUsd}",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.8f
                                )
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Swap arrow (disabled during parsing)
                        IconButton(
                            onClick = onSwapCurrency,
                            enabled = !isParsingInvoice,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapVert,
                                contentDescription = "Swap currency",
                                tint = if (isParsingInvoice) 
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) 
                                else 
                                    MaterialTheme.colorScheme.primary,
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
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "LIGHTNING INVOICE / ADDRESS",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Standard input field - no nested box
            OutlinedTextField(
                value = lightningInvoice,
                onValueChange = { if (!isLoading && !isParsingInvoice) onLightningInvoiceChange(it) },
                enabled = !isLoading && !isParsingInvoice,
                label = { Text("Lightning Invoice / Address", style = MaterialTheme.typography.bodySmall) },
                placeholder = { 
                    Text(
                        "Enter invoice / address...", 
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
                singleLine = true,
                shape = RoundedCornerShape(4.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            
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
                            if (clipText.startsWith("lnbc") || clipText.startsWith("lnbtb")) {
                                onLightningInvoiceChange(clipText)
                            }
                        }
                    },
                    enabled = !isLoading && !isParsingInvoice,
                    style = BitchatButtonStyle.Secondary,
                    modifier = Modifier.weight(1f)
                )
                
                BitchatButton(
                    text = "Pay",
                    onClick = onPayInvoice,
                    enabled = !isLoading && !isParsingInvoice && lightningInvoice.isNotBlank() && 
                             (amountSats.toLongOrNull() ?: 0L) > 0 && paymentState == PaymentState.IDLE,
                    style = BitchatButtonStyle.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EcashWithdrawContent(
    amountSats: String,
    amountFiat: String,
    showSatsInput: Boolean,
    formattedSats: String,
    formattedUsd: String,
    ecashToken: String,
    generatedToken: String?,
    isLoading: Boolean,
    focusRequester: FocusRequester,
    focusManager: androidx.compose.ui.focus.FocusManager,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onAmountSatsChange: (String) -> Unit,
    onAmountFiatChange: (String) -> Unit,
    onShowSatsInputChange: (Boolean) -> Unit,
    onSwapCurrency: () -> Unit,
    onEcashTokenChange: (String) -> Unit,
    onCreateToken: () -> Unit,
    viewModel: com.bitchat.android.wallet.viewmodel.WalletViewModel
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
                        text = "Ecash Token Generated",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.8f
                        )
                    )
                    
                    Text(
                        text = if (generatedToken.length > 50) {
                            "${generatedToken.take(25)}...${generatedToken.takeLast(25)}"
                        } else {
                            generatedToken
                        },
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace
                        )
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
        // Show amount input and token input
        Column {
            // Amount Input Section - matching TopUpScreen pattern
            Text(
                text = "AMOUNT",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Amount input box - exactly matching TopUpScreen styling
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
                    .padding(24.dp), // Exact padding from TopUpScreen
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Exact spacing from TopUpScreen
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
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Spacer to push button to bottom
            Spacer(modifier = Modifier.weight(1f))
            
            // Single CTA button - "Create Ecash"
            BitchatButton(
                text = if (isLoading) "Creating..." else "Create Ecash",
                onClick = onCreateToken,
                enabled = !isLoading && (amountSats.toLongOrNull() ?: 0L) > 0,
                style = BitchatButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth()
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