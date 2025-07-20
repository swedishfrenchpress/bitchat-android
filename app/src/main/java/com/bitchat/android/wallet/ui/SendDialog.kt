package com.bitchat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.wallet.viewmodel.WalletViewModel

/**
 * Full-screen send view with options for Cashu tokens or Lightning payments
 * Following TopUpScreen.kt UI design patterns
 */
@Composable
fun SendView(
    viewModel: WalletViewModel,
    onNavigateBack: () -> Unit
) {
    val sendType by viewModel.sendType.observeAsState(WalletViewModel.SendType.CASHU)
    val generatedToken by viewModel.generatedToken.observeAsState()
    val currentMeltQuote by viewModel.currentMeltQuote.observeAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val balance by viewModel.balance.observeAsState(0L)
    
    // Determine if we should show type selector
    val showTypeSelector = generatedToken == null && currentMeltQuote == null
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp)
    ) {
        // Header - matching TopUpScreen pattern exactly
        SendHeader(
            onBackClick = onNavigateBack
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Balance section header
        Text(
            text = "BALANCE",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Balance display (following TopUpScreen pattern)
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
                // Main balance display - following TopUpScreen pattern
                Text(
                                            text = "₿${balance}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.8f
                    )
                )
                
                // USD equivalent (simple conversion like TopUpScreen)
                val usdAmount = balance * 0.001
                Text(
                    text = String.format("%.2f USD available", usdAmount),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Send type selector (only show when not displaying generated token or quote)
        if (showTypeSelector) {
            Text(
                text = "TYPE",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    onClick = { viewModel.setSendType(WalletViewModel.SendType.CASHU) },
                    label = {
                        Text(
                            text = "Cashu Token",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    selected = sendType == WalletViewModel.SendType.CASHU,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                )
                
                FilterChip(
                    onClick = { viewModel.setSendType(WalletViewModel.SendType.LIGHTNING) },
                    label = {
                        Text(
                            text = "Lightning Payment",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    selected = sendType == WalletViewModel.SendType.LIGHTNING,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Content based on send type
        when (sendType) {
            WalletViewModel.SendType.CASHU -> {
                SendEcashDialog(
                    viewModel = viewModel,
                    generatedToken = generatedToken,
                    isLoading = isLoading,
                    maxAmount = balance
                )
            }
            WalletViewModel.SendType.LIGHTNING -> {
                SendLightningDialog(
                    viewModel = viewModel,
                    currentMeltQuote = currentMeltQuote,
                    isLoading = isLoading,
                    maxAmount = balance
                )
            }
        }
    }
}

// Header component following TopUpScreen pattern
@Composable
private fun SendHeader(
    onBackClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    
    // Navigation row: exactly matching TopUpScreen pattern
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back arrow on the left
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .padding(start = 0.dp)
                .size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(16.dp),
                tint = colorScheme.primary
            )
        }
        
        // Title: 'send' in lower case - matching TopUpScreen pattern
        Text(
            text = "send",
            style = typography.headlineSmall,
            color = colorScheme.primary,
            modifier = Modifier
                .padding(start = 0.dp)
                .align(Alignment.CenterVertically)
        )
        
        Spacer(modifier = Modifier.weight(1f))
    }
}


