# Cashu wallet: summary

Branch `cashu-wallet`, cut from `main`. Nothing was pushed.
All money involved was fake: testnut sats.

## What was built

A native Cashu ecash wallet inside Bitchat. It looks like Bitchat (Geist Mono,
terminal green, Bitchat sheets, cards, pills, and motion tokens) and follows
proven wallet UX patterns (state-machine flows, keypad amount entry, review
before commit, honest pending states, terminal success and error pages,
recovery-word confirmation).

**Protocol** (`wallet/cashu`, pure Kotlin on BouncyCastle, no new
dependencies, no native code):
- BDHKE, DLEQ verification, keyset IDs v1/v2, BIP-39/BIP-32, NUT-13 v1+v2
  deterministic secrets, P2PK key path, NUT-27 backup key.
- NUT-10/11 P2PK secrets and signatures, reusing Bitchat's BIP-340.
- Bounded CBOR, full V3/V4 token codec (short keyset IDs, DLEQ, witnesses).
- Fee math and coin selection.
- Every critical format is pinned by the official NUT test vectors, plus
  NIP-44 v2 vectors.

**Mint client** (`wallet/mint`): NUT-01…09 and NUT-23 over Bitchat's
Tor-aware OkHttp. Typed errors separate definite rejections from unknown
outcomes. POSTs are never silently retried.

**Engine** (`wallet/engine/CashuWallet.kt`):
- **Journaling:** every mint, swap, and melt is journaled atomically with its
  reserved inputs and advanced counters before it leaves the device.
- **Recovery:** NUT-09 restore and NUT-07 state checks resolve every ambiguous
  outcome after drops, kills, and restarts.
- **Operations:** Lightning receive, ecash send (offline exact-coin or swap;
  sender covers the recipient's fee; optional P2PK lock with a 7-day
  seed-derived refund), claim (DLEQ check, P2PK signing, unknown-mint gate,
  partial-spend salvage), reclaim, and Lightning send with NUT-08 change.
- **Restore and mints:** NUT-13 seed restore, keyset rotation, suspect-mint
  flag, multi-mint balances.
- **History:** pending, completed, failed, expired, and reclaimed states.

**Storage and safety**:
- SQLite rows sealed with AES-GCM under a dedicated Keystore key, with hashed
  row IDs.
- Recovery words in their own encrypted prefs.
- Panic wipe joins Bitchat's all-stages-confirmed wipe and is race-proof:
  background work is cancelled and a wiped store refuses writes.

**Backup**: NUT-27 encrypted mint list on Nostr from an unlinkable,
seed-derived key, with no client tag. On by default with a toggle.

**UI** (`wallet/ui`): one full-height Bitchat sheet with a page stack.
- Onboarding, recovery words (blurred, screenshot-blocked) with a 3-word check.
- Restore, with the Nostr mint-list lookup and manual mints.
- Home: balance, per-mint balances, recent history, backup nudge, offline
  banner.
- Receive: Lightning keypad, invoice QR, auto-polling; ecash paste, scan, or
  tap in chat, then a review with trust notes, then claim.
- Send: ecash keypad, review with fees and an optional lock, then a token card
  with QR, copy, share, and send-in-chat. Lightning: invoice, review with fee
  reserve and quote countdown, then paying, then the result.
- Mints: list, add with preview and warning, detail, coin check, remove.
- History, details, take back, and settings (hide balance, backup toggle).
- Every error has calm copy in string resources: what happened, whether funds
  are safe, and what to do next.

**Chat integration**:
- Existing payment chips show the token's status from local state and open
  the in-app claim.
- The DM header gets a send-ecash action that offers a lock to the peer's
  Nostr key when known.
- About → Settings → Wallet, and `/wallet`.
- Sending from the wallet into chat is DM-only; public posting stays the
  explicit `/pay … public`.
- Tokens stay plain `cashuB` text, so iOS and other wallets keep working.

The thinking behind the chat behavior is in `integration.md`; individual
calls are in `decisions.md` (D-001 to D-025).

## How it was tested

| Suite | Count | Result |
|---|---|---|
| Full app unit suite (`./gradlew :app:testDebugUnitTest`) | 936 tests, 9 skipped (6 are the opt-in live tests) | pass |
| Wallet unit tests: vectors, codec, engine against an in-process fake mint with real crypto and fault injection, persistence, backup, UI logic | 97 | pass |
| Live testnut (`CASHU_TESTNUT=1 ./gradlew :app:testDebugUnitTest --tests '*TestnutIntegrationTest' --rerun`) | 6: keysets, mint, send, receive with DLEQ, double spend, reclaim, melt with change, P2PK enforcement, seed restore, usd unit | pass |
| On-device panic wipe (`WalletPanicWipeInstrumentedTest`, emulator API 37, real Keystore) | 1 | pass |
| On-device Compose UI tests (`WalletUiInstrumentedTest`) | 5 | **not runnable here**, see known issues |
| `lintDebug` | no new findings on changed lines of shared files; wallet findings fixed | pass |

**Manual, on an emulator** through a debug-only host
(`WalletPreviewActivity`), with Tor on, against testnut:
- create a wallet, add testnut (keyset verification), receive 100 sat over
  Lightning, and send 21 sat as ecash (fees disclosed);
- paste our own token, which is recognized and taken back;
- over-balance entry with the Max pill;
- an "already paid" invoice, handled calmly;
- light and dark themes;
- back navigation, and screenshot blocking on the words;
- the 3-word backup check;
- clear app data, then restore from the words, with the mint list found on
  Nostr and **98 sat recovered**.

Four bugs found only on the device were fixed: the screenshot leak, sats with
decimals, the clipboard preview, and the false wallet detection. All are in
`edge-cases.md`.

All 68 edge cases (network drops mid-mint, mid-melt, and mid-swap; app kills;
double spends; malformed, spent, and unknown-mint tokens; offline mint; keyset
rotation; unit mismatch; zero, dust, and overflow; fees over amount; expired
quotes; double taps; wrong and partial seeds; plus what the hunt turned up) are
logged with result, fix, and test in `edge-cases.md`.

## Known issues and limits

1. **Compose instrumented tests cannot run on the API 37 emulator.** Espresso
   3.7 calls `InputManager.getInstance`, which API 37 removed. The repo's
   existing `ParticipantListInstrumentedTest` fails the same way. The wallet
   UI tests are written and should pass on API 36 or older, or after an
   Espresso bump, which goes through the dependency and reproducible-build
   process.
2. **The full chat screen was not exercised on device.** Bitchat's onboarding
   requires system location services, which the privacy rules forbid enabling
   even on an emulator. Chat cards were checked in a debug card gallery and in
   unit and instrumented tests; `/wallet` has a unit test; the DM header action
   was checked by code review and build only. A quick pass on a physical phone (and Mesh Lab,
   since messaging is touched) is needed before merging.
3. **Locked sends we made are not restorable from the seed** (random P2PK
   secrets). If we wipe and the recipient never claims, those coins are lost
   to us. No NUT defines deterministic P2PK secrets yet.
4. **Queued claims retry only while Bitchat runs with the wallet in use** (on
   wallet open, every 30 s while open, and at engine start). No background job
   touches the network on its own.
5. **No amountless invoices, LNURL, Lightning addresses, NUT-18 payment
   requests, or fiat display**, by choice for now (see `integration.md` §8).
6. **A cold Tor circuit** can make the first mint call fail with "Can't reach
   the mint"; Try again works. Seen live.
7. **The Mesh Lab physical-device run required by AGENTS.md for messaging
   changes was not done** (no devices attached). Message transport code is
   unchanged; tokens are ordinary text.

## Open questions for you

1. **Tapping a chat token now opens the in-app wallet** (onboarding first if
   needed) instead of an external Cashu wallet. The long-press menu still
   offers "Redeem in wallet" and the web redeemer. OK, or should external
   wallets stay the tap default until the wallet is set up?
2. **NUT-27 backup on by default** (D-009). It improves recovery a lot and
   leaks little, but it is a network publish to public relays. Keep on, or
   make it opt-in at setup?
3. **Lock default off** (D-020). Flip to on once iOS Bitchat can sign
   P2PK with the Nostr key?
4. **Discoverability.** The wallet is reachable only through About, `/wallet`,
   chat cards, and the DM coin action, with no header icon (D-004). Is that
   discreet enough, or too hidden?
5. **testnut suggestion** appears only in debug builds (D-005). Should release
   builds suggest any mint, or none?
6. **Panic dialog copy** was left unchanged so translations stay valid (D-014).
   Should it mention the wallet once translators can update it?
7. **Strings are English-only**, marked `MissingTranslation`, as with other new
   Bitchat strings. Who handles translations?
8. **Merging**: the branch touches shared files lightly (About row, ChatScreen
   host, chip status line, DM header action, `/wallet`, panic hook, and
   `ScannerView` made internal). Want it split into wallet-core and
   chat-integration PRs?

## Where things are

```
app/src/main/java/com/bitchat/android/wallet/
  cashu/    protocol (Bdhke, Bip32/39, Nut13, P2pk, Cbor, TokenCodec, Amounts, KeysetIds)
  mint/     MintApi, HttpMintApi
  engine/   CashuWallet, models, store interface, errors, Bolt11
  data/     SqliteWalletStore, MnemonicVault, record codec
  backup/   NUT-27 MintListBackup, NIP-44 v2
  ui/       WalletSheet + screens, components, copy, view model, chat card status
  WalletService.kt  wiring, panic wipe, backup publish/fetch
app/src/debug/.../testhook/WalletPreviewActivity.kt   debug-only wallet host and card gallery
docs/cashu/  audit, plan, decisions, integration, edge-cases, SUMMARY
```
