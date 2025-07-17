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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.bitchat.android.ui.walletcomponents.MintRatingItem
import androidx.compose.foundation.clickable

data class MintData(val name: String, val url: String, val rating: Int)

@Composable
fun WalletScreen(onClose: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    var url by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var foundMints by remember { mutableStateOf(listOf<MintData>()) }
    var selectedMint by remember { mutableStateOf<MintData?>(null) }

    // Dummy data for found mints
    val dummyMints = listOf(
        MintData("Cashu Mint 1", "https://mint1.example.com", 5),
        MintData("Cashu Mint 2", "https://mint2.example.com", 4),
        MintData("Cashu Mint 3", "https://mint3.example.com", 3)
    )

    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(1500)
            foundMints = dummyMints
            isLoading = false
        }
    }

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

            // Top area: either MintUrlInput or selected MintRatingItem
            if (selectedMint == null) {
                MintUrlInput(
                    url = url,
                    onUrlChange = { url = it },
                    onAddClick = {
                        isLoading = true
                        foundMints = listOf()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            } else {
                MintRatingItem(
                    mintName = selectedMint!!.name,
                    mintUrl = selectedMint!!.url,
                    rating = selectedMint!!.rating,
                    onClick = {},
                    selected = true,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // Loading spinner or found mints
            if (isLoading) {
                Spacer(modifier = Modifier.height(32.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colorScheme.primary)
                }
            } else if (foundMints.isNotEmpty() && selectedMint == null) {
                Spacer(modifier = Modifier.height(32.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    foundMints.forEach { mint ->
                        MintRatingItem(
                            mintName = mint.name,
                            mintUrl = mint.url,
                            rating = mint.rating,
                            onClick = {
                                foundMints = foundMints.filter { it != mint }
                                selectedMint = mint
                            },
                            selected = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Finds Mints",
                style = typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .align(Alignment.CenterHorizontally)
                    .clickable(enabled = !isLoading && selectedMint == null) {
                        isLoading = true
                        foundMints = listOf()
                    },
                textAlign = TextAlign.Center
            )
        }
    }
} 