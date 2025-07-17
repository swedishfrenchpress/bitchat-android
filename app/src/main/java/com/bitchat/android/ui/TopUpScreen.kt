package com.bitchat.android.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitchat.android.ui.theme.BitchatTheme
import com.bitchat.android.ui.walletcomponents.MintDropDownSelector
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.*

/**
 * TopUpScreen - Stable version with proper numeric input and keyboard management
 */

@Composable
fun TopUpScreen(
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMint by remember { mutableStateOf("Antiflat Fiat") }
    var selectedMintUrl by remember { mutableStateOf("https://antiflat.cash") }
    var amountSats by remember { mutableStateOf("") }
    var isUsdMode by remember { mutableStateOf(false) }
    var generatedInvoice by remember { mutableStateOf<String?>(null) }
    var showQrCode by remember { mutableStateOf(false) }
    
    val clipboardManager = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // Constants for conversion (mock rates)
    val satsToBtc = 100_000_000.0
    val btcToUsdRate = 67000.0
    
    // Staggered focus: Let screen fade in first, then show keyboard
    LaunchedEffect(Unit) {
        delay(500) // Wait for screen fade to complete
        focusRequester.requestFocus()
    }
    
    // Calculate equivalent amounts
    val satsAmount = amountSats.toLongOrNull() ?: 0L
    val btcAmount = satsAmount / satsToBtc
    val usdAmount = btcAmount * btcToUsdRate
    
    // Format currency values
    val formattedBtc = if (satsAmount > 0) {
        String.format("%.8f", btcAmount).trimEnd('0').trimEnd('.')
    } else "0"
    
    val formattedUsd = if (usdAmount > 0) {
        NumberFormat.getCurrencyInstance(Locale.US).format(usdAmount)
    } else "$0"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                // Dismiss keyboard when clicking outside
                focusManager.clearFocus()
            }
    ) {
        // Header - matching ChatHeader pattern
        TopUpHeader(
            onBackClick = onBackClick,
            onSettingsClick = onSettingsClick
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Mint Selection
        Text(
            text = "MINT",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        MintDropDownSelector(
            mintName = selectedMint,
            mintUrl = selectedMintUrl,
            bitcoinAmount = "0​₿", // Using thin space as per memory
            onDropdownClick = { /* TODO: Implement mint selection */ },
            expanded = false
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Amount Input Section
        Text(
            text = "AMOUNT",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Amount input with proper keyboard
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
                    // Re-focus when clicking on the amount box
                    focusRequester.requestFocus()
                }
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Main amount display
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isUsdMode) {
                        Text(
                            text = formattedUsd,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.8f
                            )
                        )
                    } else {
                        Text(
                            text = "${formattedBtc}​₿",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.8f
                            )
                        )
                    }
                    
                    // Swap button
                    IconButton(
                        onClick = { isUsdMode = !isUsdMode },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapVert,
                            contentDescription = "Swap currency",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                // Secondary amount display
                Text(
                    text = if (isUsdMode) "${formattedBtc}​₿" else formattedUsd,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                // Hidden input field for numeric keyboard with proper keyboard actions
                BasicTextField(
                    value = amountSats,
                    onValueChange = { newValue ->
                        // Only allow digits and limit length
                        if (newValue.all { it.isDigit() } && newValue.length <= 10) {
                            amountSats = newValue
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            // Dismiss keyboard when enter/done is pressed
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
        
        // QR Code section
        if (showQrCode && generatedInvoice != null) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Left-aligned title
                Text(
                    text = "LIGHTNING INVOICE",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Centered QR code
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
                        Text(
                            text = "QR CODE\n\n${generatedInvoice!!.take(20)}...",
                            color = Color.Black,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        // Action Buttons
        if (showQrCode && generatedInvoice != null) {
            // Copy button only (back arrow handles close)
            BitchatButton(
                text = "Copy",
                onClick = {
                    clipboardManager.setText(AnnotatedString(generatedInvoice ?: ""))
                },
                style = BitchatButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // Top Up button
            BitchatButton(
                text = "Top Up",
                onClick = {
                    if (satsAmount > 0) {
                        // Dismiss keyboard first
                        focusManager.clearFocus()
                        
                        generatedInvoice = "lnbc${satsAmount}u1p3xnhl2pp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqsfpp3qjmp7lwpagxun9pygexvgpjdc4jdj85fr9yxcm0tfrqy7swr3vqq7t0dp5kuqq5v5s73ulrpfegfjqy6qsqqqqqqqqqqqq"
                        showQrCode = true
                    }
                },
                enabled = satsAmount > 0,
                style = BitchatButtonStyle.Primary
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TopUpHeader(
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Box(modifier = Modifier.fillMaxWidth()) {
        // Back button with "top up" text - matching ChatHeader pattern
        Button(
            onClick = onBackClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = colorScheme.primary
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
                    tint = colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "top up",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.primary
                )
            }
        }
        
        // Settings icon on the right
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = colorScheme.primary
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TopUpScreenPreview() {
    BitchatTheme {
        TopUpScreen(
            onBackClick = {},
            onSettingsClick = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TopUpScreenDarkPreview() {
    BitchatTheme {
        TopUpScreen(
            onBackClick = {},
            onSettingsClick = {}
        )
    }
} 