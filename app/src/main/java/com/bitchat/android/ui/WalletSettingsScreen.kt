package com.bitchat.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitchat.android.ui.walletcomponents.MintListItem

data class MintInfo(
    val id: String,
    val name: String,
    val url: String,
    val balance: String
)

@Composable
fun WalletSettingsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    
    // Sample mint data - 3 mints with one selected
    var selectedMintId by remember { mutableStateOf("mint1") }
    val mints = remember {
        listOf(
            MintInfo("mint1", "Cashu Mint 1", "https://mint1.example.com", "0.00234​₿"),
            MintInfo("mint2", "Cashu Mint 2", "https://mint2.example.com", "0.00156​₿"),
            MintInfo("mint3", "Cashu Mint 3", "https://mint3.example.com", "0.00089​₿")
        )
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Navigation row: same as main wallet screen
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
            // Title: 'wallet settings' in lower case
            Text(
                text = "wallet settings",
                style = typography.headlineSmall,
                color = colorScheme.primary,
                modifier = Modifier
                    .padding(start = 0.dp)
                    .align(Alignment.CenterVertically)
            )
            Spacer(modifier = Modifier.weight(1f))
            // Empty space where settings icon would be (to maintain layout balance)
            Spacer(modifier = Modifier.size(36.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // MINTS section
        Text(
            text = "MINTS",
            style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Mint list
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            mints.forEach { mint ->
                MintListItem(
                    mintName = mint.name,
                    mintUrl = mint.url,
                    balance = mint.balance,
                    selected = selectedMintId == mint.id,
                    onDelete = {
                        // Only allow deletion if it's not the selected mint
                        // and there are other mints available
                        if (selectedMintId != mint.id && mints.size > 1) {
                            // Handle mint deletion logic here
                        }
                    },
                    onClick = {
                        // Select this mint
                        selectedMintId = mint.id
                    },
                    enabled = true
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // BACK UP section
        Text(
            text = "BACK UP",
            style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Back up options
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // View Seed Phrase option
            Text(
                text = "View Seed Phrase",
                style = typography.bodyMedium,
                color = colorScheme.primary
            )
            
            // Description
            Text(
                text = "Display wallet recovery information",
                style = typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        // Spacer to push content up
        Spacer(modifier = Modifier.weight(1f))
    }
} 