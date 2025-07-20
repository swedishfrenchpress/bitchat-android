package com.bitchat.android.wallet.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
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
import com.bitchat.android.ui.TerminalInputField
import com.bitchat.android.wallet.viewmodel.WalletViewModel
import com.bitchat.android.parsing.CashuTokenParser
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
    IDLE, LOADING, SUCCESS, ERROR, CANCELLED
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
    val paymentStateHolder = remember { mutableStateOf(PaymentState.IDLE) }
    val isParsingInvoiceHolder = remember { mutableStateOf(false) }
    val paymentErrorHolder = remember { mutableStateOf<String?>(null) }
    val lightningInvoiceErrorHolder = remember { mutableStateOf<String?>(null) }
    val balanceErrorHolder = remember { mutableStateOf<String?>(null) }
    
    val paymentState by paymentStateHolder
    val isParsingInvoice by isParsingInvoiceHolder
    val paymentError by paymentErrorHolder
    val lightningInvoiceError by lightningInvoiceErrorHolder
    val balanceError by balanceErrorHolder
    
    val clipboardManager = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // ViewModel state
    val balance by viewModel.balance.observeAsState(0L)
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.errorMessage.observeAsState()
    val currentMeltQuote by viewModel.currentMeltQuote.observeAsState()
    val generatedToken by viewModel.generatedToken.observeAsState()
    
    // Conversion calculations
    val satsAmount = amountSats.toLongOrNull() ?: 0L
    val fiatAmount = amountFiat.toDoubleOrNull() ?: 0.0
    val usdAmount = satsAmount * 0.001
    val calculatedSats = (fiatAmount / 0.001).toLong()
    
    // Balance validation for ecash withdrawals
    val finalAmount = if (showSatsInput) satsAmount else calculatedSats
    val isAmountValid = finalAmount > 0 && finalAmount <= balance
    val balanceErrorMessage = if (finalAmount > balance && finalAmount > 0) {
        "You only have ${balance} sats available in this mint. Try reducing the amount or select a different mint."
    } else null
    
    // Update balance error when amount changes
    LaunchedEffect(finalAmount, balance) {
        balanceErrorHolder.value = balanceErrorMessage
    }
    
    // Lightning invoice validator
    fun validateLightningInvoice(invoice: String): String? {
        return when {
            invoice.isBlank() -> null
            !invoice.startsWith("lnbc") && !invoice.startsWith("lnbtb") -> 
                "Invalid Lightning invoice format. Please check and try again."
            invoice.length < 20 -> 
                "Invoice appears to be incomplete. Please check and try again."
            else -> null
        }
    }
    
    // Reset function to clear all state
    fun resetWithdrawState() {
        amountSats = ""
        amountFiat = ""
        lightningInvoice = ""
        showSatsInput = true
        paymentStateHolder.value = PaymentState.IDLE
        paymentErrorHolder.value = null
        lightningInvoiceErrorHolder.value = null
        balanceErrorHolder.value = null
        isParsingInvoiceHolder.value = false
        viewModel.clearCurrentMeltQuote()
    }
    
    // Immediate keyboard focus for amount input - with safety check
    LaunchedEffect(selectedMethod) {
        try {
            // Add a small delay to ensure the composable is fully laid out
            delay(100)
            focusRequester.requestFocus()
        } catch (e: Exception) {
            Log.w("WithdrawScreen", "Error requesting focus: ${e.message}")
        }
    }
    
    // Handle payment success and auto-navigation
    LaunchedEffect(paymentState) {
        if (paymentState == PaymentState.SUCCESS) {
            // Show success state briefly, then navigate directly to wallet overview
            delay(1500) // 1.5 second delay to show success state
            paymentStateHolder.value = PaymentState.IDLE
            // Clear focus before navigation to prevent crash
            try {
                focusManager.clearFocus()
            } catch (e: Exception) {
                Log.w("WithdrawScreen", "Error clearing focus: ${e.message}")
            }
            // Navigate back to wallet overview with success animation
            onBackClick()
        }
    }
    
    // Handle Lightning invoice parsing with validation
    LaunchedEffect(lightningInvoice) {
        // Clear previous errors
        lightningInvoiceErrorHolder.value = null
        paymentErrorHolder.value = null
        
        if (lightningInvoice.isNotBlank()) {
            // Validate invoice format first
            val validationError = validateLightningInvoice(lightningInvoice)
            if (validationError != null) {
                lightningInvoiceErrorHolder.value = validationError
                return@LaunchedEffect
            }
            
            // Invoice format is valid, try to parse with CDK
            isParsingInvoiceHolder.value = true
            
            try {
                viewModel.createMeltQuote(lightningInvoice)
                // CDK will parse the invoice and extract the correct amount
                delay(1000) // Show loading for 1 second
            } catch (e: Exception) {
                Log.e("WithdrawScreen", "Error creating melt quote: ${e.message}")
                paymentErrorHolder.value = "Failed to parse invoice: ${e.message}"
            } finally {
                isParsingInvoiceHolder.value = false
            }
        }
    }
    
    // Monitor melt quote creation and update amount
    LaunchedEffect(currentMeltQuote) {
        currentMeltQuote?.let { quote ->
            if (isParsingInvoice) {
                // CDK has parsed the invoice and created a melt quote
                // Use the amount from CDK (this is the correct amount)
                val cdkAmount = quote.amount.toLong()
                if (cdkAmount > 0) {
                    amountSats = cdkAmount.toString()
                    amountFiat = ""
                    showSatsInput = true
                    Log.d("WithdrawScreen", "CDK parsed amount: $cdkAmount sats")
                }
                isParsingInvoiceHolder.value = false
            }
        }
    }
    
    // Monitor error messages and handle payment failures
    LaunchedEffect(errorMessage) {
        if (errorMessage != null && paymentState == PaymentState.LOADING) {
            Log.e("WithdrawScreen", "Payment error: $errorMessage")
            paymentStateHolder.value = PaymentState.ERROR
            paymentErrorHolder.value = errorMessage
        }
    }
    
    // Payment timeout handling
    LaunchedEffect(paymentState) {
        if (paymentState == PaymentState.LOADING) {
            delay(30000) // 30 second timeout
            if (paymentState == PaymentState.LOADING) {
                Log.e("WithdrawScreen", "Payment timeout")
                paymentStateHolder.value = PaymentState.ERROR
                paymentErrorHolder.value = "Payment timed out. Please try again."
            }
        }
    }
    
    // Cleanup on navigation away
    DisposableEffect(Unit) {
        onDispose {
            if (paymentState == PaymentState.LOADING) {
                Log.d("WithdrawScreen", "User navigated away during payment, cancelling")
                paymentStateHolder.value = PaymentState.CANCELLED
                paymentErrorHolder.value = null
            }
            // Clear generated token when navigating away
            Log.d("WithdrawScreen", "Clearing generated token on navigation")
            viewModel.clearGeneratedToken()
        }
    }
    
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
                    paymentError = paymentError,
                    lightningInvoiceError = lightningInvoiceError,
                    isAmountValid = isAmountValid,
                    balanceError = balanceError,
                    paymentStateHolder = paymentStateHolder,
                    paymentErrorHolder = paymentErrorHolder,
                    lightningInvoiceErrorHolder = lightningInvoiceErrorHolder,
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
                            paymentStateHolder.value = PaymentState.LOADING
                            paymentErrorHolder.value = null
                            // First create melt quote, then pay it
                            viewModel.createMeltQuote(lightningInvoice)
                        }
                    },
                    onPaymentComplete = {
                        Log.d("WithdrawScreen", "Payment completed, setting success state")
                        // Trigger success animation with payment details
                        currentMeltQuote?.let { quote ->
                            val animationData = WalletViewModel.SuccessAnimationData(
                                type = WalletViewModel.SuccessAnimationType.LIGHTNING_SENT,
                                amount = quote.amount.toLong(),
                                unit = quote.unit,
                                description = "Lightning payment sent"
                            )
                            viewModel.showSuccessAnimation(animationData)
                        }
                        paymentStateHolder.value = PaymentState.SUCCESS
                    },
                    onPaymentError = { error ->
                        Log.e("WithdrawScreen", "Payment error: $error")
                        paymentStateHolder.value = PaymentState.ERROR
                        paymentErrorHolder.value = error
                    },
                    onPaymentCancel = {
                        Log.d("WithdrawScreen", "Payment cancelled")
                        paymentStateHolder.value = PaymentState.IDLE
                        paymentErrorHolder.value = null
                    },
                    onResetState = { resetWithdrawState() },
                    validateLightningInvoice = { validateLightningInvoice(it) },
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
                    isAmountValid = isAmountValid,
                    balanceError = balanceError,
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
                        if (finalAmount > 0 && isAmountValid) {
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
        paymentError?.let { message ->
            Spacer(modifier = Modifier.height(16.dp))
            TerminalErrorCard(
                message = message,
                onDismiss = { paymentErrorHolder.value = null }
            )
        }
    }
}

// CDK handles all Lightning invoice parsing - no manual parsing needed

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
    paymentError: String?,
    lightningInvoiceError: String?,
    isAmountValid: Boolean,
    balanceError: String?,
    paymentStateHolder: androidx.compose.runtime.MutableState<PaymentState>,
    paymentErrorHolder: androidx.compose.runtime.MutableState<String?>,
    lightningInvoiceErrorHolder: androidx.compose.runtime.MutableState<String?>,
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
    onPaymentError: (String) -> Unit,
    onPaymentCancel: () -> Unit,
    onResetState: () -> Unit,
    validateLightningInvoice: (String) -> String?,
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
                
                PaymentState.ERROR -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Error icon
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Payment failed",
                                tint = colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Text(
                            text = "Payment failed: ${paymentError ?: "Unknown error"}",
                            style = typography.bodyMedium,
                            color = colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        
                        // Retry and Cancel buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BitchatButton(
                                text = "Retry",
                                onClick = {
                                    paymentStateHolder.value = PaymentState.LOADING
                                    paymentErrorHolder.value = null
                                    viewModel.createMeltQuote(lightningInvoice)
                                },
                                enabled = !isLoading,
                                style = BitchatButtonStyle.Secondary,
                                modifier = Modifier.weight(1f)
                            )
                            BitchatButton(
                                text = "Cancel",
                                onClick = onPaymentCancel,
                                enabled = true,
                                style = BitchatButtonStyle.Secondary,
                                modifier = Modifier.weight(1f)
                            )
                                                 }
                     }
                 }
                 
                 PaymentState.CANCELLED -> {
                     Column(
                         horizontalAlignment = Alignment.CenterHorizontally,
                         verticalArrangement = Arrangement.spacedBy(16.dp)
                     ) {
                         // Cancelled icon
                         Box(
                             modifier = Modifier.size(40.dp),
                             contentAlignment = Alignment.Center
                         ) {
                             Icon(
                                 imageVector = Icons.Filled.Cancel,
                                 contentDescription = "Payment cancelled",
                                 tint = colorScheme.onSurface.copy(alpha = 0.6f),
                                 modifier = Modifier.size(32.dp)
                             )
                         }
                         
                         Text(
                             text = "Payment cancelled",
                             style = typography.bodyMedium,
                             color = colorScheme.onSurface.copy(alpha = 0.6f),
                             textAlign = TextAlign.Center
                         )
                         
                         // Try again button
                         BitchatButton(
                             text = "Try Again",
                             onClick = {
                                 paymentStateHolder.value = PaymentState.IDLE
                                 paymentErrorHolder.value = null
                             },
                             enabled = true,
                             style = BitchatButtonStyle.Primary,
                             modifier = Modifier.fillMaxWidth()
                         )
                     }
                 }
                 
                 PaymentState.IDLE -> {
                    currentMeltQuote?.let { quote ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Pay button
                            BitchatButton(
                                text = "Pay",
                                onClick = {
                                    // Pay the Lightning invoice using the melt quote
                                    Log.d("WithdrawScreen", "Starting payment for quote: ${quote.id}")
                                    paymentStateHolder.value = PaymentState.LOADING
                                    viewModel.payLightningInvoice(
                                        quoteId = quote.id,
                                                                            onPaymentComplete = {
                                        Log.d("WithdrawScreen", "Payment completed via callback")
                                        // Trigger success animation with payment details
                                        val animationData = WalletViewModel.SuccessAnimationData(
                                            type = WalletViewModel.SuccessAnimationType.LIGHTNING_SENT,
                                            amount = quote.amount.toLong(),
                                            unit = quote.unit,
                                            description = "Lightning payment sent"
                                        )
                                        viewModel.showSuccessAnimation(animationData)
                                        onPaymentComplete()
                                    },
                                        onPaymentError = { error ->
                                            Log.e("WithdrawScreen", "Payment failed via callback: $error")
                                            onPaymentError(error)
                                        }
                                    )
                                },
                                enabled = !isLoading,
                                style = BitchatButtonStyle.Primary,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            // Go Back button
                            BitchatButton(
                                text = "Go Back",
                                onClick = { onResetState() },
                                enabled = true,
                                style = BitchatButtonStyle.Secondary,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } ?: run {
                        BitchatButton(
                            text = "Pay",
                            onClick = onPayInvoice,
                            enabled = !isLoading && lightningInvoice.isNotBlank() && isAmountValid,
                            style = BitchatButtonStyle.Primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
            
            // Balance error message - display right after amount input
            if (balanceError != null) {
                TerminalBalanceErrorCard(message = balanceError!!)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Text(
                text = "LIGHTNING INVOICE / ADDRESS",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Standard input field - using standardized terminal input
            TerminalInputField(
                value = lightningInvoice,
                onValueChange = { if (!isLoading && !isParsingInvoice) onLightningInvoiceChange(it) },
                placeholder = "Enter invoice or address...",
                enabled = !isLoading && !isParsingInvoice,
                singleLine = true,
                isError = lightningInvoiceError != null,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Error message display
            lightningInvoiceError?.let { error ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
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
                            // Validate the clipboard content before pasting
                            val validationError = validateLightningInvoice(clipText)
                            if (validationError != null) {
                                lightningInvoiceErrorHolder.value = validationError
                            } else {
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
                             isAmountValid && paymentState == PaymentState.IDLE,
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
    isAmountValid: Boolean,
    balanceError: String?,
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
        // Parse the generated token to show details
        val parser = remember { CashuTokenParser() }
        val parsedToken = remember(generatedToken) { parser.parseToken(generatedToken) }
        
        // Show generated token with layout matching TopUpScreen exactly
        Column {
            Text(
                text = "ECASH TOKEN",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Token display field - matching TopUpScreen OutlinedTextField exactly
            OutlinedTextField(
                value = generatedToken,
                onValueChange = { }, // Read-only
                enabled = false,
                label = { Text("Generated Token", style = MaterialTheme.typography.bodySmall) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    disabledBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    disabledTextColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(4.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                )
            )
            
            // Show token details below input if token is decoded - matching TopUpScreen exactly
            if (parsedToken != null) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Token details section - matching TopUpScreen exactly
                Text(
                    text = "TOKEN DETAILS",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Simple token details display - matching TopUpScreen exactly
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
                        text = "Amount: ${parsedToken.amount} ${parsedToken.unit}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "From: ${parsedToken.mintUrl}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (!parsedToken.memo.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Memo: ${parsedToken.memo}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
            
            // Balance error message - display right after amount input
            if (balanceError != null) {
                TerminalBalanceErrorCard(message = balanceError!!)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Spacer to push button to bottom
            Spacer(modifier = Modifier.weight(1f))
            
            // Single CTA button - "Create Ecash"
            BitchatButton(
                text = if (isLoading) "Creating..." else "Create Ecash",
                onClick = onCreateToken,
                enabled = !isLoading && isAmountValid,
                style = BitchatButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TerminalErrorCard(
    message: String,
    onDismiss: () -> Unit
) {
    // Terminal-style error box - matching the app's aesthetic
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f), // Subtle red tint
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 0.25.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f), // Subtle red border
                shape = RoundedCornerShape(4.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Terminal-style error indicator
            Text(
                text = "ERROR:",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.padding(end = 8.dp)
            )
            
            // Error message
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.weight(1f)
            )
            
            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TerminalBalanceErrorCard(
    message: String
) {
    // Terminal-style balance error box - no close button, auto-clears
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f), // Subtle red tint
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 0.25.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f), // Subtle red border
                shape = RoundedCornerShape(4.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            // Header row with warning triangle icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Warning triangle icon
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp)
                )
                
                // Error title
                Text(
                    text = "INSUFFICIENT FUNDS",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Error message - left aligned
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
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