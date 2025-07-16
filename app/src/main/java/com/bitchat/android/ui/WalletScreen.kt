package com.bitchat.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitchat.android.ui.walletcomponents.MintUrlInput

@Composable
fun WalletScreen(onClose: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    var url by remember { mutableStateOf("") }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colorScheme.background // Set background to pure black in dark mode
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.Start
        ) {
            // Navigation row: match ChatHeader style
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back arrow on the left
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .padding(start = 0.dp)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(16.dp),
                        tint = colorScheme.primary
                    )
                }
                // Title: 'bitcoin wallet' in lower case, styled and positioned like 'bitchat*'
                Text(
                    text = "bitcoin wallet",
                    style = typography.headlineSmall,
                    color = colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 0.dp)
                        .align(Alignment.CenterVertically)
                )
                Spacer(modifier = Modifier.weight(1f))
                // Settings icon on the right
                IconButton(
                    onClick = { /* TODO: Settings action */ },
                    modifier = Modifier
                        .padding(end = 0.dp)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(16.dp),
                        tint = colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // MintUrlInput component
            MintUrlInput(
                url = url,
                onUrlChange = { url = it },
                onAddClick = {},
                modifier = Modifier.padding(horizontal = 12.dp) // Add this!
            )
        }
    }
} 