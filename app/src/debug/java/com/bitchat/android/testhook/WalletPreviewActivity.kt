package com.bitchat.android.testhook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.bitchat.android.ui.CashuPaymentChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.bitchat.android.ui.theme.BitchatTheme
import com.bitchat.android.wallet.ui.WalletLaunch
import com.bitchat.android.wallet.ui.WalletSheet

/**
 * Debug-only host for the wallet sheet, so wallet flows can be exercised and screenshotted
 * on a device or emulator without the mesh onboarding (Bluetooth, location services).
 *
 * `adb shell am start -n com.bitchat.droid/com.bitchat.android.testhook.WalletPreviewActivity`
 * Optional extra `token` opens the claim flow for that token. Extra `cards=true` shows chat
 * payment cards instead: a synthetic claimable token plus the wallet's own pending sends.
 * Extra `chat=true` shows the real chat screen (optionally `dm=<peer id>`), so wallet entry
 * points in chat can be exercised without mesh onboarding.
 */
class WalletPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launch = intent.getStringExtra("token")?.let { WalletLaunch.Claim(it, viaChat = false) } ?: WalletLaunch.Home
        if (intent.getBooleanExtra("cards", false)) {
            setContent { BitchatTheme { CardGallery() } }
            return
        }
        if (intent.getBooleanExtra("chat", false)) {
            // The real ChatScreen and ChatViewModel, built exactly as MainActivity does, minus
            // the onboarding (which requires system location services). Mesh transports are
            // not started here; nothing is broadcast. Extra `dm` opens a DM with that peer ID.
            val mesh = com.bitchat.android.service.MeshServiceHolder.getOrCreate(applicationContext)
            val unified = com.bitchat.android.service.MeshServiceHolder.getUnifiedOrCreate(applicationContext)
            val chat = androidx.lifecycle.ViewModelProvider(this, object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return com.bitchat.android.ui.ChatViewModel(application, mesh, unified) as T
                }
            })[com.bitchat.android.ui.ChatViewModel::class.java]
            intent.getStringExtra("dm")?.let(chat::showPrivateChatSheet)
            setContent { BitchatTheme { com.bitchat.android.ui.ChatScreen(viewModel = chat) } }
            return
        }
        setContent {
            BitchatTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    WalletSheet(launch = launch, onDismiss = { finish() })
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun CardGallery() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val wallet = androidx.compose.runtime.remember { com.bitchat.android.wallet.WalletService.wallet(context) }
    val state by wallet.state.collectAsState()
    val synthetic = com.bitchat.android.wallet.cashu.TokenCodec.encodeV4(
        com.bitchat.android.wallet.cashu.Token(
            "https://mint.example", "sat",
            listOf(com.bitchat.android.wallet.cashu.Proof(21, "00ad268c4d1f5826", "a".repeat(64), "02" + "11".repeat(32))),
            memo = "coffee",
        )
    )
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        CashuPaymentChip(synthetic)
        state.history.mapNotNull { it.token }.forEach { CashuPaymentChip(it) }
        CashuPaymentChip(synthetic, onClick = {}, showActions = false)
    }
}
