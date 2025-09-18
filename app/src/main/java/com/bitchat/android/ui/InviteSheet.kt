package com.bitchat.android.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Apple Pay inspired invite sheet for sharing channel invites via NFC
 * Matches Bitchat's design language while providing a premium experience
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteSheet(
    isPresented: Boolean,
    channelName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    
    // Generate one-time invite code (6 characters, alphanumeric)
    val inviteCode = remember(channelName) {
        generateInviteCode()
    }
    
    // Animation states
    val cardScale by animateFloatAsState(
        targetValue = if (isPresented) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardScale"
    )
    
    val cardAlpha by animateFloatAsState(
        targetValue = if (isPresented) 1f else 0f,
        animationSpec = tween(300),
        label = "cardAlpha"
    )
    
    // NFC pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "nfcPulse")
    val nfcPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nfcPulse"
    )
    
    // Trigger haptic feedback when sheet appears
    LaunchedEffect(isPresented) {
        if (isPresented) {
            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }
    
    if (isPresented) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            modifier = modifier.statusBarsPadding(),
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = null,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .alpha(cardAlpha)
                    .scale(cardScale),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header
                Text(
                    text = "Share Channel",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                // Invite Card
                InviteCard(
                    channelName = channelName,
                    inviteCode = inviteCode,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // NFC Instructions with animated pulse
                NFCInstructions(
                    pulseProgress = nfcPulse,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Close button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Close",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun InviteCard(
    channelName: String,
    inviteCode: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f
    
    // Create gradient colors based on theme
    val gradientColors = if (isDark) {
        listOf(
            Color(0xFF2D2D2D),
            Color(0xFF1A1A1A),
            Color(0xFF0D0D0D)
        )
    } else {
        listOf(
            Color(0xFFF8F8F8),
            Color(0xFFEEEEEE),
            Color(0xFFE0E0E0)
        )
    }
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(gradientColors),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Channel Avatar
                ChannelAvatar(
                    channelName = channelName,
                    modifier = Modifier.size(64.dp)
                )
                
                // Channel Name
                Text(
                    text = "#$channelName",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Invite Code Label
                Text(
                    text = "One time invite code",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                // Invite Code Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = inviteCode,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelAvatar(
    channelName: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    
    // Generate consistent color based on channel name
    val avatarColor = remember(channelName) {
        val hash = channelName.hashCode()
        val hue = (hash % 360).let { if (it < 0) it + 360 else it }.toFloat()
        Color.hsv(hue, 0.6f, 0.8f)
    }
    
    // Get first character for avatar
    val initial = channelName.firstOrNull()?.uppercase() ?: "#"
    
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(avatarColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun NFCInstructions(
    pulseProgress: Float,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // NFC Icon with pulse animation
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulse rings
            repeat(3) { index ->
                val delay = index * 0.3f
                val adjustedProgress = ((pulseProgress - delay).coerceIn(0f, 1f))
                val alpha = (1f - adjustedProgress) * 0.3f
                val scale = 1f + adjustedProgress * 2f
                
                if (adjustedProgress > 0f) {
                    Canvas(
                        modifier = Modifier
                            .size(60.dp)
                            .scale(scale)
                            .alpha(alpha)
                    ) {
                        drawCircle(
                            color = colorScheme.primary,
                            radius = size.minDimension / 2,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
            
            // NFC Icon
            NFCIcon(
                modifier = Modifier.size(48.dp),
                color = colorScheme.primary
            )
        }
        
        // Instructions
        Text(
            text = "Tap the other user's Bitchat app with NFC\nto have them join this channel",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        
        // Feature flag notice (temporary)  
        if (true) { // TODO: Replace with BuildConfig.DEBUG when available
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.secondary.copy(alpha = 0.1f),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = "NFC handoff: Coming soon",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun NFCIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Black
) {
    Canvas(modifier = modifier) {
        drawNFCIcon(color)
    }
}

private fun DrawScope.drawNFCIcon(color: Color) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val radius = size.minDimension / 3
    val strokeWidth = size.minDimension / 12
    
    // Draw NFC symbol (stylized radio waves)
    for (i in 0..2) {
        val currentRadius = radius + (i * radius / 3)
        val startAngle = -45f
        val sweepAngle = 90f
        
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth),
            topLeft = androidx.compose.ui.geometry.Offset(
                centerX - currentRadius,
                centerY - currentRadius
            ),
            size = androidx.compose.ui.geometry.Size(currentRadius * 2, currentRadius * 2)
        )
    }
    
    // Center dot
    drawCircle(
        color = color,
        radius = strokeWidth / 2,
        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
    )
}

private fun generateInviteCode(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..6)
        .map { chars[Random.nextInt(chars.length)] }
        .joinToString("")
}

// Stub for NFC functionality behind feature flag
object NFCInviteManager {
    const val NFC_ENABLED = false // Feature flag
    
    fun shareInvite(channelName: String, inviteCode: String): Boolean {
        // TODO: Implement NFC handoff
        return if (NFC_ENABLED) {
            // Real NFC implementation would go here
            false
        } else {
            // Stub implementation
            false
        }
    }
    
    fun isNFCAvailable(): Boolean {
        return NFC_ENABLED && false // TODO: Check device NFC capability
    }
}
