package com.bitchat.android.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bitchat.android.ui.CashuPaymentChip
import com.bitchat.android.ui.theme.BitchatTheme
import com.bitchat.android.wallet.cashu.Token
import com.bitchat.android.wallet.cashu.TokenCodec
import com.bitchat.android.wallet.engine.CashuWallet
import com.bitchat.android.wallet.ui.AmountDigits
import com.bitchat.android.wallet.ui.AmountStep
import com.bitchat.android.wallet.ui.TrustNotes
import com.bitchat.android.wallet.ui.WalletLaunch
import com.bitchat.android.wallet.ui.WalletLauncher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** On-device checks of wallet surfaces that need real resources but no mint. */
class WalletUiInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val token = TokenCodec.encodeV4(
        Token(
            "https://mint.example", "sat",
            listOf(com.bitchat.android.wallet.cashu.Proof(21, "00ad268c4d1f5826", "a".repeat(64), "02" + "11".repeat(32))),
            memo = "lunch",
        )
    )

    @Test
    fun chatCardShowsAmountMintAndAClaimCallToAction() {
        compose.setContent { BitchatTheme { CashuPaymentChip(token) } }
        compose.onNodeWithText("21 sat · mint.example").assertIsDisplayed()
        compose.onNodeWithText("tap to claim").assertIsDisplayed()
        compose.onNodeWithText("lunch").assertIsDisplayed()
    }

    @Test
    fun tappingAChatCardAsksTheWalletToClaimThatToken() {
        WalletLauncher.close()
        compose.setContent { BitchatTheme { CashuPaymentChip(token) } }
        compose.onNodeWithText("tap to claim").performClick()
        assertEquals(WalletLaunch.Claim(token), WalletLauncher.request.value)
        WalletLauncher.close()
    }

    @Test
    fun composerPreviewChipHasNoStatusLine() {
        compose.setContent { BitchatTheme { CashuPaymentChip(token, onClick = {}, showActions = false) } }
        compose.onNodeWithText("21 sat · mint.example").assertIsDisplayed()
        compose.onAllNodesWithTextCount("tap to claim", 0)
    }

    @Test
    fun keypadBuildsTheAmountAndBlocksContinuePastTheBalance() {
        compose.setContent {
            BitchatTheme {
                var amount by remember { mutableStateOf(AmountDigits()) }
                AmountStep(
                    amount = amount, unit = "sat",
                    contextLine = if (amount.value > 50) "over" else "ok", contextIsError = amount.value > 50,
                    mints = emptyList(), selectedMint = null, onMint = {},
                    onChange = { amount = it },
                    continueLabel = "Continue", onContinue = {}, continueEnabled = amount.value in 1..50,
                    maxAmount = 50,
                )
            }
        }
        compose.onNodeWithText("4").performClick()
        compose.onNodeWithText("2").performClick()
        compose.onNodeWithContentDescription("42 sat").assertIsDisplayed()
        compose.onNodeWithText("9").performClick()
        compose.onNodeWithText("over").assertIsDisplayed()
        compose.onNodeWithText("CONTINUE").assertIsNotEnabled()
        compose.onNodeWithText("Max").performClick()
        compose.onNodeWithContentDescription("50 sat").assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete digit").performClick()
        compose.onNodeWithContentDescription("5 sat").assertIsDisplayed()
    }

    @Test
    fun trustNoteWarnsThatAnUnlockedTokenIsNotYoursYet() {
        val inspection = CashuWallet.TokenInspection(
            raw = token, token = TokenCodec.decode(token), mintKnown = true,
            dleq = CashuWallet.DleqStatus.VERIFIED, lock = CashuWallet.LockStatus.None,
            alreadyClaimed = false, isOurPendingSend = false, fee = 0,
        )
        compose.setContent { BitchatTheme { TrustNotes(inspection) } }
        compose.onNodeWithText("unlocked · claim it before the sender spends it").assertIsDisplayed()
    }

    @Test
    fun trustNoteFlagsAForgedSignatureInsteadOfTheLockLine() {
        val inspection = CashuWallet.TokenInspection(
            raw = token, token = TokenCodec.decode(token), mintKnown = true,
            dleq = CashuWallet.DleqStatus.INVALID, lock = CashuWallet.LockStatus.None,
            alreadyClaimed = false, isOurPendingSend = false, fee = 0,
        )
        compose.setContent { BitchatTheme { TrustNotes(inspection) } }
        compose.onNodeWithText("signatures don't match the mint · don't accept it").assertIsDisplayed()
        compose.onAllNodesWithTextCount("unlocked · claim it before the sender spends it", 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String, count: Int) {
        assertEquals(count, onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size)
    }
}
