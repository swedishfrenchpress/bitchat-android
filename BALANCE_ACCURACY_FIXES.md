# Balance Accuracy and Mint State Fixes

## Issues Fixed

### 1. **Ecash Withdraw Defaults to Wrong Mint**
**Problem**: When attempting to withdraw bitcoin as an ecash token, it was generating tokens using the testnut mint (`https://testnut.cashu.space`) instead of the user's currently selected, connected mint.

**Root Cause**: The `CashuService.createToken()` method was hardcoded to use `DEFAULT_MINT_URL` instead of checking the currently active mint from the repository.

**Solution**: 
- Modified `createToken()` to use `ensureWalletInitializedWithActiveMint()` helper method
- This ensures the wallet is always initialized with the currently active mint before creating tokens
- Added comprehensive logging to track mint state changes

### 2. **Payment Shows in History but Balance Does Not Update**
**Problem**: After paying Lightning invoices, transactions appeared in the history but the wallet's total balance did not decrease, and mint balances in Settings showed no change.

**Root Causes**:
1. The `getBalance()` method was also defaulting to `DEFAULT_MINT_URL` instead of using the active mint
2. Race conditions where balance was queried before the CDK wallet state was fully synchronized
3. No proper synchronization between wallet state and repository's active mint

**Solutions**:
1. **Fixed Balance Method**: Modified `getBalance()` to use `ensureWalletInitializedWithActiveMint()` helper
2. **Added State Synchronization**: Created `ensureWalletInitializedWithActiveMint()` helper method that:
   - Gets the currently active mint from repository
   - Ensures wallet is initialized with the correct mint
   - Handles fallback to default mint only when no active mint is configured
3. **Added Timing Delays**: Added 500ms delays after successful operations to ensure CDK wallet state is properly updated before refreshing balances
4. **Enhanced Logging**: Added comprehensive logging to track mint state changes and wallet initialization

## Technical Changes

### 1. **New Helper Method**
```kotlin
private suspend fun ensureWalletInitializedWithActiveMint(): Result<String>
```
This method centralizes the logic for:
- Getting the active mint from repository
- Ensuring wallet is initialized with the correct mint
- Handling fallback scenarios
- Providing detailed logging for debugging

### 2. **Updated Methods**
All wallet operations now use the helper method:
- `getBalance()` - Now uses active mint instead of default
- `createToken()` - Now uses active mint for token creation
- `createMintQuote()` - Now uses active mint for Lightning receiving
- `checkAndMintQuote()` - Now uses active mint for quote checking
- `createMeltQuote()` - Now uses active mint for Lightning sending
- `payInvoice()` - Now uses active mint for payments

### 3. **Enhanced Balance Refresh**
Added timing delays in:
- `LightningManager.payLightningInvoice()` - 500ms delay after payment
- `LightningManager.checkPendingQuotes()` - 500ms delay after minting
- `TokenManager.receiveCashuToken()` - 500ms delay after receiving
- `TokenManager.createCashuToken()` - 500ms delay after creating
- `TokenManager.createCashuTokenForPayment()` - 500ms delay after creating

### 4. **Improved Logging**
Added detailed logging throughout the wallet operations:
- Active mint tracking
- Wallet initialization state
- Mint URL changes
- Balance refresh operations
- CDK state synchronization

## Design Principles Maintained

✅ **All balance rendering driven by CDK** - No hardcoded or fake balances
✅ **Existing state management preserved** - Uses existing repository and manager patterns
✅ **No unintended wallet reinitialization** - Only reinitializes when mint actually changes
✅ **Consistent behavior across screens** - All operations now use the same mint selection logic

## Testing Recommendations

1. **Test Ecash Withdraw**: 
   - Select a specific mint in Settings
   - Create an ecash token for withdrawal
   - Verify the token is generated from the correct mint

2. **Test Lightning Payments**:
   - Pay a Lightning invoice
   - Verify the balance decreases immediately
   - Check that the transaction appears in history with correct balance

3. **Test Mint Switching**:
   - Switch between different mints
   - Verify all operations use the newly selected mint
   - Check that balances are accurate for each mint

4. **Test Balance Accuracy**:
   - Perform various transactions
   - Verify balances update correctly in all UI components
   - Check that mint-specific balances are accurate

## Files Modified

- `app/src/main/java/com/bitchat/android/wallet/service/CashuService.kt`
  - Added `ensureWalletInitializedWithActiveMint()` helper method
  - Updated all wallet operations to use active mint
  - Enhanced logging throughout

- `app/src/main/java/com/bitchat/android/wallet/viewmodel/LightningManager.kt`
  - Added timing delays for balance refresh
  - Enhanced logging for payment operations

- `app/src/main/java/com/bitchat/android/wallet/viewmodel/TokenManager.kt`
  - Added timing delays for balance refresh
  - Enhanced logging for token operations

## Expected Behavior After Fixes

1. **Ecash tokens will always be generated from the currently selected, connected mint**
2. **Balance will update immediately after any transaction (withdraw or payment)**
3. **Mint state will persist reliably between screens**
4. **No fallback to test mints unless explicitly selected by user**
5. **All balance displays will be driven by CDK and reflect actual wallet state**

## Monitoring and Debugging

The enhanced logging will help identify any remaining issues:
- Check logs for "Current active mint from repository" to verify mint selection
- Look for "Wallet successfully initialized with active mint" to confirm wallet state
- Monitor "Refreshing balance after..." messages to track balance updates
- Verify "Real balance from active mint" shows correct amounts

These fixes ensure that the wallet maintains accurate balance state and always operates with the user's intended mint, providing a reliable and consistent user experience. 