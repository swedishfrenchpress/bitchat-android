package com.bitchat.android.wallet.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import android.util.Log
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bitchat.android.wallet.viewmodel.WalletViewModel
import com.bitchat.android.wallet.ui.BitchatButton
import com.bitchat.android.wallet.ui.BitchatButtonStyle
import com.bitchat.android.wallet.ui.MintListItem
import com.bitchat.android.wallet.ui.SeedPhraseBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSettings(
    viewModel: WalletViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    
    // State for dialogs
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSeedPhrase by remember { mutableStateOf(false) }
    var showDeleteMintDialog by remember { mutableStateOf(false) }
    var mintToDelete by remember { mutableStateOf<String?>(null) }
    
    // Use ViewModel's dialog state with local state synchronization for reliable recomposition
    val showAddMintDialog by viewModel.showAddMintDialog.observeAsState(false)
    var localAddMintDialog by remember { mutableStateOf(false) }
    
    // Sync ViewModel state with local state to ensure recomposition
    LaunchedEffect(showAddMintDialog) {
        localAddMintDialog = showAddMintDialog
    }
    
    // Ensure local state is reset when ViewModel state is false
    LaunchedEffect(showAddMintDialog) {
        if (!showAddMintDialog) {
            localAddMintDialog = false
        }
    }
    
    // Observe real wallet data
    val mints by viewModel.mints.observeAsState(emptyList())
    val activeMint by viewModel.activeMint.observeAsState()
    val transactions by viewModel.transactions.observeAsState(emptyList())
    val balance by viewModel.balance.observeAsState(0L)
    
    // State for mint balances
    var mintBalances by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    
    // Calculate total balance from all mint balances
    val totalBalance = mintBalances.values.sum()
    
    // Load balances for all mints sequentially to avoid race conditions
    LaunchedEffect(mints) {
        val balances = mutableMapOf<String, Long>()
        for (mint in mints) {
            try {
                // Use a suspend function approach to avoid race conditions
                viewModel.getMintBalance(mint.url,
                    onSuccess = { balance ->
                        balances[mint.url] = balance
                        mintBalances = balances.toMap()
                    },
                    onError = { _ ->
                        balances[mint.url] = 0L
                        mintBalances = balances.toMap()
                    }
                )
                // Add a small delay between mint balance queries to avoid overwhelming CDK
                kotlinx.coroutines.delay(100)
            } catch (e: Exception) {
                Log.w("WalletSettings", "Error loading balance for mint ${mint.url}: ${e.message}")
                balances[mint.url] = 0L
                mintBalances = balances.toMap()
            }
        }
    }
    
    // Helper function to normalize mint URL (add https:// if missing)
    fun normalizeMintUrl(url: String): String {
        return if (url.isNotBlank() && !url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
    }
    
    // Main content with proper composition scope for modals
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // Header Navigation (ChatHeader style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                // Back button - positioned all the way to the left with minimal margin
                Button(
                    onClick = onBackClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-8).dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(16.dp),
                            tint = colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Settings",
                            style = typography.titleMedium,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }
            
            // MINTS section
            Text(
                text = "MINTS",
                style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Real mint list from viewModel
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                if (mints.isEmpty()) {
                    // Empty state
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Add a mint to get started.",
                                color = colorScheme.onSurface.copy(alpha = 0.6f),
                                style = typography.bodyMedium
                            )
                        }
                    }
                } else {
                    mints.forEach { mint ->
                        MintListItem(
                            mintName = mint.info?.name ?: mint.nickname,
                            mintUrl = mint.url,
                            balance = mintBalances[mint.url]?.let { "${it} ₿" } ?: "— ₿",
                            selected = activeMint == mint.url,
                            onDelete = {
                                if (mints.size > 1 && activeMint != mint.url) {
                                    mintToDelete = mint.url
                                    showDeleteMintDialog = true
                                }
                            },
                            onClick = {
                                viewModel.setActiveMint(mint.url)
                            },
                            enabled = true
                        )
                    }
                }
                
                // Add New Mint button - styled to match "All transactions" text
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "+ New mint",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            // Set local state immediately for instant feedback
                            localAddMintDialog = true
                            // Then call ViewModel method
                            viewModel.showAddMintDialog()
                        }
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
            
            // BACK UP section
            Text(
                text = "BACK UP",
                style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // View Seed Phrase option
            Column(
                modifier = Modifier
                    .clickable { showSeedPhrase = true }
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "View Seed Phrase",
                    style = typography.bodyMedium,
                    color = colorScheme.primary
                )
                Text(
                    text = "Display wallet recovery information",
                    style = typography.bodySmall,
                    color = colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // ADVANCED section
            Text(
                text = "ADVANCED",
                style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Advanced options
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Export wallet data
                Column(
                    modifier = Modifier
                        .clickable { showExportDialog = true }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Export Wallet Data",
                        style = typography.bodyMedium,
                        color = colorScheme.primary
                    )
                    Text(
                        text = "Export transaction history and mint info",
                        style = typography.bodySmall,
                        color = colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                // Sync all mints
                Column(
                    modifier = Modifier
                        .clickable { viewModel.syncAllMints() }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Sync All Mints",
                        style = typography.bodyMedium,
                        color = colorScheme.primary
                    )
                    Text(
                        text = "Refresh mint information and keysets",
                        style = typography.bodySmall,
                        color = colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                // Clear wallet data
                Column(
                    modifier = Modifier
                        .clickable { 
                            // Close any open dialogs first
                            if (showAddMintDialog || localAddMintDialog) {
                                localAddMintDialog = false
                                viewModel.hideAddMintDialog()
                            }
                            showClearDataDialog = true 
                        }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Clear Wallet Data",
                        style = typography.bodyMedium,
                        color = colorScheme.error
                    )
                    Text(
                        text = "Remove all wallet data (irreversible)",
                        style = typography.bodySmall,
                        color = colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Spacer to push content up
            Spacer(modifier = Modifier.weight(1f))
        }
        
        // Seed phrase bottom sheet
        SeedPhraseBottomSheet(
            isVisible = showSeedPhrase,
            onDismiss = { showSeedPhrase = false }
        )
        
        // Add Mint URL Input Dialog
        if (localAddMintDialog) {
            ModalBottomSheet(
                onDismissRequest = { 
                    localAddMintDialog = false
                    viewModel.hideAddMintDialog() 
                },
                containerColor = colorScheme.background,
                contentColor = colorScheme.onBackground
            ) {
                var mintUrl by remember { mutableStateOf("") }
                
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
                            text = "Add New Mint",
                            style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.onSurface
                        )
                        
                        IconButton(
                            onClick = { 
                                localAddMintDialog = false
                                viewModel.hideAddMintDialog() 
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
                    
                    // Description
                    Text(
                        text = "Enter the URL of a Cashu mint to add it to your wallet.",
                        style = typography.bodyMedium,
                        color = colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Mint URL Input
                    MintUrlInput(
                        url = mintUrl,
                        onUrlChange = { mintUrl = it },
                        onAddClick = {
                            if (mintUrl.isNotBlank()) {
                                val normalizedUrl = normalizeMintUrl(mintUrl)
                                viewModel.addMint(normalizedUrl, "") // Use empty nickname for now
                                // Note: viewModel.addMint() already calls hideAddMintDialog()
                                localAddMintDialog = false
                                mintUrl = ""
                            }
                        },
                        enabled = true // Always enable the input field itself - internal logic handles add button state
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BitchatButton(
                            text = "Cancel",
                            onClick = { 
                                localAddMintDialog = false
                                viewModel.hideAddMintDialog()
                                mintUrl = ""
                            },
                            style = BitchatButtonStyle.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                        BitchatButton(
                            text = "Add Mint",
                            onClick = {
                                if (mintUrl.isNotBlank()) {
                                    val normalizedUrl = normalizeMintUrl(mintUrl)
                                    viewModel.addMint(normalizedUrl, "") // Use empty nickname for now
                                    // Note: viewModel.addMint() already calls hideAddMintDialog()
                                    localAddMintDialog = false
                                    mintUrl = ""
                                }
                            },
                            style = BitchatButtonStyle.Primary,
                            enabled = mintUrl.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        
        // Clear Data Confirmation Bottom Sheet
        if (showClearDataDialog) {
            ModalBottomSheet(
                onDismissRequest = { 
                    showClearDataDialog = false 
                },
                containerColor = colorScheme.background,
                contentColor = colorScheme.onBackground
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
                            text = "Clear Wallet Data?",
                            style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.onSurface
                        )
                        
                        IconButton(
                            onClick = { showClearDataDialog = false }
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
                    
                    // Warning content
                    Text(
                        text = "This will permanently delete all wallet data including:",
                        style = typography.bodyMedium,
                        color = colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // List of items to be deleted
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "• Transaction history",
                            style = typography.bodySmall,
                            color = colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "• Saved mints",
                            style = typography.bodySmall,
                            color = colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "• Wallet balance",
                            style = typography.bodySmall,
                            color = colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "• Settings",
                            style = typography.bodySmall,
                            color = colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "This action cannot be undone!",
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BitchatButton(
                            text = "Cancel",
                            onClick = { showClearDataDialog = false },
                            style = BitchatButtonStyle.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                        BitchatButton(
                            text = "Clear Data",
                            onClick = {
                                viewModel.clearAllWalletData()
                                showClearDataDialog = false
                            },
                            style = BitchatButtonStyle.Primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        
        // Export Data Bottom Sheet
        if (showExportDialog) {
            ModalBottomSheet(
                onDismissRequest = { showExportDialog = false },
                containerColor = colorScheme.background,
                contentColor = colorScheme.onBackground
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
                            text = "Export Wallet Data",
                            style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.onSurface
                        )
                        
                        IconButton(
                            onClick = { showExportDialog = false }
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
                    
                    // Description
                    Text(
                        text = "This will export your transaction history and mint information to a JSON file.",
                        style = typography.bodyMedium,
                        color = colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Security note
                    Text(
                        text = "This does NOT include your private keys or wallet secrets.",
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BitchatButton(
                            text = "Cancel",
                            onClick = { showExportDialog = false },
                            style = BitchatButtonStyle.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                        BitchatButton(
                            text = "Export",
                            onClick = {
                                viewModel.exportWalletData()
                                showExportDialog = false
                            },
                            style = BitchatButtonStyle.Primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        
        // Delete Mint Confirmation Dialog
        if (showDeleteMintDialog && mintToDelete != null) {
            ModalBottomSheet(
                onDismissRequest = { 
                    showDeleteMintDialog = false
                    mintToDelete = null
                },
                containerColor = colorScheme.background,
                contentColor = colorScheme.onBackground
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
                            text = "Delete Mint?",
                            style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.onSurface
                        )
                        
                        IconButton(
                            onClick = { 
                                showDeleteMintDialog = false
                                mintToDelete = null
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
                    
                    // Warning content
                    Text(
                        text = "Are you sure you want to delete this mint?",
                        style = typography.bodyMedium,
                        color = colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "This will remove the mint from your wallet. Any remaining balance will be lost.",
                        style = typography.bodySmall,
                        color = colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BitchatButton(
                            text = "Cancel",
                            onClick = { 
                                showDeleteMintDialog = false
                                mintToDelete = null
                            },
                            style = BitchatButtonStyle.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                        BitchatButton(
                            text = "Delete",
                            onClick = {
                                mintToDelete?.let { mintUrl ->
                                    viewModel.removeMint(mintUrl)
                                }
                                showDeleteMintDialog = false
                                mintToDelete = null
                            },
                            style = BitchatButtonStyle.Primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}




