package com.bitchat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    // Box with green background matching TotalBalance styling
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 0.25.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            enabled = enabled,
            singleLine = true,
            label = { Text("Mint URL", style = MaterialTheme.typography.bodySmall) },
            placeholder = { 
                Text(
                    "https://mint.example.com", 
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                ) 
            },
                                colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            textStyle = MaterialTheme.typography.bodyMedium
        )
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