package com.bitchat.android.wallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitchat.android.ui.theme.BitchatTheme

/**
 * MintUrlInput component - input field for entering a mint URL with an add (+) button
 * Styled to match TotalBalance and use only theme colors and typography
 */

@Composable
fun MintUrlInput(
    url: String,
    onUrlChange: (String) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            enabled = enabled,
            singleLine = true,
            label = { Text("Mint URL") },
            placeholder = { Text("https://mint.example.com") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(4.dp)
        )
        
        IconButton(
            onClick = onAddClick,
            enabled = enabled && url.isNotBlank(),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add mint URL",
                tint = if (enabled && url.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MintUrlInputPreview() {
    var url by remember { mutableStateOf("") }
    BitchatTheme {
        MintUrlInput(
            url = url,
            onUrlChange = { url = it },
            onAddClick = {},
            enabled = true
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MintUrlInputDarkPreview() {
    var url by remember { mutableStateOf("") }
    BitchatTheme {
        MintUrlInput(
            url = url,
            onUrlChange = { url = it },
            onAddClick = {},
            enabled = true
        )
    }
} 