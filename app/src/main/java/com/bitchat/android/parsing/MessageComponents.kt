package com.bitchat.android.parsing

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import kotlinx.coroutines.delay

/**
 * Composable components for rendering parsed message elements
 */

/**
 * Render a list of message elements with proper inline layout
 */
@Composable
fun ParsedMessageContent(
    elements: List<MessageElement>,
    modifier: Modifier = Modifier,
    onCashuPaymentClick: ((ParsedCashuToken) -> Unit)? = null,
    redeemedTokens: Set<String> = emptySet(),
    onRedeemClick: ((ParsedCashuToken) -> Unit)? = null
) {
    Log.d("ParsedMessageContent", "ParsedMessageContent called with ${elements.size} elements")
    elements.forEachIndexed { index, element ->
        when (element) {
            is MessageElement.Text -> Log.d("ParsedMessageContent", "Element $index: Text('${element.content.take(50)}...')")
            is MessageElement.CashuPayment -> Log.d("ParsedMessageContent", "Element $index: CashuPayment(${element.token.amount} ${element.token.unit})")
        }
    }
    
    // Use a Column with proper text and special element rendering
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var currentTextRow = mutableListOf<MessageElement>()
        
        for (element in elements) {
            when (element) {
                is MessageElement.Text -> {
                    // Add text to current row
                    currentTextRow.add(element)
                }
                is MessageElement.CashuPayment -> {
                    Log.d("ParsedMessageContent", "Found CashuPayment element: ${element.token.amount} ${element.token.unit}")
                    // Flush any accumulated text first
                    if (currentTextRow.isNotEmpty()) {
                        TextRow(elements = currentTextRow.toList())
                        currentTextRow.clear()
                    }
                    
                    // Show the payment chip on its own row
                    Log.d("ParsedMessageContent", "About to render CashuPaymentChip")
                    CashuPaymentChip(
                        token = element.token,
                        onPaymentClick = onCashuPaymentClick,
                        isRedeemed = redeemedTokens.contains(element.token.originalString),
                        onRedeemClick = onRedeemClick,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    Log.d("ParsedMessageContent", "CashuPaymentChip rendered")
                }
            }
        }
        
        // Flush any remaining text
        if (currentTextRow.isNotEmpty()) {
            TextRow(elements = currentTextRow.toList())
        }
    }
}

/**
 * Render a row of text elements
 */
@Composable
fun TextRow(elements: List<MessageElement>) {
    Row(
        modifier = Modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        elements.forEach { element ->
            when (element) {
                is MessageElement.Text -> {
                    Text(
                        text = element.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                else -> { /* Skip non-text elements */ }
            }
        }
    }
}

/**
 * Chip component for displaying Cashu payments inline pill
 */
@Composable
fun CashuPaymentChip(
    token: ParsedCashuToken,
    modifier: Modifier = Modifier,
    onPaymentClick: ((ParsedCashuToken) -> Unit)? = null,
    isRedeemed: Boolean = false,
    onRedeemClick: ((ParsedCashuToken) -> Unit)? = null
) {
    Log.d("CashuPaymentChip", "Rendering CashuPaymentChip for token: ${token.amount} ${token.unit}")
    Log.d("CashuPaymentChip", "Token memo: ${token.memo}")
    Log.d("CashuPaymentChip", "Is redeemed: $isRedeemed")
    
    // Animation states
    var isAnimating by remember { mutableStateOf(false) }
    var showSuccessAnimation by remember { mutableStateOf(false) }
    
    // Track redemption state changes for animations
    LaunchedEffect(isRedeemed) {
        if (isRedeemed) {
            showSuccessAnimation = true
            isAnimating = true
            delay(1000) // Show success animation for 1 second
            showSuccessAnimation = false
            isAnimating = false
        }
    }
    
    // Animation values
    val animatedScale by animateFloatAsState(
        targetValue = if (isAnimating) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale_animation"
    )
    
    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            showSuccessAnimation -> MaterialTheme.colorScheme.primary // Theme primary during success
            isRedeemed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f) // Subtle border when redeemed
            else -> MaterialTheme.colorScheme.primary // Theme primary when active
        },
        animationSpec = tween(
            durationMillis = if (showSuccessAnimation) 300 else 800,
            easing = EaseInOutCubic
        ),
        label = "border_color_animation"
    )
    
    val animatedBackgroundColor by animateColorAsState(
        targetValue = when {
            showSuccessAnimation -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) // Subtle primary tint during success
            isRedeemed -> MaterialTheme.colorScheme.surface.copy(alpha = 0.7f) // Dimmed surface when redeemed
            else -> MaterialTheme.colorScheme.surface // Theme surface when active
        },
        animationSpec = tween(
            durationMillis = if (showSuccessAnimation) 300 else 800,
            easing = EaseInOutCubic
        ),
        label = "background_color_animation"
    )
    
    Card(
        modifier = modifier
            .scale(animatedScale)
            .then(
                if (!isRedeemed) {
                    Modifier.clickable { 
                        onRedeemClick?.invoke(token) ?: onPaymentClick?.invoke(token) ?: handleCashuPayment(token)
                    }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = animatedBackgroundColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.25.dp, 
            animatedBorderColor
        )
    ) {
        // Define all animated colors using MaterialTheme design tokens
        val animatedTextColor by animateColorAsState(
            targetValue = when {
                showSuccessAnimation -> MaterialTheme.colorScheme.primary // Theme primary during success
                isRedeemed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) // Disabled text color
                else -> MaterialTheme.colorScheme.primary // Theme primary when active
            },
            animationSpec = tween(
                durationMillis = if (showSuccessAnimation) 300 else 800,
                easing = EaseInOutCubic
            ),
            label = "text_color_animation"
        )
        
        val animatedMemoColor by animateColorAsState(
            targetValue = when {
                showSuccessAnimation -> MaterialTheme.colorScheme.secondary // Theme secondary during success
                isRedeemed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) // More disabled for memo
                else -> MaterialTheme.colorScheme.secondary // Theme secondary when active
            },
            animationSpec = tween(
                durationMillis = if (showSuccessAnimation) 300 else 800,
                easing = EaseInOutCubic
            ),
            label = "memo_color_animation"
        )
        
        val animatedButtonColor by animateColorAsState(
            targetValue = when {
                showSuccessAnimation -> MaterialTheme.colorScheme.primary // Theme primary during success
                isRedeemed -> MaterialTheme.colorScheme.surface // Theme surface when disabled
                else -> MaterialTheme.colorScheme.primary // Theme primary when active
            },
            animationSpec = tween(
                durationMillis = if (showSuccessAnimation) 300 else 800,
                easing = EaseInOutCubic
            ),
            label = "button_color_animation"
        )
        
        val animatedButtonTextColor by animateColorAsState(
            targetValue = when {
                showSuccessAnimation -> MaterialTheme.colorScheme.onPrimary // Theme on-primary during success
                isRedeemed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) // Disabled text
                else -> MaterialTheme.colorScheme.onPrimary // Theme on-primary when active
            },
            animationSpec = tween(
                durationMillis = if (showSuccessAnimation) 300 else 800,
                easing = EaseInOutCubic
            ),
            label = "button_text_color_animation"
        )
        
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side - Payment info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Top row: Bitcoin icon and "bitcoin" text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Bitcoin circle with symbol inside using theme colors
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                MaterialTheme.colorScheme.secondary, 
                                shape = androidx.compose.foundation.shape.CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "₿",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                                lineHeight = MaterialTheme.typography.labelSmall.fontSize * 0.9f
                            ),
                            color = MaterialTheme.colorScheme.onSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    // "bitcoin" text
                    Text(
                        text = "bitcoin",
                        style = MaterialTheme.typography.titleMedium,
                        color = animatedTextColor
                    )
                }
                
                // Middle row: Amount and unit
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${token.amount}",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.3f
                        ),
                        color = animatedTextColor
                    )
                    Text(
                        text = "₿",
                        style = MaterialTheme.typography.titleMedium,
                        color = animatedTextColor
                    )
                }
                
                // Bottom row: Memo (if present)
                if (token.memo?.isNotBlank() == true) {
                    Text(
                        text = "\"${token.memo}\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = animatedMemoColor
                    )
                }
            }
            
            // Right side - Receive/Redeemed button
            Button(
                onClick = { 
                    if (!isRedeemed) {
                        onRedeemClick?.invoke(token) ?: onPaymentClick?.invoke(token) ?: handleCashuPayment(token)
                    }
                },
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = animatedButtonColor,
                    contentColor = animatedButtonTextColor
                ),
                shape = RoundedCornerShape(8.dp),
                enabled = !isRedeemed
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Download/Receive icon or Check icon for redeemed
                    Icon(
                        imageVector = if (isRedeemed) Icons.Filled.CheckCircle else Icons.Filled.Download,
                        contentDescription = if (isRedeemed) "Redeemed" else "Receive",
                        tint = animatedButtonTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Text(
                        text = if (isRedeemed) "Redeemed" else "Receive",
                        style = MaterialTheme.typography.bodySmall,
                        color = animatedButtonTextColor
                    )
                }
            }
        }
    }
}

/**
 * Handle Cashu payment interaction
 */
private fun handleCashuPayment(token: ParsedCashuToken) {
    Log.d("CashuPayment", "User clicked Cashu payment: ${token.originalString}")
    Log.d("CashuPayment", "Amount: ${token.amount} ${token.unit}")
    Log.d("CashuPayment", "Mint: ${token.mintUrl}")
    if (token.memo != null) {
        Log.d("CashuPayment", "Memo: ${token.memo}")
    }
    Log.d("CashuPayment", "Proofs: ${token.proofCount}")
    
    // TODO: Implement wallet integration
}
