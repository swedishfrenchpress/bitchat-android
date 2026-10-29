# Cashu wallet: repository audit

Audit of the existing Bitchat Android codebase before building the wallet. All
paths are repository-relative.

## 1. Existing Cashu code

| Area | Location | What it does |
|---|---|---|
| Display decoder | `app/src/main/java/com/bitchat/android/ui/CashuTokenDecoder.kt` | Bounded, display-only parsing of `cashuA` (JSON) and `cashuB` (CBOR) tokens: total amount, unit, mint host, memo. Has a hardened mini CBOR reader (depth/item budgets). Never contacts a mint. Extracts up to 3 tokens from free text, builds `cashu:` and `redeem.cashu.me` URIs. |
| Chat chip | `ui/MessageComponents.kt` (`CashuMessageContent`, `CashuPaymentChip`) | Renders tokens inside messages as a 🥜 chip (amount · mint host, memo). Tap hands the token to an external wallet via `cashu:` intent, falling back to the web redeemer. Long-press: copy / redeem in wallet / redeem on web. |
| Composer preview | `ui/InputComponents.kt` | When the composer holds a bare token, the text is hidden and a chip is shown instead. |
| `/pay` command | `ui/CommandProcessor.kt` | `/pay <token> [public]` sends a pasted token. In a DM it goes straight through; in public it requires the explicit `public` suffix because "first redeemer wins". |
| Tests | `app/src/test/.../ui/CashuTokenDecoderTest.kt`, `CommandProcessorTest.kt` | Decoder edge cases (minor units, sanitization, CBOR bounds) and `/pay` behavior. |
| Strings | `res/values/strings.xml` (`cashu_*`) | 5 strings, partially translated (`values-eu`). |

**There is no wallet on `main`.** Tokens are treated as opaque bearer text and
handed to other apps. The working branch at the time of this audit was `main`;
the wallet branch `cashu-wallet` was cut from it.

Prior art outside `main`: an older CDK-FFI based wallet lives on a fork branch
(`parse-cashu-v3`). It is ~670 commits behind `main`, ships native CDK binaries
(conflicts with the reproducible-build policy in `docs/reproducible-builds.md`),
and uses its own visual language. It was reviewed for feature ideas only; no
code is reused (see `decisions.md`, D-002).

## 2. Visual identity

### Typography
- `ui/theme/ChatVisualTokens.kt` defines `BitchatFontFamily`: bundled **Geist
  Mono** (regular/medium/semibold/bold) used for *everything*, kept in the APK
  for offline rendering.
- `ui/theme/Typography.kt` maps all Material 3 styles onto Geist Mono, sized off
  `AppConstants.UI.BASE_FONT_SIZE_SP`.
- Section labels: 11sp, Medium, `letterSpacing 0.8sp`, **uppercased in code**
  (translators supply sentence case), tinted `palette.textTertiary`
  (`AboutSectionLabel`, `SheetSectionLabel`).
- Row titles 14sp Medium, subtitles 12sp / 17sp line height
  (`SettingsToggleRow` in `ui/AboutSheet.kt`).
- Hero wordmark 40sp Bold primary (`AboutHero`).

### Color
- `ui/theme/Theme.kt`: Material 3 schemes. Dark: pure black background,
  terminal green primary `#32D74B`, blue secondary `#0A84FF`, orange tertiary,
  green-tinted near-black surfaces (`#0E150E`, `#182118`). Light: white
  background, green `#248A3D`.
- `ui/theme/BitchatPalette.kt` (`LocalBitchatPalette`): extra semantics that
  Material lacks: neutral input surfaces/outlines, `textTertiary`,
  `accentOrange` (self/mentions/unread), `accentPurple` (Nostr reachability).
- Errors use `colorScheme.error` (`#FF453A` dark).
- Theme follows a System/Light/Dark preference (`ThemePreferenceManager`).

### Shape, density, components
- Sheets: `core/ui/component/sheet/BitchatBottomSheet.kt`, a full-height
  `ModalBottomSheet` with no drag handle, 28dp top corners, background-colored,
  and `LocalSheetDismiss` for animated programmatic dismissal. **Every secondary
  surface in Bitchat is a sheet**; there is no navigation graph.
- Sheet top bars: `BitchatSheetTopBar` / `BitchatSheetCenterTopBar` with a
  trailing green `CloseButton` (44dp target) and a background that fades in on
  scroll (`AboutSheet` pattern: alpha animated over `EMPHASIZED_MS`).
- Grouped cards: `Surface(color = surface, shape = RoundedCornerShape(16.dp))`
  inset 20dp (`AboutHorizontalPadding`, `AboutCardShape`), rows 16dp/13dp
  padding, 22dp leading icon, dividers inset to the text column
  (`SheetCardDivider`).
- Buttons: Bitchat avoids filled Material buttons. Actions are tinted
  outline pills (`SheetDestructiveButton`: 44dp tall, 10dp radius, accent at
  10% fill + 30% border, uppercase 11sp tracked label). The theme chip
  (`ThemeChip`) is the selection idiom.
- Press feedback: **scale-only**, no ripples (`ui/PressFeedback.kt`,
  `rememberPressScale`, `pressScaleClickable`).
- Icons: Material icons (filled/outlined), a few custom drawables
  (`res/drawable/ic_spec_*`), the pixel `BitChatIcon`.
- Emoji used sparingly as glyphs (🥜 in the Cashu chip).

### Motion
- `BitchatMotion` tokens: `QUICK_MS 120` (tints), `STANDARD_MS 180` (chips,
  pills), `EMPHASIZED_MS 240` (sheet-level fades). Short and snappy by
  design; `FastOutSlowInEasing` throughout.
- Programmatic sheet dismissal always animates (`LocalSheetDismiss`).

### Copy tone
- System messages in the transcript are **lowercase, terse, technical**:
  `sent 21 sat — bearer token; first redeemer wins`,
  `all data wiped — new identity created`.
- UI labels are sentence case and plain (`Copy token`, `Wipe all data?`).
- Dialogs state consequences directly: "This permanently deletes… It can't be
  undone."
- Strings live in `res/values/strings.xml`; new strings are marked
  `tools:ignore="MissingTranslation"` until translated.

## 3. Navigation and entry points

- Single activity (`MainActivity`) hosting `ChatScreen`. Overlays are
  boolean-driven sheets declared in `ChatScreen.ChatDialogs`.
- Header (`ui/ChatHeader.kt`, `MainHeader`): brand glyph (tap = About,
  **triple-tap = panic wipe**), nickname, then a crowded trailing cluster
  (unread DMs, notes, channel, people). There is no free header slot.
- About sheet (`ui/AboutSheet.kt`): Info / Settings tabs; Settings is a stack of
  labeled cards. This is the natural home for a "Wallet" row.
- DMs: `PrivateChatSheet` (in `ui/MeshPeerListSheet.kt`) stacks over the
  network sheet. Composer: `ui/InputComponents.kt` (`MessageInput`).
- Commands: `ui/CommandProcessor.kt`, which has a suggestion list and a `/pay`
  precedent.

## 4. Mesh and Nostr layers

- Messages are text. A DM goes over Noise-encrypted BLE/Wi-Fi Aware mesh when
  the peer is reachable, otherwise NIP-17 gift wraps over Nostr for mutual
  favorites (`PrivateChatManager`, `MessageRouter`). Public mesh and geohash
  channels are plaintext broadcasts to everyone present.
- **Tokens are therefore carried as message text**, which keeps iOS
  compatibility: iOS Bitchat renders the same `cashuA`/`cashuB` strings.
- Nostr crypto is in-house on BouncyCastle (`nostr/NostrCrypto.kt`): secp256k1
  key generation, BIP-340 Schnorr sign/verify, ECDH, NIP-44 v2. **There is no
  libsecp256k1**; BouncyCastle `SECNamedCurves` is the EC backend.
- Nostr identity: `NostrIdentityBridge.getCurrentNostrIdentity` (persistent
  secp256k1 key in `SecureIdentityStateManager`), plus per-geohash derived
  identities. Favorites store the peer's npub
  (`favorites/FavoritesPersistenceService.kt`), so for mutual favorites we
  know a secp256k1 public key for the peer, which is usable for NUT-11 P2PK.
- Relays: `nostr/NostrRelayManager.kt` (`sendEvent`, `subscribe`, and
  `clearAllOnPanic`), `NostrEvent.sign`.

## 5. Networking and privacy

- `net/OkHttpProvider.kt` hands out a client that **routes through Arti/Tor's
  SOCKS proxy whenever Tor mode is on** (fail-closed: proxy set even before
  bootstrap). Mint HTTP must use this provider so the wallet inherits Tor.
- Default timeouts (15s call) are too short for melts; a derived client with
  longer read/call timeouts is needed for Lightning payments.

## 6. Storage

- Identity secrets: `identity/SecureIdentityStateManager.kt` over
  `EncryptedSharedPreferences` (AES256-SIV keys / AES256-GCM values).
- Conversations: `services/ConversationRepository.kt` is SQLite with every
  payload sealed by `ConversationStorageCipher` (AES-GCM, Android Keystore key,
  **row identity bound as associated data**). Panic wipes by destroying the
  Keystore key (cryptographic erasure) and deleting the database.
- This is the pattern the wallet should copy: SQLite gives atomic multi-row
  updates (reserve proofs + record an operation in one transaction), and the
  sealed-payload approach keeps proofs and secrets unreadable at rest.

## 7. Panic wipe

- `ChatViewModel.performPanicClearAllData` wipes mesh state, conversations,
  keys, media, Nostr state, bookmarks, nickname, and recreates identity. Each
  stage reports success; the completion message is shown only if **every**
  stage confirmed. Triggered by triple-tapping the brand glyph and confirmed
  in a dialog.
- The wallet must join this: wipe seed, proofs, history, and mint list, and
  report into `wipeComplete`. Funds stay recoverable only through the
  recovery words (NUT-13) plus the mint list (Nostr backup or manual entry).

## 8. QR

- Generation: ZXing (`hotspot/QrCodeGenerator.kt`, private helpers in
  `ui/VerificationSheet.kt`).
- Scanning: CameraX + ML Kit (`ScannerView`, `QRCodeAnalyzer` in
  `ui/VerificationSheet.kt`) with an accompanist permission prompt.

## 9. Testing

- JUnit4 + Robolectric 4.15 (keeps the AndroidKeyStore shim) + Mockito +
  coroutine test + OkHttp MockWebServer (`gradle/libs.versions.toml`).
- `docs/testing-conventions.md`: **no internet or public relays in the default
  suite**, injectable time/randomness/storage, golden vectors for external
  formats, fail-closed negative tests, regression test per fixed bug.
  Live-mint (testnut) tests therefore have to be opt-in.
- CI runs `testDebugUnitTest lintDebug`.

## 10. Constraints that shape the design

1. No new native dependencies (reproducible builds), so the Cashu crypto is
   implemented in Kotlin on BouncyCastle, like `NostrCrypto`.
2. No new third-party libraries unless unavoidable. Gson, OkHttp, ZXing, ML
   Kit, and CameraX are already present. CBOR and BIP-39/BIP-32 are small
   enough to implement and test against vectors.
3. Sheets, not screens. The wallet is a `BitchatBottomSheet` with an internal
   flow stack.
4. Tor-aware HTTP via `OkHttpProvider`.
5. Panic wipe must be complete and verifiable.
6. Tokens travel as plain message text for cross-client compatibility.
