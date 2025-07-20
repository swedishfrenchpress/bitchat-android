# Transaction Detail Implementation

## Overview

This implementation adds detailed transaction views to the bitchat Android app. When a user taps on a transaction in the transaction list, it opens a new screen showing comprehensive information about that specific transaction.

## Features

### UI Design
- **Terminal/TTY Style**: Follows the existing app's monospace design language
- **Theme Consistency**: Uses existing styles from `Theme.kt` and `Typography.kt`
- **Dark/Light Mode**: Fully supports both themes without hardcoded colors
- **Responsive Layout**: Scrollable content with proper spacing and grouping

### Transaction Types Supported

#### Ecash Transactions (CASHU_SEND/CASHU_RECEIVE)
- Status badge (redeemed/pending/expired)
- Amount in sats with ₿ formatting
- Mint name or domain
- Timestamp
- Token (truncated view with expand option)
- Copy token functionality
- Reclaim token button (for sent tokens)

#### Lightning Transactions (LIGHTNING_SEND/LIGHTNING_RECEIVE)
- Status badge (paid/expired/pending)
- Amount with ₿ formatting
- Mint information
- Timestamp
- Lightning invoice (truncated with expand option)
- Copy invoice functionality
- Fee information (if available)

#### Mint/Melt Transactions (MINT/MELT)
- Status badge
- Amount with ₿ formatting
- Mint information
- Timestamp
- Lightning invoice (truncated with expand option)
- Copy invoice functionality
- Fee information (if available)

## Implementation Details

### Files Created/Modified

1. **`TransactionDetailScreen.kt`** (New)
   - Main detail screen component
   - Handles different transaction types
   - Implements copy functionality
   - Supports token reclamation

2. **`TransactionItem.kt`** (Modified)
   - Added `onClick` parameter for navigation
   - Made items clickable when enabled

3. **`TransactionHistoryScreen.kt`** (Modified)
   - Added `onTransactionClick` callback
   - Passes transaction data to detail screen

4. **`WalletScreen.kt`** (Modified)
   - Added transaction detail navigation state
   - Integrated detail screen into navigation flow
   - Added back handler for detail screen

### Navigation Flow

```
Wallet Overview → Transaction History → Transaction Detail
     ↑                    ↑                      ↑
   Back to Chat      Back to Overview      Back to History
```

### Key Components

#### TransactionDetailScreen
- Main composable that orchestrates the detail view
- Handles clipboard operations
- Manages expand/collapse state for long text

#### TransactionDetailHeader
- Shows transaction status, amount, type, and timestamp
- Uses consistent styling with other wallet screens

#### TransactionStatusBadge
- Color-coded status indicators
- Supports all transaction statuses

#### DetailRow
- Reusable component for label-value pairs
- Consistent typography and spacing

#### Type-Specific Detail Components
- `CashuTransactionDetails`: Handles ecash-specific fields
- `LightningTransactionDetails`: Handles lightning-specific fields
- `MintMeltTransactionDetails`: Handles mint/melt-specific fields

### Styling Guidelines

- **Colors**: All colors come from `MaterialTheme.colorScheme`
- **Typography**: Uses existing `Typography` definitions
- **Spacing**: Consistent 8dp, 16dp, 24dp spacing
- **Shapes**: 4dp rounded corners for containers
- **Buttons**: Reuse existing `BitchatButton` component

### Interactive Features

#### Copy Functionality
- Uses `LocalClipboardManager` for clipboard operations
- Available for tokens and lightning invoices
- Clear button labeling

#### Expand/Collapse
- Long text (tokens, invoices) are truncated by default
- "Show full" / "Show less" buttons for expansion
- Maintains readability while saving space

#### Token Reclamation
- Only shown for sent ecash transactions
- Disabled if transaction is already confirmed
- Calls back to parent for actual reclamation logic

## Usage

### Basic Navigation
```kotlin
// In TransactionHistoryScreen
TransactionItem(
    // ... other parameters
    onClick = { onTransactionClick(transaction) }
)
```

### Detail Screen
```kotlin
TransactionDetailScreen(
    transaction = selectedTransaction,
    onBackClick = { /* navigate back */ },
    onReclaimToken = { token ->
        // Implement token reclamation logic
    }
)
```

## Future Enhancements

1. **Token Reclamation**: Implement actual CDK calls for token reclamation
2. **Payment Hash Display**: Show payment hashes for lightning transactions
3. **QR Code Generation**: Add QR codes for tokens/invoices
4. **Transaction Sharing**: Allow sharing transaction details
5. **Export Functionality**: Export transaction data

## Testing

The implementation includes:
- Preview functions for UI testing
- Proper state management
- Error handling for missing data
- Accessibility considerations

## Notes

- All UI components reuse existing design elements
- No hardcoded colors or styles
- Follows established navigation patterns
- Maintains consistency with existing wallet screens 