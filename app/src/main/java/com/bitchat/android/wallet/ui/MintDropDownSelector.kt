package com.bitchat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitchat.android.ui.theme.BitchatTheme

@Composable
fun MintDropDownSelector(
    mintName: String,
    mintUrl: String,
    bitcoinAmount: String,
    onDropdownClick: () -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val borderColor = if (enabled) colorScheme.primary else colorScheme.primary.copy(alpha = 0.5f)
    val textAlpha = if (enabled) 1f else 0.5f
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color = colorScheme.surface, shape = shape)
            .border(width = 0.25.dp, color = borderColor, shape = shape)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mint name and URL
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = mintName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.primary.copy(alpha = textAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = mintUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface.copy(alpha = 0.7f * textAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            // Bitcoin amount and dropdown
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bitcoinAmount,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.primary.copy(alpha = textAlpha),
                    maxLines = 1
                )
                IconButton(
                    onClick = onDropdownClick,
                    enabled = enabled,
                    modifier = Modifier.size(32.dp)
                ) {
                    if (expanded) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropUp,
                            contentDescription = "Collapse mint list",
                            tint = colorScheme.primary.copy(alpha = textAlpha)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Expand mint list",
                            tint = colorScheme.primary.copy(alpha = textAlpha)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MintDropDownSelectorPreview() {
    var expanded by remember { mutableStateOf(false) }
    BitchatTheme {
        MintDropDownSelector(
            mintName = "Cashu Mint",
            mintUrl = "https://mint.example.com",
            bitcoinAmount = "0.005 ₿",
            onDropdownClick = { expanded = !expanded },
            expanded = expanded,
            enabled = true
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MintDropDownSelectorDarkPreview() {
    var expanded by remember { mutableStateOf(false) }
    BitchatTheme {
        MintDropDownSelector(
            mintName = "Cashu Mint",
            mintUrl = "https://mint.example.com",
            bitcoinAmount = "0.005 ₿",
            onDropdownClick = { expanded = !expanded },
            expanded = expanded,
            enabled = true
        )
    }
} 