package com.bitchat.android.wallet.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bitchat.android.wallet.viewmodel.WalletViewModel
import com.bitchat.android.wallet.ui.BitchatButton
import com.bitchat.android.wallet.ui.BitchatButtonStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSettings(
    viewModel: WalletViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            // Wallet Info Section
            item {
                SettingsSection(title = "WALLET INFORMATION") {
                    SettingsCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            InfoRow("Wallet Version", "0.1.0-beta")
                            Spacer(modifier = Modifier.height(8.dp))
                            InfoRow("Protocol Version", "Cashu v1")
                            Spacer(modifier = Modifier.height(8.dp))
                            InfoRow("Active Mints", "${viewModel.mints.value?.size ?: 0}")
                            Spacer(modifier = Modifier.height(8.dp))
                            InfoRow("Total Transactions", "${viewModel.transactions.value?.size ?: 0}")
                        }
                    }
                }
            }
            
            // Security Section
            item {
                SettingsSection(title = "SECURITY") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsItem(
                            icon = Icons.Filled.FileDownload,
                            title = "Export Wallet Data",
                            description = "Export transaction history and mint info",
                            onClick = { showExportDialog = true }
                        )
                        
                        SettingsItem(
                            icon = Icons.Filled.Security,
                            title = "View Seed Phrase",
                            description = "Display wallet recovery information",
                            onClick = { /* TODO: Implement seed phrase display */ }
                        )
                        
                        SettingsItem(
                            icon = Icons.Filled.Warning,
                            title = "Clear Wallet Data",
                            description = "Remove all wallet data (irreversible)",
                            onClick = { showClearDataDialog = true },
                            textColor = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Network Section
            item {
                SettingsSection(title = "NETWORK") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsItem(
                            icon = Icons.Filled.Public,
                            title = "Network Statistics",
                            description = "View network connectivity information",
                            onClick = { /* TODO: Show network stats */ }
                        )
                        
                        SettingsItem(
                            icon = Icons.Filled.Refresh,
                            title = "Sync All Mints",
                            description = "Refresh mint information and keysets",
                            onClick = { viewModel.syncAllMints() }
                        )
                    }
                }
            }
            
            // Development Section
            item {
                SettingsSection(title = "DEVELOPMENT") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsItem(
                            icon = Icons.Filled.BugReport,
                            title = "Debug Mode",
                            description = "Enable detailed logging and debug features",
                            onClick = { /* TODO: Toggle debug mode */ }
                        )
                        
                        SettingsItem(
                            icon = Icons.Filled.Code,
                            title = "Developer Tools",
                            description = "Advanced tools for testing and debugging",
                            onClick = { /* TODO: Open dev tools */ }
                        )
                    }
                }
            }
            
            // About Section
            item {
                SettingsSection(title = "ABOUT") {
                    SettingsCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "bitchat Cashu Wallet",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "A privacy-focused Cashu ecash wallet integrated with bitchat mesh networking.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Built with the Cashu Development Kit (CDK)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    
    // Clear Data Confirmation Dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = {
                Text(
                    text = "Clear Wallet Data?",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "This will permanently delete all wallet data including:\n\n" +
                           "• Transaction history\n" +
                           "• Saved mints\n" +
                           "• Wallet balance\n" +
                           "• Settings\n\n" +
                           "This action cannot be undone!",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                BitchatButton(
                    text = "Clear Data",
                    onClick = {
                        viewModel.clearAllWalletData()
                        showClearDataDialog = false
                    },
                    style = BitchatButtonStyle.Primary
                )
            },
            dismissButton = {
                BitchatButton(
                    text = "Cancel",
                    onClick = { showClearDataDialog = false },
                    style = BitchatButtonStyle.Secondary
                )
            }
        )
    }
    
    // Export Data Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Text(
                    text = "Export Wallet Data",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "This will export your transaction history and mint information to a JSON file. " +
                           "This does NOT include your private keys or wallet secrets.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                BitchatButton(
                    text = "Export",
                    onClick = {
                        viewModel.exportWalletData()
                        showExportDialog = false
                    },
                    style = BitchatButtonStyle.Primary
                )
            },
            dismissButton = {
                BitchatButton(
                    text = "Cancel",
                    onClick = { showExportDialog = false },
                    style = BitchatButtonStyle.Secondary
                )
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(0.25.dp, MaterialTheme.colorScheme.primary)
    ) {
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = textColor
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
