package com.bitchat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bitchat.android.wallet.viewmodel.WalletViewModel

// Import the SuccessAnimation component (same package, no need for full path)

/**
 * Main wallet screen showing wallet overview
 */
@Composable
fun WalletScreen(
    walletViewModel: WalletViewModel = viewModel(),
    onBackToChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showReceiveView by remember { mutableStateOf(false) }
    var showSendView by remember { mutableStateOf(false) }
    var showSettingsView by remember { mutableStateOf(false) }
    var showTransactionHistoryView by remember { mutableStateOf(false) }
    var showTopUpScreen by remember { mutableStateOf(false) }
    var navigationHistory by remember { mutableStateOf<String?>(null) }
    val showSendDialog by walletViewModel.showSendDialog.observeAsState(false)
    val showReceiveDialog by walletViewModel.showReceiveDialog.observeAsState(false)
    val showSuccessAnimation by walletViewModel.showSuccessAnimation.observeAsState(false)
    val successAnimationData by walletViewModel.successAnimationData.observeAsState()
    val showFailureAnimation by walletViewModel.showFailureAnimation.observeAsState(false)
    val failureAnimationData by walletViewModel.failureAnimationData.observeAsState()
    
    // Back handler for the wallet
    fun handleBackPress(): Boolean {
        return when {
            // Close transaction history view
            showTransactionHistoryView -> {
                showTransactionHistoryView = false
                true
            }
            // Close settings view
            showSettingsView -> {
                showSettingsView = false
                true
            }
            // Close top up screen
            showTopUpScreen -> {
                showTopUpScreen = false
                true
            }
            // Close receive view
            showReceiveView -> {
                showReceiveView = false
                walletViewModel.hideReceiveDialog()
                true
            }
            // Close send view
            showSendView -> {
                showSendView = false
                walletViewModel.hideSendDialog()
                true
            }
            // Close send dialog
            showSendDialog -> {
                walletViewModel.hideSendDialog()
                true
            }
            // Go back to chat
            else -> {
                onBackToChat()
                true
            }
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Main content
        if (showTopUpScreen) {
            // Full-screen TopUpScreen
            TopUpScreen(
                viewModel = walletViewModel,
                onBackClick = { showTopUpScreen = false },
                onSettingsClick = { 
                    navigationHistory = "topup"
                    showTopUpScreen = false
                    showSettingsView = true 
                },
                onTokenReceived = { showTopUpScreen = false }, // Return to Wallet Overview after receiving token
                modifier = Modifier.fillMaxSize()
            )
        } else if (showReceiveView) {
            // Full-screen ReceiveView
            ReceiveView(
                viewModel = walletViewModel,
                onNavigateBack = { 
                    showReceiveView = false 
                    walletViewModel.hideReceiveDialog()
                }
            )
        } else if (showSendView) {
            // Full-screen SendView
            SendView(
                viewModel = walletViewModel,
                onNavigateBack = { 
                    showSendView = false 
                    walletViewModel.hideSendDialog()
                }
            )
        } else if (showSettingsView) {
            // Settings screen
            WalletSettings(
                viewModel = walletViewModel,
                onBackClick = { 
                    showSettingsView = false
                    // Navigate back to where we came from
                    when (navigationHistory) {
                        "topup" -> {
                            showTopUpScreen = true
                            navigationHistory = null
                        }
                        else -> {
                            // Default: go back to main wallet overview
                            navigationHistory = null
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (showTransactionHistoryView) {
            // Transaction history screen
            TransactionHistoryScreen(
                viewModel = walletViewModel,
                onBackClick = { showTransactionHistoryView = false },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Main wallet overview
            WalletOverview(
                viewModel = walletViewModel,
                onBackToChat = onBackToChat,
                onSettingsClick = { 
                    navigationHistory = "overview"
                    showSettingsView = true 
                },
                onTransactionHistoryClick = { showTransactionHistoryView = true },
                onTopUpClick = { showTopUpScreen = true },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Success animation overlay
        if (showSuccessAnimation && successAnimationData != null) {
            SuccessAnimation(
                animationData = successAnimationData!!,
                onAnimationComplete = {
                    walletViewModel.hideSuccessAnimation()
                },
                modifier = Modifier.zIndex(10f)
            )
        }
        
        // Failure animation overlay
        if (showFailureAnimation && failureAnimationData != null) {
            FailureAnimation(
                errorMessage = failureAnimationData!!.errorMessage,
                operationType = failureAnimationData!!.operationType,
                onAnimationComplete = {
                    walletViewModel.hideFailureAnimation()
                },
                modifier = Modifier.zIndex(10f)
            )
        }
    }
    
    // Handle dialog states - sync local view state with ViewModel dialog state
    LaunchedEffect(showSendDialog) {
        if (showSendDialog) {
            showSendView = true
        } else {
            showSendView = false
        }
    }
    
    LaunchedEffect(showReceiveDialog) {
        if (showReceiveDialog) {
            showReceiveView = true
        } else {
            showReceiveView = false
        }
    }
    
    // Expose the back handler to the parent
    // This will be called from MainAppScreen
    walletViewModel.setBackHandler { handleBackPress() }
}


