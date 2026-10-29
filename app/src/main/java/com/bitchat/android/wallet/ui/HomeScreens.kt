package com.bitchat.android.wallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.R
import com.bitchat.android.ui.AboutSectionLabel
import com.bitchat.android.ui.BitchatBadge
import com.bitchat.android.ui.theme.BitchatFontFamily
import com.bitchat.android.ui.theme.LocalBitchatPalette
import com.bitchat.android.wallet.WalletService
import com.bitchat.android.wallet.engine.HistoryEntry
import com.bitchat.android.wallet.engine.HistoryKind
import com.bitchat.android.wallet.engine.HistoryStatus
import com.bitchat.android.wallet.engine.MintBalance
import com.bitchat.android.wallet.engine.WalletException
import kotlinx.coroutines.launch

// MARK: - Home

@Composable
internal fun HomeScreen(vm: WalletViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val unreachable by vm.unreachable.collectAsStateWithLifecycle()
    val revealed by vm.balanceRevealed.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme
    val palette = LocalBitchatPalette.current
    val context = LocalContext.current
    val hideBalance = state.settings[WalletService.SETTING_HIDE_BALANCE] == "true" && !revealed
    val sat = state.available("sat")
    val otherUnits = state.units.filter { it != "sat" && state.available(it) > 0 }
    val hasFunds = state.mints.any { m -> m.available.values.any { it > 0 } }
    val pendingCount = state.history.count { it.isPending && it.kind != HistoryKind.ECASH_SEND }

    WalletPage {
        // Balance
        Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AmountHero(
                amount = sat,
                unit = "sat",
                hidden = hideBalance,
                contentDescription = if (hideBalance) stringResource(R.string.wallet_cd_balance_hidden)
                else stringResource(R.string.wallet_cd_balance, formatAmount(sat, "sat")),
                onClick = if (state.settings[WalletService.SETTING_HIDE_BALANCE] == "true") vm::toggleBalance else null,
            )
            otherUnits.forEach { unit ->
                Text(
                    if (hideBalance) stringResource(R.string.wallet_balance_hidden) else formatAmount(state.available(unit), unit),
                    fontFamily = BitchatFontFamily, fontSize = 14.sp, color = colorScheme.onSurfaceVariant,
                )
            }
            val pendingOut = state.pendingOut("sat")
            if (pendingOut > 0 || pendingCount > 0) {
                Spacer(Modifier.height(6.dp))
                val parts = listOfNotNull(
                    pendingOut.takeIf { it > 0 && !hideBalance }?.let { stringResource(R.string.wallet_pending_out, formatAmount(it, "sat")) },
                    pendingCount.takeIf { it > 0 }?.let { stringResource(R.string.wallet_pending_in, it) },
                )
                if (parts.isNotEmpty()) {
                    Text(parts.joinToString(" · "), fontFamily = BitchatFontFamily, fontSize = 12.sp, color = palette.accentOrange)
                }
            }
            if (unreachable.isNotEmpty() && state.mints.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                WalletNotice(stringResource(R.string.wallet_offline_banner), tone = NoticeTone.Warning, icon = Icons.Filled.CloudOff, centered = true)
            }
        }

        // Without a mint there is nothing to receive or send: adding one is the only action.
        if (state.mints.isEmpty()) {
            WalletNotice(stringResource(R.string.wallet_no_mints_body), centered = true)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WalletButton(stringResource(R.string.wallet_add_mint), onClick = { vm.startAddMint() })
                if (WalletService.suggestTestMint) {
                    WalletButton(
                        stringResource(R.string.wallet_add_mint_suggest_test),
                        onClick = { vm.startAddMint(WalletService.TEST_MINT_URL) },
                        tone = WalletButtonTone.Neutral,
                    )
                }
            }
            return@WalletPage
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WalletButton(
                stringResource(R.string.wallet_receive), onClick = { vm.push(WalletRoute.ReceiveChoice) },
                modifier = Modifier.weight(1f),
            )
            WalletButton(
                stringResource(R.string.wallet_send), onClick = { vm.push(WalletRoute.SendChoice) },
                modifier = Modifier.weight(1f), enabled = hasFunds,
            )
        }

        if (!vm.backedUp && hasFunds) {
            Spacer(Modifier.height(4.dp))
            WalletCard {
                WalletRow(
                    title = stringResource(R.string.wallet_backup_nudge_title),
                    icon = Icons.Filled.WarningAmber,
                    iconTint = palette.accentOrange,
                    onClick = vm::showBackup,
                    trailing = { Chevron() },
                )
            }
        }

        if (state.history.isNotEmpty()) {
            AboutSectionLabel(stringResource(R.string.wallet_section_recent))
            WalletCard {
                state.history.take(4).forEachIndexed { i, entry ->
                    if (i > 0) WalletCardDivider()
                    HistoryRow(entry, hideBalance) { vm.push(WalletRoute.HistoryDetail(entry.id)) }
                }
                if (state.history.size > 4) {
                    WalletCardDivider()
                    WalletRow(title = stringResource(R.string.wallet_see_all), titleColor = colorScheme.primary, onClick = { vm.push(WalletRoute.History) }, trailing = { Chevron() })
                }
            }
        }

        AboutSectionLabel(stringResource(R.string.wallet_section_mints))
        WalletCard {
            state.mints.forEachIndexed { i, mint ->
                if (i > 0) WalletCardDivider()
                MintRow(mint, hideBalance, unreachable = mint.mint.url in unreachable) { vm.push(WalletRoute.MintDetail(mint.mint.url)) }
            }
            WalletCardDivider()
            WalletRow(title = stringResource(R.string.wallet_add_mint), icon = Icons.Filled.Add, titleColor = colorScheme.primary, onClick = { vm.startAddMint() })
        }
    }
}

@Composable
internal fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = LocalBitchatPalette.current.textTertiary)
}

@Composable
internal fun MintRow(mint: MintBalance, hidden: Boolean, unreachable: Boolean = false, onClick: () -> Unit) {
    val palette = LocalBitchatPalette.current
    val balance = mint.available.entries.filter { it.value > 0 }.joinToString(" · ") { formatAmount(it.value, it.key) }
        .ifEmpty { formatAmount(0, "sat") }
    WalletRow(
        title = mint.mint.displayName,
        subtitle = mint.mint.host + if (unreachable) " · " + stringResource(R.string.wallet_error_unreachable_title).lowercase() else "",
        painter = painterResource(R.drawable.ic_spec_ecash),
        iconTint = if (unreachable) palette.accentOrange else MaterialTheme.colorScheme.primary,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mint.mint.url == WalletService.TEST_MINT_URL) {
                    BitchatBadge(stringResource(R.string.wallet_mint_test_badge))
                    Spacer(Modifier.padding(start = 6.dp))
                }
                Text(
                    if (hidden) stringResource(R.string.wallet_balance_hidden) else balance,
                    fontFamily = BitchatFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}

@Composable
internal fun HistoryRow(entry: HistoryEntry, hidden: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val palette = LocalBitchatPalette.current
    val incoming = entry.isIncoming()
    val muted = entry.status == HistoryStatus.FAILED || entry.status == HistoryStatus.EXPIRED || entry.status == HistoryStatus.RECLAIMED
    val amountColor = when {
        muted -> palette.textTertiary
        entry.isPending -> palette.accentOrange
        incoming -> colorScheme.primary
        else -> colorScheme.onSurface
    }
    val subtitle = listOfNotNull(
        historyStatus(context, entry),
        formatRelative(entry.updatedAt),
        if (entry.viaChat) stringResource(R.string.wallet_note_via_chat) else null,
    ).joinToString(" · ")
    WalletRow(
        title = historyTitle(context, entry),
        subtitle = subtitle,
        icon = when (entry.kind) {
            HistoryKind.LIGHTNING_RECEIVE, HistoryKind.LIGHTNING_SEND -> Icons.Filled.Bolt
            HistoryKind.ECASH_RECEIVE -> Icons.AutoMirrored.Filled.CallReceived
            HistoryKind.ECASH_SEND -> Icons.AutoMirrored.Filled.CallMade
        },
        iconTint = if (muted) palette.textTertiary else if (entry.isPending) palette.accentOrange else colorScheme.primary,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.isPending) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = palette.accentOrange, modifier = Modifier.padding(end = 6.dp).size(14.dp))
                }
                Text(
                    if (hidden) stringResource(R.string.wallet_balance_hidden)
                    else (if (incoming) "+" else "−") + formatAmount(entry.amount, entry.unit),
                    fontFamily = BitchatFontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = amountColor,
                    textDecoration = if (entry.status == HistoryStatus.FAILED || entry.status == HistoryStatus.EXPIRED) TextDecoration.LineThrough else null,
                )
            }
        },
    )
}

// MARK: - Choosers

@Composable
internal fun ReceiveChoiceScreen(vm: WalletViewModel) {
    WalletPage {
        Spacer(Modifier.height(8.dp))
        WalletCard {
            WalletRow(
                title = stringResource(R.string.wallet_receive_lightning),
                icon = Icons.Filled.Bolt,
                onClick = { vm.startReceiveLightning() },
                trailing = { Chevron() },
            )
            WalletCardDivider()
            WalletRow(
                title = stringResource(R.string.wallet_receive_ecash),
                painter = painterResource(R.drawable.ic_spec_ecash),
                onClick = { vm.startClaim() },
                trailing = { Chevron() },
            )
        }
    }
}

@Composable
internal fun SendChoiceScreen(vm: WalletViewModel) {
    WalletPage {
        Spacer(Modifier.height(8.dp))
        WalletCard {
            WalletRow(
                title = stringResource(R.string.wallet_send_ecash),
                painter = painterResource(R.drawable.ic_spec_ecash),
                onClick = { vm.startSendEcash() },
                trailing = { Chevron() },
            )
            WalletCardDivider()
            WalletRow(
                title = stringResource(R.string.wallet_send_lightning),
                icon = Icons.Filled.Bolt,
                onClick = { vm.startSendLightning() },
                trailing = { Chevron() },
            )
        }
    }
}

// MARK: - History

@Composable
internal fun HistoryScreen(vm: WalletViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val revealed by vm.balanceRevealed.collectAsStateWithLifecycle()
    val hidden = state.settings[WalletService.SETTING_HIDE_BALANCE] == "true" && !revealed
    WalletPage {
        if (state.history.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            WalletNotice(stringResource(R.string.wallet_history_empty))
            return@WalletPage
        }
        Spacer(Modifier.height(8.dp))
        WalletCard {
            state.history.forEachIndexed { i, entry ->
                if (i > 0) WalletCardDivider()
                HistoryRow(entry, hidden) { vm.push(WalletRoute.HistoryDetail(entry.id)) }
            }
        }
    }
}

@Composable
internal fun HistoryDetailScreen(vm: WalletViewModel, id: String) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val entry = state.history.firstOrNull { it.id == id } ?: return
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ErrorCopy?>(null) }
    var confirmReclaim by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    val mint = state.mint(entry.mintUrl)?.mint
    val incoming = entry.isIncoming()

    WalletPage(
        bottom = if (entry.kind == HistoryKind.ECASH_SEND && entry.isPending && entry.token != null) {
            {
                WalletButton(stringResource(R.string.wallet_detail_reshare), onClick = { showToken = !showToken }, tone = WalletButtonTone.Neutral)
                WalletButton(stringResource(R.string.wallet_detail_reclaim), onClick = { confirmReclaim = true }, loading = busy)
            }
        } else if (entry.isPending) {
            {
                WalletButton(stringResource(R.string.wallet_detail_check), loading = busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { vm.wallet.recover() }
                        busy = false
                    }
                })
            }
        } else null,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AmountHero(
                entry.amount, entry.unit,
                color = if (incoming && entry.status == HistoryStatus.COMPLETED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(historyTitle(context, entry) + " · " + historyStatus(context, entry), fontFamily = BitchatFontFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        error?.let {
            WalletNotice(it.line(), tone = if (it.pending) NoticeTone.Warning else NoticeTone.Danger)
            Spacer(Modifier.height(8.dp))
        }
        HistoryTimeline(entry)
        Spacer(Modifier.height(12.dp))
        if (showToken && entry.token != null) {
            TokenShareBlock(entry.token)
            Spacer(Modifier.height(12.dp))
        }
        // Amount and status are already in the header above.
        WalletCard {
            WalletValueRow(stringResource(R.string.wallet_detail_mint), mint?.displayName ?: entry.mintUrl)
            if (entry.fee > 0) {
                WalletCardDivider(plain = true)
                WalletValueRow(stringResource(R.string.wallet_detail_fee), formatAmount(entry.fee, entry.unit))
            }
            WalletCardDivider(plain = true)
            WalletValueRow(stringResource(R.string.wallet_detail_date), formatDateTime(context, entry.createdAt))
            entry.memo?.let {
                WalletCardDivider(plain = true)
                WalletValueRow(stringResource(R.string.wallet_detail_memo), it, maxLines = 3)
            }
            entry.lockedUntil?.let {
                WalletCardDivider(plain = true)
                WalletValueRow(stringResource(R.string.wallet_detail_lock), stringResource(R.string.wallet_detail_locked_until, formatDateTime(context, it * 1000)), maxLines = 2)
            }
            entry.invoice?.takeIf { entry.kind == HistoryKind.LIGHTNING_SEND }?.let { invoice ->
                WalletCardDivider(plain = true)
                WalletValueRow(stringResource(R.string.wallet_detail_invoice), invoice.take(14) + "…", onCopy = { copyToClipboard(context, "invoice", invoice) })
            }
            entry.preimage?.let { pre ->
                WalletCardDivider(plain = true)
                WalletValueRow(stringResource(R.string.wallet_detail_proof_of_payment), pre.take(12) + "…", onCopy = { copyToClipboard(context, "preimage", pre) })
            }
        }
    }

    if (confirmReclaim) {
        AlertDialog(
            onDismissRequest = { confirmReclaim = false },
            title = { Text(stringResource(R.string.wallet_detail_reclaim_confirm_title), fontFamily = BitchatFontFamily) },
            text = { Text(stringResource(R.string.wallet_detail_reclaim_confirm_body), fontFamily = BitchatFontFamily) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReclaim = false
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            vm.wallet.reclaim(entry.id)
                        } catch (e: WalletException) {
                            error = errorCopy(context, e.error, entry.unit)
                        }
                        busy = false
                    }
                }) { Text(stringResource(R.string.wallet_detail_reclaim), fontFamily = BitchatFontFamily) }
            },
            dismissButton = { TextButton(onClick = { confirmReclaim = false }) { Text(stringResource(R.string.wallet_cancel), fontFamily = BitchatFontFamily) } },
        )
    }
}

// MARK: - Settings

@Composable
internal fun SettingsScreen(vm: WalletViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmWords by remember { mutableStateOf(false) }
    val palette = LocalBitchatPalette.current
    WalletPage {
        Spacer(Modifier.height(8.dp))
        WalletCard {
            WalletRow(
                title = stringResource(R.string.wallet_settings_words),
                subtitle = stringResource(if (vm.backedUp) R.string.wallet_settings_words_ok else R.string.wallet_settings_words_todo),
                icon = Icons.Filled.Key,
                iconTint = if (vm.backedUp) MaterialTheme.colorScheme.primary else palette.accentOrange,
                onClick = { confirmWords = true },
                trailing = { Chevron() },
            )
            WalletCardDivider()
            SettingSwitchRow(
                title = stringResource(R.string.wallet_settings_nostr_backup),
                subtitle = stringResource(R.string.wallet_settings_nostr_backup_desc),
                icon = Icons.Outlined.CloudUpload,
                checked = state.settings[WalletService.SETTING_NOSTR_BACKUP] == "true",
                onChange = { on ->
                    vm.setSetting(WalletService.SETTING_NOSTR_BACKUP, on.toString())
                    if (on) WalletService.publishBackup(context, state.mints.map { it.mint.url })
                },
            )
            WalletCardDivider()
            SettingSwitchRow(
                title = stringResource(R.string.wallet_settings_hide_balance),
                subtitle = stringResource(R.string.wallet_settings_hide_balance_desc),
                icon = Icons.Filled.VisibilityOff,
                checked = state.settings[WalletService.SETTING_HIDE_BALANCE] == "true",
                onChange = { vm.setSetting(WalletService.SETTING_HIDE_BALANCE, it.toString()) },
            )
        }
    }
    if (confirmWords) {
        AlertDialog(
            onDismissRequest = { confirmWords = false },
            title = { Text(stringResource(R.string.wallet_settings_show_words_title), fontFamily = BitchatFontFamily) },
            text = { Text(stringResource(R.string.wallet_settings_show_words_body), fontFamily = BitchatFontFamily) },
            confirmButton = {
                TextButton(onClick = { confirmWords = false; vm.showBackup() }) {
                    Text(stringResource(R.string.wallet_settings_show_words_confirm), fontFamily = BitchatFontFamily)
                }
            },
            dismissButton = { TextButton(onClick = { confirmWords = false }) { Text(stringResource(R.string.wallet_cancel), fontFamily = BitchatFontFamily) } },
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    WalletRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = { onChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = colorScheme.primary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = colorScheme.surfaceVariant,
                ),
            )
        },
    )
}
