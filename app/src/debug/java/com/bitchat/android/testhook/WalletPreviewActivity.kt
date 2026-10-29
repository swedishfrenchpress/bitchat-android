package com.bitchat.android.testhook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
 * Optional extra `token` opens the claim flow for that token.
 */
class WalletPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launch = intent.getStringExtra("token")?.let { WalletLaunch.Claim(it, viaChat = false) } ?: WalletLaunch.Home
        setContent {
            BitchatTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    WalletSheet(launch = launch, onDismiss = { finish() })
                }
            }
        }
    }
}
