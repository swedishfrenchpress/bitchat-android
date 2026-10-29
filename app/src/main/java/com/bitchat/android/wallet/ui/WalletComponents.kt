package com.bitchat.android.wallet.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R
import com.bitchat.android.ui.AboutCardShape
import com.bitchat.android.ui.AboutHorizontalPadding
import com.bitchat.android.ui.rememberPressScale
import com.bitchat.android.ui.theme.BitchatFontFamily
import com.bitchat.android.ui.theme.BitchatMotion
import com.bitchat.android.ui.theme.LocalBitchatPalette
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.launch

// MARK: - Formatting

/**
 * Amounts as Bitchat shows them elsewhere ("21 sat"), with locale grouping for readability
 * and currency units scaled by their minor digits ("1.25 usd").
 */
internal fun formatAmount(amount: Long, unit: String): String {
    // Only real ISO currencies get minor digits; Android happily invents a 2-digit "SAT".
    val minor = minorDigits[unit.uppercase(Locale.ROOT)]?.takeIf { it > 0 }
    val format = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = minor ?: 0
        maximumFractionDigits = minor ?: 0
    }
    val value = if (minor == null) BigDecimal.valueOf(amount) else BigDecimal.valueOf(amount, minor)
    return "${format.format(value)} $unit"
}

private val minorDigits: Map<String, Int> by lazy {
    Currency.getAvailableCurrencies().associate { it.currencyCode to it.defaultFractionDigits }
}

/** The number alone, for the big amount display where the unit is set separately. */
internal fun formatAmountNumber(amount: Long, unit: String): String = formatAmount(amount, unit).substringBeforeLast(' ')

// MARK: - Haptics

/** Haptic vocabulary: a light tick for keys, a confirm on success, a reject on invalid input. */
internal class WalletHaptics(private val haptic: HapticFeedback) {
    fun tick() = haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
    fun confirm() = haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    fun reject() = haptic.performHapticFeedback(HapticFeedbackType.Reject)
}

@Composable
internal fun rememberWalletHaptics(): WalletHaptics {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { WalletHaptics(haptic) }
}

/** True when the system asks for no animations; shakes and pulses are skipped then. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Copies wallet data marked as sensitive, so Android 13+ hides it from the clipboard preview
 * overlay and keyboard suggestions: a token on the clipboard is spendable money.
 */
// EXTRA_IS_SENSITIVE is a plain string extra; older Android versions simply ignore it.
@android.annotation.SuppressLint("InlinedApi")
internal fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text).apply {
        description.extras = android.os.PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}

// MARK: - Buttons

internal enum class WalletButtonTone { Primary, Neutral, Destructive }

/**
 * The wallet's action button, in Bitchat's pill idiom (see `SheetDestructiveButton`): a tinted
 * fill with a hairline border and an uppercase tracked label, never a solid Material block.
 * While [loading] it keeps its size and swaps the label for a small spinner, so nothing
 * shifts under the person's finger and a second tap cannot land.
 */
@Composable
internal fun WalletButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: WalletButtonTone = WalletButtonTone.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val palette = LocalBitchatPalette.current
    val accent = when (tone) {
        WalletButtonTone.Primary -> colorScheme.primary
        WalletButtonTone.Neutral -> colorScheme.onSurface
        WalletButtonTone.Destructive -> colorScheme.error
    }
    val active = enabled && !loading
    val tint = if (enabled) accent else palette.textTertiary
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, pressedScale = 0.97f)
    Surface(
        onClick = onClick,
        enabled = active,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .scale(scale),
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = if (tone == WalletButtonTone.Primary && enabled) 0.14f else 0.06f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.32f)),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (loading) {
                CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            }
            Text(
                text = text.uppercase(),
                fontFamily = BitchatFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                color = if (loading) Color.Transparent else tint,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A quiet text action, e.g. "Paste" next to a field or "See all" by a section label. */
@Composable
internal fun WalletTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colorScheme = MaterialTheme.colorScheme
    Text(
        text = text,
        fontFamily = BitchatFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = if (enabled) colorScheme.primary else LocalBitchatPalette.current.textTertiary,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

// MARK: - Cards and rows

@Composable
internal fun WalletCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AboutHorizontalPadding),
        color = MaterialTheme.colorScheme.surface,
        shape = AboutCardShape,
    ) {
        Column(content = content)
    }
}

/** Inset to the text column after a 22dp glyph; [plain] rows without a glyph inset 16dp. */
@Composable
internal fun WalletCardDivider(plain: Boolean = false) {
    HorizontalDivider(
        modifier = Modifier.padding(start = if (plain) 16.dp else 54.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Settings-style row matching `SettingsToggleRow`: 22dp glyph, title, subtitle, trailing slot. */
@Composable
internal fun WalletRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    painter: Painter? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                else Modifier
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null || painter != null) {
            val m = Modifier.size(22.dp)
            if (icon != null) Icon(icon, contentDescription = null, tint = iconTint, modifier = m)
            else Icon(painter!!, contentDescription = null, tint = iconTint, modifier = m)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = BitchatFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = titleColor)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontFamily = BitchatFontFamily, fontSize = 12.sp, lineHeight = 17.sp, color = colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Label on the left, value on the right; used by review and detail screens. */
@Composable
internal fun WalletValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 1,
    onCopy: (() -> Unit)? = null,
) {
    val palette = LocalBitchatPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = BitchatFontFamily, fontSize = 13.sp, color = palette.textTertiary)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            fontFamily = BitchatFontFamily,
            fontSize = if (emphasized) 15.sp else 13.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            color = valueColor,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        if (onCopy != null) CopyIcon(onCopy)
    }
}

@Composable
private fun CopyIcon(onCopy: () -> Unit) {
    val haptics = rememberWalletHaptics()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1_500)
            copied = false
        }
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.wallet_copy)) {
                onCopy()
                haptics.tick()
                copied = true
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(copied, transitionSpec = { fadeIn(tween(BitchatMotion.QUICK_MS)) togetherWith fadeOut(tween(BitchatMotion.QUICK_MS)) }, label = "copy") { done ->
            Icon(
                if (done) Icons.Filled.Check else Icons.Filled.ContentCopy,
                contentDescription = stringResource(if (done) R.string.wallet_copied else R.string.wallet_copy),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Inline explainer used for trust, bearer, and lock notes. Tone sets the tint only. */
internal enum class NoticeTone { Info, Positive, Warning, Danger }

@Composable
internal fun WalletNotice(text: String, modifier: Modifier = Modifier, tone: NoticeTone = NoticeTone.Info, icon: ImageVector? = null) {
    val colorScheme = MaterialTheme.colorScheme
    val palette = LocalBitchatPalette.current
    val accent = when (tone) {
        NoticeTone.Info -> colorScheme.onSurfaceVariant
        NoticeTone.Positive -> colorScheme.primary
        NoticeTone.Warning -> palette.accentOrange
        NoticeTone.Danger -> colorScheme.error
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AboutHorizontalPadding)
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp).offset(y = 1.dp))
        Text(text, fontFamily = BitchatFontFamily, fontSize = 12.sp, lineHeight = 17.sp, color = if (tone == NoticeTone.Info) colorScheme.onSurfaceVariant else accent)
    }
}

// MARK: - Amounts

/**
 * The big amount. Shrinks for long numbers instead of wrapping, and is read as one phrase by
 * screen readers ("Balance 12,345 sat"). [shake] drives the invalid-input wiggle.
 */
@Composable
internal fun AmountHero(
    amount: Long,
    unit: String,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    shake: Animatable<Float, *>? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalBitchatPalette.current
    val number = if (hidden) stringResource(R.string.wallet_balance_hidden) else formatAmountNumber(amount, unit)
    val size = when {
        number.length <= 7 -> 44.sp
        number.length <= 10 -> 36.sp
        else -> 28.sp
    }
    val description = contentDescription ?: formatAmount(amount, unit)
    Row(
        modifier = modifier
            .offset { IntOffset((shake?.value ?: 0f).toInt(), 0) }
            .then(if (onClick != null) Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick) else Modifier)
            .clearAndSetSemantics {
                this.contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Shrinks instead of clipping at large system font scales or long amounts.
        androidx.compose.foundation.text.BasicText(
            number,
            style = androidx.compose.ui.text.TextStyle(fontFamily = BitchatFontFamily, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = color),
            maxLines = 1,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = size),
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            " $unit",
            fontFamily = BitchatFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            color = palette.textTertiary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/** A short decaying horizontal wiggle for rejected input. No-op with reduced motion. */
internal suspend fun Animatable<Float, *>.shake(reducedMotion: Boolean) {
    if (reducedMotion) return
    @Suppress("UNCHECKED_CAST")
    val anim = this as Animatable<Float, androidx.compose.animation.core.AnimationVector1D>
    for (offset in listOf(18f, -14f, 10f, -6f, 3f, 0f)) {
        anim.animateTo(offset, tween(durationMillis = 45, easing = FastOutSlowInEasing))
    }
}

/**
 * Numeric keypad. Custom rather than the system keyboard so the amount stays the focus and
 * the layout never jumps. Keys are 64dp tall with scale-only press feedback.
 */
@Composable
internal fun AmountKeypad(onDigit: (Int) -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-1, 0, -2))
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(Modifier.weight(1f).height(64.dp), contentAlignment = Alignment.Center) {
                        when (key) {
                            -1 -> Unit
                            -2 -> KeypadKey(onClick = onDelete, label = stringResource(R.string.wallet_cd_delete)) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                            }
                            else -> KeypadKey(onClick = { onDigit(key) }, label = key.toString()) {
                                Text(key.toString(), fontFamily = BitchatFontFamily, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(onClick: () -> Unit, label: String, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, pressedScale = 0.88f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .scale(scale),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// MARK: - Status panel

internal enum class StatusKind { Loading, Success, Pending, Error }

/**
 * One component for loading, success, pending and error, cross-fading between them so a
 * spinner can turn into a check in place. Success plays a single scale-in and a confirm haptic.
 */
@Composable
internal fun WalletStatusPanel(kind: StatusKind, title: String, body: String?, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val palette = LocalBitchatPalette.current
    val haptics = rememberWalletHaptics()
    LaunchedEffect(kind) {
        when (kind) {
            StatusKind.Success -> haptics.confirm()
            StatusKind.Error -> haptics.reject()
            else -> Unit
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = {
                (fadeIn(tween(BitchatMotion.EMPHASIZED_MS)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.7f)) togetherWith
                    fadeOut(tween(BitchatMotion.QUICK_MS))
            },
            label = "status",
        ) { state ->
            val tint = when (state) {
                StatusKind.Loading -> colorScheme.primary
                StatusKind.Success -> colorScheme.primary
                StatusKind.Pending -> palette.accentOrange
                StatusKind.Error -> colorScheme.error
            }
            Box(
                Modifier.size(64.dp).background(tint.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    StatusKind.Loading -> CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    StatusKind.Success -> Icon(Icons.Filled.Check, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
                    StatusKind.Pending -> Icon(Icons.Filled.Schedule, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
                    StatusKind.Error -> Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            fontFamily = BitchatFontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (!body.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(body, fontFamily = BitchatFontFamily, fontSize = 13.sp, lineHeight = 19.sp, color = colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Neutral empty state: the ecash glyph, a title, one line, nothing alarming. */
@Composable
internal fun WalletEmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_spec_ecash), contentDescription = null, tint = LocalBitchatPalette.current.textTertiary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, fontFamily = BitchatFontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, fontFamily = BitchatFontFamily, fontSize = 13.sp, lineHeight = 19.sp, color = colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// MARK: - Text input

/** Single field styled like the composer: neutral fill and outline from [LocalBitchatPalette]. */
@Composable
internal fun WalletTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
) {
    val palette = LocalBitchatPalette.current
    val colorScheme = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else 6,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = BitchatFontFamily, color = colorScheme.onSurface, fontSize = 13.sp),
        cursorBrush = SolidColor(colorScheme.primary),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AboutHorizontalPadding),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(palette.inputSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, if (isError) colorScheme.error.copy(alpha = 0.6f) else palette.inputOutline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, fontFamily = BitchatFontFamily, fontSize = 13.sp, color = palette.textTertiary)
                }
                inner()
            }
        },
    )
}

// MARK: - QR

/**
 * A QR code on white with a quiet zone, so phones scan it from the dark theme too. Uses
 * error-correction L to fit longer tokens. Callers check [MAX_QR_LENGTH] first; data that
 * still cannot be encoded renders nothing.
 */
@Composable
internal fun WalletQrCode(data: String, contentDescription: String, modifier: Modifier = Modifier, size: Dp = 240.dp) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = remember(data, sizePx) { qrBitmap(data, sizePx) } ?: return
    Box(
        modifier = modifier
            .size(size + 24.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size).aspectRatio(1f))
    }
}

private fun qrBitmap(data: String, sizePx: Int): Bitmap? = runCatching {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 0)
    val matrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}.getOrNull()

/** QR payloads above this are unreliable to scan; we offer copy/share instead. */
internal const val MAX_QR_LENGTH = 1_800

// MARK: - Misc

@Composable
internal fun WalletBackIcon(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .size(44.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = stringResource(R.string.back), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).scale(scale),
        )
    }
}

/** Remembers a scope-launched action guarded against double taps until it completes. */
@Composable
internal fun rememberSingleFlight(): (suspend () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    return { block ->
        if (!running) {
            running = true
            scope.launch {
                try {
                    block()
                } finally {
                    running = false
                }
            }
        }
    }
}
