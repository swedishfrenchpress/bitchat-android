package com.bitchat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bitchat.android.wallet.data.Mint
import com.bitchat.android.wallet.viewmodel.WalletViewModel
import com.bitchat.android.ui.walletcomponents.MintListItem
import com.bitchat.android.ui.walletcomponents.MintUrlInput
import com.bitchat.android.ui.walletcomponents.BitchatButton
import com.bitchat.android.ui.walletcomponents.BitchatButtonStyle
import java.text.SimpleDateFormat
import java.util.*

/**
 * Mints management screen
 */
@Composable
fun MintsScreen(
    viewModel: WalletViewModel,
    modifier: Modifier = Modifier
) {
    val mints by viewModel.mints.observeAsState(emptyList())
    val activeMint by viewModel.activeMint.observeAsState()
    val showAddMintDialog by viewModel.showAddMintDialog.observeAsState(false)
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.errorMessage.observeAsState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header Section
        Text(
            text = "MINTS",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (mints.isEmpty()) {
            EmptyMintsCard(onAddClick = { viewModel.showAddMintDialog() })
        } else {
            // Add mint URL input at the top
            MintUrlInput(
                url = "",
                onUrlChange = { /* Handle URL change */ },
                onAddClick = { viewModel.showAddMintDialog() },
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            MintsList(
                mints = mints,
                activeMint = activeMint,
                onMintSelect = { viewModel.setActiveMint(it) },
                onMintDelete = { mintUrl ->
                    // TODO: Implement mint deletion functionality
                    // viewModel.removeMint(mintUrl) // Method doesn't exist yet
                }
            )
        }
        
        // Error message
        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(16.dp))
            ErrorCard(
                message = message,
                onDismiss = { viewModel.clearError() }
            )
        }
    }
    
    // Add mint dialog
    if (showAddMintDialog) {
        AddMintDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.hideAddMintDialog() }
        )
    }
}

@Composable
private fun MintsList(
    mints: List<Mint>,
    activeMint: String?,
    onMintSelect: (String) -> Unit,
    onMintDelete: (String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(mints) { mint ->
            MintListItem(
                mintName = mint.nickname,
                mintUrl = mint.url,
                balance = "0​₿", // TODO: Get actual balance for this mint
                selected = mint.url == activeMint,
                onDelete = { onMintDelete(mint.url) },
                onClick = { onMintSelect(mint.url) },
                enabled = true
            )
        }
    }
}

@Composable
private fun EmptyMintsCard(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountBalance,
                    contentDescription = "No mints",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No mints added yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Add a mint to start using ecash",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                BitchatButton(
                    text = "Add first mint",
                    onClick = onAddClick,
                    style = BitchatButtonStyle.Primary
                )
            }
        }
    }
}

@Composable
private fun AddMintDialog(
    viewModel: WalletViewModel,
    onDismiss: () -> Unit
) {
    var mintUrl by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    val isLoading by viewModel.isLoading.observeAsState(false)
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Mint",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // URL input
                OutlinedTextField(
                    value = mintUrl,
                    onValueChange = { mintUrl = it },
                    label = {
                        Text(
                            text = "Mint URL",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    placeholder = {
                        Text(
                            text = "https://mint.example.com",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Nickname input
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = {
                        Text(
                            text = "Nickname (optional)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Add button
                BitchatButton(
                    text = if (isLoading) "Adding..." else "Add Mint",
                    onClick = {
                        if (mintUrl.isNotEmpty()) {
                            viewModel.addMint(mintUrl, nickname)
                        }
                    },
                    enabled = !isLoading && mintUrl.isNotEmpty(),
                    style = BitchatButtonStyle.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun EditMintDialog(
    currentNickname: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nickname by remember { mutableStateOf(currentNickname) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Edit Mint Nickname",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = {
                        Text(
                            text = "Nickname",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Cancel button
                    BitchatButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        style = BitchatButtonStyle.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Save button
                    BitchatButton(
                        text = "Save",
                        onClick = { onSave(nickname) },
                        style = BitchatButtonStyle.Primary,
                        enabled = nickname.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Utility function
private fun formatDate(date: Date): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
}
