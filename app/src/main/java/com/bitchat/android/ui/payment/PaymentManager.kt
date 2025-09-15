package com.bitchat.android.ui.payment

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.bitchat.android.wallet.viewmodel.WalletViewModel
import com.bitchat.android.wallet.data.TransactionType
import com.bitchat.android.wallet.data.TransactionStatus
import com.bitchat.android.wallet.data.WalletTransaction
import com.bitchat.android.model.BitchatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.*

/**
 * Manages payment creation and status for the /pay command
 * Coordinates between ChatViewModel, WalletViewModel, and UI
 */
class PaymentManager(
    private val coroutineScope: CoroutineScope,
    private val walletViewModel: WalletViewModel
) {
    
    companion object {
        private const val TAG = "PaymentManager"
    }
    
    // Payment status state
    private val _paymentStatus = MutableStateFlow<PaymentStatus?>(null)
    val paymentStatus: StateFlow<PaymentStatus?> = _paymentStatus.asStateFlow()
    
    /**
     * Create a Cashu token payment for the specified amount
     */
    fun createPayment(
        amount: Long,
        memo: String? = null,
        onTokenCreated: (String) -> Unit
    ) {
        Log.d(TAG, "Creating payment for $amount sats")
        Log.d(TAG, "WalletViewModel is null: ${walletViewModel == null}")
        
        // Set creating status
        _paymentStatus.value = PaymentStatus.Creating(amount)
        
        coroutineScope.launch {
            try {
                // Check if wallet is properly initialized first
                Log.d(TAG, "Attempting to create payment token...")
                
                // Use WalletViewModel's createCashuToken method
                walletViewModel.createCashuTokenForPayment(
                    amount = amount,
                    memo = memo,
                    onSuccess = { token ->
                        Log.d(TAG, "Payment token created successfully: ${token.take(20)}...")
                        
                        // Set success status
                        _paymentStatus.value = PaymentStatus.Success(amount, token)
                        
                        // Notify caller with the token
                        onTokenCreated(token)
                    },
                    onError = { error ->
                        Log.e(TAG, "Payment creation failed: $error")
                        
                        // Check if it's a transport error and provide helpful message
                        val userFriendlyError = when {
                            error.contains("transport error") -> {
                                "Network error: Unable to connect to mint. Please check your internet connection and try again. If the problem persists, try resetting your wallet in Settings."
                            }
                            error.contains("No active mint") -> {
                                "No mint configured: Please add a mint in wallet settings first."
                            }
                            else -> error
                        }
                        
                        // Set error status
                        _paymentStatus.value = PaymentStatus.Error(amount, userFriendlyError)
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception creating payment", e)
                _paymentStatus.value = PaymentStatus.Error(
                    amount, 
                    e.message ?: "Unknown error occurred"
                )
            }
        }
    }
    
    /**
     * Clear the current payment status
     */
    fun clearStatus() {
        _paymentStatus.value = null
    }
    
    /**
     * Check if a payment is currently being created
     */
    fun isCreatingPayment(): Boolean {
        return _paymentStatus.value is PaymentStatus.Creating
    }
} 