package com.bitchat.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedPhraseBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    
    // Sample 12-word seed phrase
    val seedWords = listOf(
        "abandon", "ability", "able", "about",
        "above", "absent", "absorb", "abstract",
        "absurd", "abuse", "access", "accident"
    )
    
    var isRevealed by remember { mutableStateOf(false) }
    
    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = {
                isRevealed = false // Reset blur state when closing
                onDismiss()
            },
            containerColor = colorScheme.background,
            contentColor = colorScheme.onBackground,
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Seed Phrase",
                        style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onSurface
                    )
                    
                    IconButton(
                        onClick = {
                            isRevealed = false // Reset blur state when closing
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Warning text
                Text(
                    text = "Write down your seed phrase and store it in a safe place. This is the only way to recover your wallet.",
                    style = typography.bodySmall,
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Seed phrase grid with blur effect
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 0.25.dp,
                            color = colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(16.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (!isRevealed) Modifier.blur(8.dp) else Modifier
                            )
                    ) {
                        itemsIndexed(seedWords) { index, word ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    style = typography.bodySmall,
                                    color = colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    text = word,
                                    style = typography.bodyMedium,
                                    color = colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Reveal/Hide button
                BitchatButton(
                    text = if (isRevealed) "Hide Seed Phrase" else "Reveal Seed Phrase",
                    onClick = { isRevealed = !isRevealed },
                    style = BitchatButtonStyle.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
} 