package com.bitchat.android.ui.walletcomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitchat.android.ui.theme.BitchatTheme

enum class BitchatButtonStyle { Primary, Secondary }

@Composable
fun BitchatButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BitchatButtonStyle = BitchatButtonStyle.Primary,
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(4.dp)
    val textColor = if (enabled) colorScheme.primary else colorScheme.primary.copy(alpha = 0.5f)
    val backgroundColor = colorScheme.surface
    val borderModifier = if (style == BitchatButtonStyle.Primary)
        Modifier.border(0.25.dp, textColor, shape)
    else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color = backgroundColor, shape = shape)
            .then(borderModifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BitchatButtonPrimaryPreview() {
    BitchatTheme {
        BitchatButton(
            text = "Primary Button",
            onClick = {},
            style = BitchatButtonStyle.Primary
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BitchatButtonSecondaryPreview() {
    BitchatTheme {
        BitchatButton(
            text = "Secondary Button",
            onClick = {},
            style = BitchatButtonStyle.Secondary
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BitchatButtonDisabledPreview() {
    BitchatTheme {
        BitchatButton(
            text = "Disabled Button",
            onClick = {},
            style = BitchatButtonStyle.Primary,
            enabled = false
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BitchatButtonSecondaryDisabledPreview() {
    BitchatTheme {
        BitchatButton(
            text = "Secondary Disabled",
            onClick = {},
            style = BitchatButtonStyle.Secondary,
            enabled = false
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BitchatButtonPrimaryDarkPreview() {
    BitchatTheme {
        BitchatButton(
            text = "Primary Button",
            onClick = {},
            style = BitchatButtonStyle.Primary
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BitchatButtonSecondaryDarkPreview() {
    BitchatTheme {
        BitchatButton(
            text = "Secondary Button",
            onClick = {},
            style = BitchatButtonStyle.Secondary
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BitchatButtonPrimaryDisabledDarkPreview() {
    BitchatTheme {
        BitchatButton(
            text = "Disabled Button",
            onClick = {},
            style = BitchatButtonStyle.Primary,
            enabled = false
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BitchatButtonSecondaryDisabledDarkPreview() {
    BitchatTheme {
        BitchatButton(
            text = "Secondary Disabled",
            onClick = {},
            style = BitchatButtonStyle.Secondary,
            enabled = false
        )
    }
} 