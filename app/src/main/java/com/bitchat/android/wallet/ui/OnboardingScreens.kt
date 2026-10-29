package com.bitchat.android.wallet.ui

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R
import com.bitchat.android.ui.AboutHorizontalPadding
import com.bitchat.android.ui.AboutSectionLabel
import com.bitchat.android.ui.theme.BitchatFontFamily
import com.bitchat.android.ui.theme.BitchatMotion
import com.bitchat.android.ui.theme.LocalBitchatPalette
import com.bitchat.android.wallet.WalletService
import com.bitchat.android.wallet.cashu.Bip39
import com.bitchat.android.wallet.cashu.MintUrl
import com.bitchat.android.wallet.engine.CashuWallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// MARK: - Onboarding

@Composable
internal fun OnboardingScreen(vm: WalletViewModel) {
    val colorScheme = MaterialTheme.colorScheme
    WalletPage(bottom = {
        WalletButton(stringResource(R.string.wallet_onboarding_create), onClick = vm::createWallet)
        WalletButton(stringResource(R.string.wallet_onboarding_restore), onClick = vm::startRestore, tone = WalletButtonTone.Neutral)
    }) {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_spec_ecash), contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.wallet_onboarding_title), fontFamily = BitchatFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.wallet_onboarding_body),
                fontFamily = BitchatFontFamily, fontSize = 14.sp, lineHeight = 20.sp,
                color = colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        WalletCard {
            listOf(
                R.string.wallet_onboarding_point_bearer,
                R.string.wallet_onboarding_point_mint,
                R.string.wallet_onboarding_point_words,
                R.string.wallet_onboarding_point_panic,
            ).forEachIndexed { i, res ->
                if (i > 0) WalletCardDivider()
                WalletRow(title = stringResource(res), painter = painterResource(if (i == 3) R.drawable.ic_spec_panic else R.drawable.ic_spec_check))
            }
        }
    }
}

// MARK: - Backup words

/**
 * Show the words, then check three of them by position. Pick from words of the same phrase
 * so the check proves the order was written down, not just the set.
 */
class BackupFlow(val words: List<String>, private val onFinished: () -> Unit) {
    sealed interface Step {
        data class Show(val revealed: Boolean) : Step
        data class Confirm(val round: Int, val position: Int, val choices: List<String>, val wrong: Boolean) : Step
        data object Done : Step
    }

    private val positions = words.indices.shuffled().take(3)
    private val _step = MutableStateFlow<Step>(Step.Show(revealed = false))
    val step: StateFlow<Step> = _step.asStateFlow()

    fun stepBack(): Boolean = if (_step.value is Step.Confirm) {
        _step.value = Step.Show(revealed = true)
        true
    } else false

    fun reveal() {
        _step.value = Step.Show(revealed = true)
    }

    fun written() {
        _step.value = confirmStep(0)
    }

    private fun confirmStep(round: Int): Step.Confirm {
        val position = positions[round]
        val decoys = words.filterIndexed { i, w -> i != position && w != words[position] }.distinct().shuffled().take(3)
        return Step.Confirm(round, position, (decoys + words[position]).shuffled(), wrong = false)
    }

    /** Returns false when the pick was wrong, so the screen can shake. */
    fun pick(word: String): Boolean {
        val current = _step.value as? Step.Confirm ?: return false
        if (word != words[current.position]) {
            _step.value = current.copy(wrong = true)
            return false
        }
        _step.value = if (current.round == positions.lastIndex) Step.Done else confirmStep(current.round + 1)
        return true
    }

    fun finish() = onFinished()
}

@Composable
internal fun BackupScreen(vm: WalletViewModel, flow: BackupFlow) {
    val step by flow.step.collectAsStateWithLifecycle()
    SecureWindow()
    AnimatedContent(step, contentKey = { it::class }, transitionSpec = { fadeIn(tween(BitchatMotion.EMPHASIZED_MS)) togetherWith fadeOut(tween(BitchatMotion.STANDARD_MS)) }, label = "backup") { s ->
        when (s) {
            is BackupFlow.Step.Show -> ShowWords(flow, s.revealed, onLater = vm::skipBackup)
            is BackupFlow.Step.Confirm -> ConfirmWords(flow, s)
            BackupFlow.Step.Done -> WalletPage(centered = true, terminal = true, bottom = { WalletButton(stringResource(R.string.wallet_done), onClick = flow::finish) }) {
                WalletStatusPanel(StatusKind.Success, stringResource(R.string.wallet_confirm_done_title), stringResource(R.string.wallet_confirm_done_body))
            }
        }
    }
}

/**
 * Blocks screenshots, screen recording and the recents thumbnail while recovery words are on
 * screen. The wallet is a bottom sheet, which lives in its own dialog window, so the flag has
 * to go on that window: securing only the activity would leave the words capturable.
 */
@Composable
private fun SecureWindow() {
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    DisposableEffect(view, activity) {
        val windows = listOfNotNull(sheetWindow(view), activity?.window).distinct()
        windows.forEach { it.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        onDispose { windows.forEach { it.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) } }
    }
}

/** The dialog window hosting [view], found by walking up to the dialog's window provider. */
private fun sheetWindow(view: View): Window? {
    var parent: ViewParent? = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }
    return null
}

@Composable
private fun ShowWords(flow: BackupFlow, revealed: Boolean, onLater: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val palette = LocalBitchatPalette.current
    val hiddenDescription = stringResource(R.string.wallet_words_hidden_cd)
    WalletPage(bottom = {
        WalletButton(stringResource(R.string.wallet_words_written), onClick = flow::written, enabled = revealed)
        WalletButton(stringResource(R.string.wallet_words_later), onClick = onLater, tone = WalletButtonTone.Neutral)
    }) {
        Text(
            stringResource(R.string.wallet_words_intro),
            fontFamily = BitchatFontFamily, fontSize = 13.sp, lineHeight = 19.sp, color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AboutHorizontalPadding, vertical = 12.dp),
        )
        Box(contentAlignment = Alignment.Center) {
            WalletCard(
                Modifier
                    .clickable(enabled = !revealed, role = Role.Button, onClick = flow::reveal)
                    .then(if (!revealed) Modifier.semantics { contentDescription = hiddenDescription } else Modifier),
            ) {
                Column(Modifier.padding(16.dp).then(if (revealed) Modifier else Modifier.blur(14.dp))) {
                    flow.words.chunked(2).forEachIndexed { row, pair ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            pair.forEachIndexed { col, word ->
                                val n = row * 2 + col + 1
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Text("%2d".format(n), fontFamily = BitchatFontFamily, fontSize = 13.sp, color = palette.textTertiary)
                                    Spacer(Modifier.width(10.dp))
                                    Text(if (revealed) word else "•••••", fontFamily = BitchatFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
            if (!revealed) {
                Text(
                    stringResource(R.string.wallet_words_reveal).uppercase(),
                    fontFamily = BitchatFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp, color = colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        WalletNotice(stringResource(R.string.wallet_onboarding_point_panic), tone = NoticeTone.Warning)
    }
}

@Composable
private fun ConfirmWords(flow: BackupFlow, step: BackupFlow.Step.Confirm) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberWalletHaptics()
    val reduced = rememberReducedMotion()
    val shake = remember { Animatable(0f) }
    LaunchedEffect(step) { if (step.wrong) shake.shake(reduced) }
    WalletPage {
        Column(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${step.round + 1} / 3", fontFamily = BitchatFontFamily, fontSize = 12.sp, color = LocalBitchatPalette.current.textTertiary)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.wallet_confirm_prompt, step.position + 1),
                fontFamily = BitchatFontFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface,
                modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(shake.value.toInt(), 0) },
            )
        }
        Column(Modifier.padding(horizontal = AboutHorizontalPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            step.choices.forEach { word ->
                WalletButton(word, tone = WalletButtonTone.Neutral, onClick = {
                    if (flow.pick(word)) haptics.tick() else haptics.reject()
                })
            }
        }
        if (step.wrong) {
            Spacer(Modifier.height(12.dp))
            WalletNotice(stringResource(R.string.wallet_confirm_wrong, step.position + 1), tone = NoticeTone.Danger)
        }
    }
}

// MARK: - Restore

class RestoreFlow(
    private val app: Application,
    private val scope: CoroutineScope,
    private val onFinished: () -> Unit,
) {
    sealed interface Step {
        data class Words(val text: String, val problem: Problem?) : Step
        data object Searching : Step
        data class Mints(val mints: List<String>, val foundBackup: Boolean, val input: String, val inputInvalid: Boolean) : Step
        data class Restoring(val mint: String, val index: Int, val total: Int) : Step
        data class Done(val results: List<CashuWallet.RestoreResult>) : Step
    }

    sealed interface Problem {
        data class Count(val count: Int) : Problem
        data class Unknown(val positions: List<Int>) : Problem
        data object Checksum : Problem
    }

    private val _step = MutableStateFlow<Step>(Step.Words("", null))
    val step: StateFlow<Step> = _step.asStateFlow()

    fun updateWords(text: String) {
        _step.value = Step.Words(text.take(400), null)
    }

    fun submitWords() {
        val current = _step.value as? Step.Words ?: return
        val words = Bip39.normalize(current.text)
        val problem = when (val v = Bip39.validate(words)) {
            Bip39.Validation.Valid -> null
            is Bip39.Validation.WrongLength -> Problem.Count(v.count)
            is Bip39.Validation.UnknownWords -> Problem.Unknown(v.positions)
            Bip39.Validation.BadChecksum -> Problem.Checksum
        }
        if (problem != null) {
            _step.value = current.copy(problem = problem)
            return
        }
        WalletService.installRecoveredWords(app, words)
        _step.value = Step.Searching
        scope.launch {
            val backup = runCatching { WalletService.fetchBackup(app, words) }.getOrNull()
            val known = WalletService.wallet(app).state.value.mints.map { it.mint.url }
            val mints = (backup?.mints.orEmpty() + known).distinct()
            _step.value = Step.Mints(mints, foundBackup = backup != null, input = "", inputInvalid = false)
        }
    }

    fun updateInput(text: String) {
        val current = _step.value as? Step.Mints ?: return
        _step.value = current.copy(input = text, inputInvalid = false)
    }

    fun addInput(url: String? = null) {
        val current = _step.value as? Step.Mints ?: return
        val normalized = MintUrl.normalizeOrNull(url ?: current.input)
        _step.value = if (normalized == null) current.copy(inputInvalid = true)
        else current.copy(mints = (current.mints + normalized).distinct(), input = "", inputInvalid = false)
    }

    fun remove(url: String) {
        val current = _step.value as? Step.Mints ?: return
        _step.value = current.copy(mints = current.mints - url)
    }

    fun start() {
        val current = _step.value as? Step.Mints ?: return
        if (current.mints.isEmpty()) return
        scope.launch {
            val wallet = WalletService.wallet(app)
            val results = current.mints.mapIndexed { index, url ->
                _step.value = Step.Restoring(MintUrl.host(url), index + 1, current.mints.size)
                wallet.restore(listOf(url)).single()
            }
            _step.value = Step.Done(results)
        }
    }

    fun finish() = onFinished()
}

@Composable
internal fun RestoreScreen(vm: WalletViewModel, flow: RestoreFlow) {
    val step by flow.step.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme
    SecureWindow()
    AnimatedContent(step, contentKey = { it::class }, transitionSpec = { fadeIn(tween(BitchatMotion.EMPHASIZED_MS)) togetherWith fadeOut(tween(BitchatMotion.STANDARD_MS)) }, label = "restore") { s ->
        when (s) {
            is RestoreFlow.Step.Words -> {
                val count = Bip39.normalize(s.text).size
                WalletPage(bottom = { WalletButton(stringResource(R.string.wallet_continue), onClick = flow::submitWords, enabled = count >= 12) }) {
                    Text(
                        stringResource(R.string.wallet_restore_words_prompt),
                        fontFamily = BitchatFontFamily, fontSize = 13.sp, lineHeight = 19.sp, color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = AboutHorizontalPadding, vertical = 12.dp),
                    )
                    WalletTextField(
                        value = s.text,
                        onValueChange = flow::updateWords,
                        placeholder = stringResource(R.string.wallet_restore_words_hint),
                        minLines = 4,
                        isError = s.problem != null,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    Text(
                        stringResource(R.string.wallet_restore_count, count),
                        fontFamily = BitchatFontFamily, fontSize = 12.sp, color = LocalBitchatPalette.current.textTertiary,
                        modifier = Modifier.padding(horizontal = AboutHorizontalPadding + 4.dp, vertical = 8.dp),
                    )
                    val problem = when (val p = s.problem) {
                        null -> null
                        is RestoreFlow.Problem.Count -> stringResource(R.string.wallet_restore_count, p.count)
                        is RestoreFlow.Problem.Unknown -> stringResource(R.string.wallet_restore_unknown_words, p.positions.joinToString(", "))
                        RestoreFlow.Problem.Checksum -> stringResource(R.string.wallet_restore_bad_checksum)
                    }
                    if (problem != null) WalletNotice(problem, tone = NoticeTone.Danger)
                }
            }
            RestoreFlow.Step.Searching -> WalletPage(centered = true) {
                WalletStatusPanel(StatusKind.Loading, stringResource(R.string.wallet_restore_searching), null)
            }
            is RestoreFlow.Step.Mints -> WalletPage(bottom = {
                WalletButton(stringResource(R.string.wallet_restore_start), onClick = flow::start, enabled = s.mints.isNotEmpty())
            }) {
                WalletNotice(
                    stringResource(if (s.foundBackup) R.string.wallet_restore_backup_found else R.string.wallet_restore_backup_none),
                    tone = if (s.foundBackup) NoticeTone.Positive else NoticeTone.Info,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.wallet_restore_mints_body),
                    fontFamily = BitchatFontFamily, fontSize = 13.sp, lineHeight = 19.sp, color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AboutHorizontalPadding, vertical = 8.dp),
                )
                if (s.mints.isNotEmpty()) {
                    AboutSectionLabel(stringResource(R.string.wallet_restore_mints_title))
                    WalletCard {
                        s.mints.forEachIndexed { i, url ->
                            if (i > 0) WalletCardDivider()
                            WalletRow(
                                title = MintUrl.host(url),
                                painter = painterResource(R.drawable.ic_spec_ecash),
                                trailing = {
                                    Box(
                                        Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClickLabel = stringResource(R.string.wallet_mint_remove_confirm)) { flow.remove(url) },
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.wallet_mint_remove_confirm), tint = LocalBitchatPalette.current.textTertiary, modifier = Modifier.size(16.dp)) }
                                },
                            )
                        }
                    }
                }
                AboutSectionLabel(stringResource(R.string.wallet_add_mint))
                WalletTextField(
                    value = s.input,
                    onValueChange = flow::updateInput,
                    placeholder = stringResource(R.string.wallet_add_mint_hint),
                    singleLine = true,
                    isError = s.inputInvalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    WalletTextAction(stringResource(R.string.wallet_add_mint), onClick = { flow.addInput() }, enabled = s.input.isNotBlank())
                    if (WalletService.suggestTestMint && WalletService.TEST_MINT_URL !in s.mints) {
                        WalletTextAction(stringResource(R.string.wallet_add_mint_suggest_test), onClick = { flow.addInput(WalletService.TEST_MINT_URL) })
                    }
                }
                if (s.inputInvalid) WalletNotice(stringResource(R.string.wallet_error_mint_not_found_body), tone = NoticeTone.Danger)
            }
            is RestoreFlow.Step.Restoring -> WalletPage(centered = true) {
                WalletStatusPanel(StatusKind.Loading, stringResource(R.string.wallet_restore_progress, s.mint), "${s.index} / ${s.total}")
            }
            is RestoreFlow.Step.Done -> {
                val totals = s.results.flatMap { it.recovered.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
                val failed = s.results.filter { it.error != null }
                WalletPage(centered = true, terminal = true, bottom = { WalletButton(stringResource(R.string.wallet_done), onClick = flow::finish) }) {
                    WalletStatusPanel(
                        if (totals.isEmpty()) StatusKind.Pending else StatusKind.Success,
                        stringResource(R.string.wallet_restore_done_title),
                        if (totals.isEmpty()) stringResource(R.string.wallet_restore_done_nothing)
                        else stringResource(R.string.wallet_restore_done_found, totals.entries.joinToString(" · ") { formatAmount(it.value, it.key) }),
                    )
                    failed.forEach { result ->
                        WalletNotice(stringResource(R.string.wallet_restore_mint_failed, MintUrl.host(result.mintUrl)), tone = NoticeTone.Warning)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
