package com.bitchat.android.wallet.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.wallet.viewmodel.WalletViewModel
import kotlinx.coroutines.delay

/**
 * Terminal-style success animation component for wallet operations
 * Matches the app's TTY/terminal aesthetic with minimal, retro styling
 */
@Composable
fun SuccessAnimation(
    animationData: WalletViewModel.SuccessAnimationData,
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    var startExit by remember { mutableStateOf(false) }
    var showCursor by remember { mutableStateOf(false) }
    
    // Control animation timing
    LaunchedEffect(animationData) {
        // Start with fade in
        isVisible = true
        delay(1500) // Show for 1.5 seconds
        // Start exit animation
        startExit = true
        delay(300) // Allow fade out animation to complete
        onAnimationComplete()
    }
    
    // Blinking cursor effect
    LaunchedEffect(isVisible) {
        if (isVisible && !startExit) {
            while (true) {
                showCursor = !showCursor
                delay(500)
            }
        }
    }
    
    // Smooth fade in/out animations
    val alpha by animateFloatAsState(
        targetValue = if (isVisible && !startExit) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (startExit) 300 else 400,
            easing = if (startExit) FastOutLinearInEasing else LinearOutSlowInEasing
        ),
        label = "content_alpha"
    )
    
    if (alpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.graphicsLayer { this.alpha = alpha }
            ) {
                // Terminal-style success indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ASCII-style checkmark
                    Text(
                        text = "[✓]",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    // Blinking cursor
                    if (showCursor) {
                        Text(
                            text = "_",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                
                // Success message in terminal style - dynamic based on animation type
                Text(
                    text = getSuccessMessage(animationData.type),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                
                // Amount in terminal style
                Text(
                    text = formatAmount(animationData.amount, animationData.unit),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                )
                
                // ASCII-style separator
                Text(
                    text = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
                
                // Description in terminal style
                Text(
                    text = animationData.description.uppercase(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Fullscreen failure animation component for wallet operations
 */
@Composable
fun FailureAnimation(
    errorMessage: String,
    operationType: String = "Operation",
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    var startExit by remember { mutableStateOf(false) }
    
    // Control animation timing
    LaunchedEffect(errorMessage) {
        // Start with fade in
        isVisible = true
        delay(3000) // Show for 3 seconds (longer for error message)
        // Start exit animation
        startExit = true
        delay(500) // Allow fade out animation to complete
        onAnimationComplete()
    }
    
    // Shake animation for the error icon
    val infiniteTransition = rememberInfiniteTransition(label = "failure_animation")
    val iconShake by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isVisible && !startExit) 3f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_shake"
    )
    
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isVisible && !startExit) 1.03f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_scale"
    )
    
    // Smooth fade in/out animations
    val contentScale by animateFloatAsState(
        targetValue = if (isVisible && !startExit) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = 0.7f, 
            stiffness = Spring.StiffnessMedium
        ),
        label = "content_scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible && !startExit) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (startExit) 400 else 600,
            easing = if (startExit) FastOutLinearInEasing else LinearOutSlowInEasing
        ),
        label = "content_alpha"
    )
    
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isVisible && !startExit) 0.95f else 0f,
        animationSpec = tween(
            durationMillis = if (startExit) 400 else 600
        ),
        label = "background_alpha"
    )
    
    if (alpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.scale(contentScale)
            ) {
                // Error icon with shake effect
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(120.dp)
                ) {
                    // Outer glow circle (red)
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                Color(0xFFFF4444).copy(alpha = 0.2f),
                                CircleShape
                            )
                    )
                    
                    // Main error circle with shake
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                Color(0xFFFF4444),
                                CircleShape
                            )
                            .scale(iconScale)
                            .graphicsLayer {
                                translationX = iconShake
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Error",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                
                // Error message with smooth fade
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.graphicsLayer { this.alpha = alpha }
                ) {
                    Text(
                        text = "FAILED",
                        color = Color(0xFFFF4444),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    
                    // Operation type
                    Text(
                        text = "$operationType Failed",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    // Error message
                    Text(
                        text = errorMessage,
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
}

/**
 * Get appropriate icon for animation type
 */
private fun getIconForAnimationType(type: WalletViewModel.SuccessAnimationType): ImageVector {
    return when (type) {
        WalletViewModel.SuccessAnimationType.CASHU_RECEIVED -> Icons.Filled.Download
        WalletViewModel.SuccessAnimationType.CASHU_SENT -> Icons.Filled.Upload
        WalletViewModel.SuccessAnimationType.LIGHTNING_RECEIVED -> Icons.Filled.FlashOn
        WalletViewModel.SuccessAnimationType.LIGHTNING_SENT -> Icons.AutoMirrored.Filled.Send
    }
}

/**
 * Get appropriate success message for animation type
 */
private fun getSuccessMessage(type: WalletViewModel.SuccessAnimationType): String {
    return when (type) {
        WalletViewModel.SuccessAnimationType.CASHU_RECEIVED -> "PAYMENT RECEIVED"
        WalletViewModel.SuccessAnimationType.CASHU_SENT -> "PAYMENT SENT"
        WalletViewModel.SuccessAnimationType.LIGHTNING_RECEIVED -> "PAYMENT RECEIVED"
        WalletViewModel.SuccessAnimationType.LIGHTNING_SENT -> "PAYMENT SENT"
    }
}

/**
 * Format amount for display
 */
private fun formatAmount(amount: Long, unit: String): String {
    return when (unit.lowercase()) {
        "sat", "sats" -> {
            when {
                amount >= 100_000_000 -> String.format("%.8f BTC", amount / 100_000_000.0)
                        amount >= 1000 -> String.format("₿%,d", amount)
        else -> "₿$amount"
            }
        }
        else -> "$amount $unit"
    }
} 