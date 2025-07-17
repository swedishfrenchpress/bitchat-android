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
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.sp
import com.bitchat.android.ui.TotalBalance
import com.bitchat.android.ui.TopUpButton
import com.bitchat.android.ui.WithdrawButton

data class MintData(val name: String, val url: String, val rating: Int)

@Composable
fun WalletScreen(onClose: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    var url by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var foundMints by remember { mutableStateOf(listOf<MintData>()) }
    var selectedMint by remember { mutableStateOf<MintData?>(null) }
    var showMainWallet by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTopUp by remember { mutableStateOf(false) }

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
        color = colorScheme.background
    ) {
        AnimatedContent(
            targetState = when {
                showTopUp -> "topup"
                showSettings -> "settings"
                else -> "main"
            },
            transitionSpec = {
                when (targetState) {
                    "topup" -> {
                        // Entering top up: simple fade in (stock animation)
                        fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
                    }
                    "settings" -> {
                        // Entering settings: smooth slide in from right with fade
                        fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) + 
                        slideInHorizontally(
                            initialOffsetX = { it }, 
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) togetherWith
                        fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) + 
                        slideOutHorizontally(
                            targetOffsetX = { -it }, 
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        )
                    }
                    else -> {
                        // Returning to main: check if coming from topup for different animation
                        if (initialState == "topup") {
                            // Exiting top up: slide out to right (consistent with other exits)
                            fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) + 
                            slideOutHorizontally(
                                targetOffsetX = { it }, 
                                animationSpec = tween(200, easing = FastOutSlowInEasing)
                            )
                        } else {
                            // Other transitions: slide in from left with fade
                            fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) + 
                            slideInHorizontally(
                                initialOffsetX = { -it }, 
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) togetherWith
                            fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) + 
                            slideOutHorizontally(
                                targetOffsetX = { it }, 
                                animationSpec = tween(200, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                }
            },
            label = "screen_transition"
        ) { currentScreen ->
            when (currentScreen) {
                "topup" -> {
                    TopUpScreen(
                        onBackClick = { showTopUp = false },
                        onSettingsClick = { 
                            showTopUp = false
                            showSettings = true 
                        }
                    )
                }
                "settings" -> {
                    WalletSettingsScreen(
                        onClose = { showSettings = false }
                    )
                }
                else -> {
                    AnimatedContent(
                        targetState = showMainWallet,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) + 
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) togetherWith
                            fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) + 
                            slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = tween(200, easing = FastOutSlowInEasing)
                            )
                        },
                        label = "wallet_screen_transition"
                    ) { showMain ->
                        if (showMain) {
                            MainWalletContent(
                                onClose = onClose,
                                selectedMint = selectedMint!!,
                                onBack = { showMainWallet = false },
                                onSettings = { showSettings = true },
                                onTopUp = { showTopUp = true }
                            )
                        } else {
                            MintSelectionContent(
                                colorScheme = colorScheme,
                                typography = typography,
                                url = url,
                                onUrlChange = { url = it },
                                isLoading = isLoading,
                                onStartLoading = {
                                    isLoading = true
                                    foundMints = listOf()
                                },
                                foundMints = foundMints,
                                selectedMint = selectedMint,
                                onMintSelected = { mint ->
                                    foundMints = foundMints.filter { it != mint }
                                    selectedMint = mint
                                },
                                onMintRemoved = {
                                    foundMints = listOf(selectedMint!!) + foundMints
                                    selectedMint = null
                                },
                                onNext = { showMainWallet = true },
                                onClose = onClose,
                                onSettings = { showSettings = true }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainWalletContent(
    onClose: () -> Unit,
    selectedMint: MintData,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onTopUp: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

        Column(
        modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.Start
        ) {
        // Navigation row: same as before
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
            // Title: 'bitcoin wallet' in lower case
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
                onClick = onSettings,
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

        Spacer(modifier = Modifier.height(32.dp))

        // TOTAL BALANCE label
        Text(
            text = "TOTAL BALANCE",
            style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Total Balance component
        TotalBalance(
            bitcoinAmount = "0​₿",
            dollarAmount = "$0",
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // Spacer to push actions to bottom
        Spacer(modifier = Modifier.weight(1f))

        // ACTIONS section at bottom for thumb zone
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 32.dp)
        ) {
            // ACTIONS label
            Text(
                text = "ACTIONS",
                style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Top Up and Withdraw buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TopUpButton(
                    onClick = onTopUp,
                    modifier = Modifier.weight(1f)
                )
                WithdrawButton(
                    onClick = { /* TODO: Withdraw action */ },
                    enabled = false, // Disabled since balance is 0
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun MintSelectionContent(
    colorScheme: ColorScheme,
    typography: Typography,
    url: String,
    onUrlChange: (String) -> Unit,
    isLoading: Boolean,
    onStartLoading: () -> Unit,
    foundMints: List<MintData>,
    selectedMint: MintData?,
    onMintSelected: (MintData) -> Unit,
    onMintRemoved: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.Start
    ) {
        // Navigation row: same as before
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
            // Title: 'bitcoin wallet' in lower case
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
                onClick = onSettings,
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
                    onUrlChange = onUrlChange,
                    onAddClick = onStartLoading
                )
            } else {
                MintRatingItem(
                    mintName = selectedMintState.name,
                    mintUrl = selectedMintState.url,
                    rating = selectedMintState.rating,
                    onClick = {},
                    selected = true,
                    onRemove = onMintRemoved
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
                    LoadingSpinner()
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
                                onClick = { onMintSelected(mint) },
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
                            onStartLoading()
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
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
            }
        }
    }
}

@Composable
fun AnimatedListItem(
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

@Composable
fun LoadingSpinner() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading_spinner")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val colorScheme = MaterialTheme.colorScheme
    
    Box(
        modifier = Modifier.size(30.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotationAngle),
            color = colorScheme.primary,
            strokeWidth = 1.5.dp
        )
    }
} 