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
import com.bitchat.android.ui.BitchatButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.*
import androidx.compose.animation.core.*

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

            // Top area: animated transition between MintUrlInput and selected MintRatingItem
            AnimatedContent(
                targetState = selectedMint,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing))
                },
                modifier = Modifier.padding(horizontal = 12.dp),
                label = "top_input_transition"
            ) { selectedMintState ->
                if (selectedMintState == null) {
                    MintUrlInput(
                        url = url,
                        onUrlChange = { url = it },
                        onAddClick = {
                            isLoading = true
                            foundMints = listOf()
                        }
                    )
                } else {
                    MintRatingItem(
                        mintName = selectedMintState.name,
                        mintUrl = selectedMintState.url,
                        rating = selectedMintState.rating,
                        onClick = {},
                        selected = true,
                        onRemove = {
                            foundMints = listOf(selectedMint!!) + foundMints
                            selectedMint = null
                        }
                    )
                }
            }

            // Loading spinner with smooth fade
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(32.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colorScheme.primary)
                    }
                }
            }

            // Found mints list with staggered animations
            AnimatedVisibility(
                visible = foundMints.isNotEmpty() && selectedMint == null && !isLoading,
                enter = fadeIn(animationSpec = tween(250, delayMillis = 50, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(32.dp))
                    foundMints.forEachIndexed { index, mint ->
                        AnimatedListItem(
                            delayMillis = index * 40,
                            content = {
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
                        )
                    }
                }
            }

            // Show 'Finds Mints' with smooth fade
            AnimatedVisibility(
                visible = !isLoading && selectedMint == null,
                enter = fadeIn(animationSpec = tween(250, delayMillis = 100, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing))
            ) {
                Column {
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

            // Show 'Next' button with smooth slide up
            AnimatedVisibility(
                visible = selectedMint != null,
                enter = fadeIn(animationSpec = tween(200, delayMillis = 100, easing = FastOutSlowInEasing)) + 
                slideInVertically(
                    initialOffsetY = { it / 6 },
                    animationSpec = tween(250, delayMillis = 100, easing = FastOutSlowInEasing)
                ),
                exit = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(32.dp))
                    BitchatButton(
                        text = "Next",
                        onClick = { /* TODO: Next action */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedListItem(
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) + 
        slideInVertically(
            initialOffsetY = { it / 8 },
            animationSpec = tween(250, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing))
    ) {
        content()
    }
} 