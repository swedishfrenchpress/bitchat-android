package com.bitchat.android.ui.walletcomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitchat.android.ui.theme.BitchatTheme

@Composable
fun MintListItem(
    mintName: String,
    mintUrl: String,
    balance: String,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val borderColor = if (selected) colorScheme.primary else Color.Transparent
    val borderWidth = if (selected) 0.25.dp else 0.dp
    val backgroundColor = if (selected) colorScheme.surface else colorScheme.background
    val textAlpha = if (enabled) 1f else 0.5f
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color = backgroundColor, shape = shape)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .clickable(enabled = enabled, onClick = onClick)
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
                    style = if (selected) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
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
            // Balance
            Text(
                text = balance,
                style = if (selected) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
                color = colorScheme.primary.copy(alpha = textAlpha),
                modifier = Modifier.padding(end = 12.dp)
            )
            // Trash can icon
            IconButton(
                onClick = onDelete,
                enabled = enabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete mint",
                    tint = colorScheme.primary.copy(alpha = textAlpha),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MintListItemPreview() {
    BitchatTheme {
        Column {
            MintListItem(
                mintName = "My Mint",
                mintUrl = "https://mint.example.com",
                balance = "0.1234 BTC",
                onDelete = {},
                onClick = {},
                enabled = true,
                selected = false
            )
            Spacer(modifier = Modifier.height(16.dp))
            MintListItem(
                mintName = "My Mint",
                mintUrl = "https://mint.example.com",
                balance = "0.1234 BTC",
                onDelete = {},
                onClick = {},
                enabled = true,
                selected = true
            )
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MintListItemDarkPreview() {
    BitchatTheme {
        Column {
            MintListItem(
                mintName = "My Mint",
                mintUrl = "https://mint.example.com",
                balance = "0.1234 BTC",
                onDelete = {},
                onClick = {},
                enabled = true,
                selected = false
            )
            Spacer(modifier = Modifier.height(16.dp))
            MintListItem(
                mintName = "My Mint",
                mintUrl = "https://mint.example.com",
                balance = "0.1234 BTC",
                onDelete = {},
                onClick = {},
                enabled = true,
                selected = true
            )
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Composable
fun MintListItemLightPreview() {
    BitchatTheme {
        MintListItem(
            mintName = "My Mint",
            mintUrl = "https://mint.example.com",
            balance = "0.1234 BTC",
            onDelete = {},
            onClick = {},
            enabled = true,
            selected = false
        )
    }
} 