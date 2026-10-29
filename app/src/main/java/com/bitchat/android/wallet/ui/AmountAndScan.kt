package com.bitchat.android.wallet.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R
import com.bitchat.android.ui.AboutHorizontalPadding
import com.bitchat.android.ui.ScannerView
import com.bitchat.android.ui.theme.BitchatFontFamily
import com.bitchat.android.ui.theme.LocalBitchatPalette
import com.bitchat.android.wallet.engine.MintBalance
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

/** Digit buffer behind the keypad. Leading zeros are dropped; 12 digits is plenty. */
data class AmountDigits(val digits: String = "") {
    val value: Long get() = digits.toLongOrNull() ?: 0L

    fun push(d: Int): AmountDigits? {
        if (digits.isEmpty() && d == 0) return null
        if (digits.length >= 12) return null
        return AmountDigits(digits + d)
    }

    fun pop(): AmountDigits? = if (digits.isEmpty()) null else AmountDigits(digits.dropLast(1))

    companion object {
        fun of(value: Long) = AmountDigits(if (value > 0) value.toString() else "")
    }
}

/**
 * Amount step shared by every flow: the big amount, a context line that doubles as the error
 * line (never a dialog), the mint picker, and the keypad. Invalid keys shake and buzz.
 */
@Composable
internal fun AmountStep(
    amount: AmountDigits,
    unit: String,
    contextLine: String?,
    contextIsError: Boolean,
    mints: List<MintBalance>,
    selectedMint: String?,
    onMint: (String) -> Unit,
    onChange: (AmountDigits) -> Unit,
    continueLabel: String,
    onContinue: () -> Unit,
    continueEnabled: Boolean,
    loading: Boolean = false,
    maxAmount: Long? = null,
) {
    val haptics = rememberWalletHaptics()
    val reduced = rememberReducedMotion()
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val palette = LocalBitchatPalette.current
    val colorScheme = MaterialTheme.colorScheme
    fun reject() {
        haptics.reject()
        scope.launch { shake.shake(reduced) }
    }
    // Not a scrolling page: the amount floats in the space above, and the keypad sits right
    // above the button where the thumb already is.
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Spacer(Modifier.height(12.dp))
        MintPicker(mints, selectedMint, unit, onMint)
        Spacer(Modifier.weight(1f))
        AmountHero(amount.value, unit, shake = shake, modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (contextLine != null) {
                    Text(
                        contextLine,
                        fontFamily = BitchatFontFamily, fontSize = 12.sp, textAlign = TextAlign.Center,
                        color = if (contextIsError) colorScheme.error else palette.textTertiary,
                    )
                }
                // The max pill only appears once the entry passes the balance; it lives in the
                // context line, so nothing reflows when it shows.
                if (maxAmount != null && maxAmount > 0 && amount.value > maxAmount) {
                    WalletTextAction(stringResource(R.string.wallet_max), onClick = { onChange(AmountDigits.of(maxAmount)); haptics.tick() })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        AmountKeypad(
            onDigit = { d -> amount.push(d)?.let { onChange(it); haptics.tick() } ?: reject() },
            onDelete = { amount.pop()?.let { onChange(it); haptics.tick() } ?: reject() },
        )
        Spacer(Modifier.height(8.dp))
        WalletButton(
            continueLabel, onClick = onContinue, enabled = continueEnabled, loading = loading,
            modifier = Modifier.padding(horizontal = AboutHorizontalPadding),
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun MintPicker(mints: List<MintBalance>, selected: String?, unit: String, onSelect: (String) -> Unit) {
    if (mints.isEmpty()) return
    val current = mints.firstOrNull { it.mint.url == selected } ?: mints.first()
    var open by remember { mutableStateOf(false) }
    val palette = LocalBitchatPalette.current
    val label = current.mint.displayName + " · " + formatAmount(current.available[unit] ?: 0, unit)
    val description = stringResource(R.string.wallet_cd_choose_mint, current.mint.displayName)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, palette.inputOutline, RoundedCornerShape(20.dp))
                .clickable(enabled = mints.size > 1, role = Role.Button) { open = true }
                .semantics { contentDescription = description }
                .heightIn(min = 40.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_spec_ecash), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.padding(start = 6.dp))
            Text(label, fontFamily = BitchatFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            if (mints.size > 1) Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = palette.textTertiary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            mints.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.mint.displayName + " · " + formatAmount(m.available[unit] ?: 0, unit), fontFamily = BitchatFontFamily, fontSize = 13.sp) },
                    onClick = { open = false; onSelect(m.mint.url) },
                )
            }
        }
    }
}

/**
 * Text entry with Paste and Scan. Paste is only offered when the clipboard holds text, and the
 * clipboard is read only when Paste is tapped, so Android never shows a "pasted" toast for a
 * glance the person did not ask for.
 */
@Composable
internal fun PasteOrScanField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean,
    onScan: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val hasText = remember { clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true }
    WalletTextField(value, onValueChange, placeholder, minLines = 3, isError = isError)
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (hasText) {
            WalletTextAction(stringResource(R.string.wallet_paste), onClick = {
                clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.let(onValueChange)
            })
        }
        WalletTextAction(stringResource(R.string.wallet_scan), onClick = onScan)
    }
}

/** Camera viewfinder reusing Bitchat's QR scanner, behind the camera permission. */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun WalletScanner(onResult: (String) -> Unit) {
    val permission = rememberPermissionState(android.Manifest.permission.CAMERA)
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberWalletHaptics()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AboutHorizontalPadding)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(if (permission.status.isGranted) Color.Black else colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (permission.status.isGranted) {
            ScannerView(onScan = { text -> haptics.confirm(); onResult(text) })
            Box(Modifier.size(220.dp).border(2.dp, colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(16.dp)))
            Text(
                stringResource(R.string.wallet_scan_prompt),
                fontFamily = BitchatFontFamily, fontSize = 12.sp, color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.wallet_camera_permission),
                    fontFamily = BitchatFontFamily, fontSize = 13.sp, textAlign = TextAlign.Center, color = colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                WalletButton(stringResource(R.string.wallet_camera_allow), onClick = { permission.launchPermissionRequest() })
            }
        }
    }
}
