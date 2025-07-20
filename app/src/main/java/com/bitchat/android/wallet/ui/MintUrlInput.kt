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
import com.bitchat.android.ui.TerminalInputField

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
    // Clean standalone input field using standardized terminal input
    TerminalInputField(
        value = url,
        onValueChange = onUrlChange,
        placeholder = "mint.example.com",
        enabled = enabled,
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
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