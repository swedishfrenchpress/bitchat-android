# Cashu wallet: milestone plan

Each milestone ends with `./gradlew :app:testDebugUnitTest` green and a
commit. Live testnut checks run with `-Pcashu.testnut=true`.

| # | Milestone | Deliverables |
|---|---|---|
| M0 | Setup and research | Branch, `audit.md`, UX research, `decisions.md`, this plan |
| M1 | Protocol core | `wallet/cashu`: secp256k1 helpers, BDHKE, DLEQ, keyset IDs (v1/v2), BIP-39/32, NUT-13 v1+v2, NUT-27 key, P2PK secrets and signatures, CBOR, full token codec (V3 decode, V4 encode/decode, short keyset IDs), amount split and fees. Golden-vector tests from the NUT test suites. |
| M2 | Mint client | `MintApi` interface, OkHttp/Gson implementation through `OkHttpProvider`, typed errors (network vs. definite rejection vs. malformed), NUT-06 info parsing. MockWebServer tests. `FakeMint` test double with real crypto and fault injection. |
| M3 | Wallet engine | `CashuWallet`: add/refresh mints (keyset verification, rotation), mint (bolt11), receive token (DLEQ verify, P2PK sign, swap), send (offline exact-coin or swap for change, optional P2PK lock), melt (fee reserve, NUT-08 change), state checks and spent cleanup, reclaim. An operation journal makes every mint-touching step crash-safe (deterministic outputs plus NUT-09 recovery). History with pending/completed/failed/expired/reclaimed. |
| M4 | Persistence and wipe | Sealed SQLite store, mnemonic vault, panic-wipe hook into `ChatViewModel`. Robolectric round-trip tests and a wipe test. |
| M5 | Wallet shell UI | `WalletSheet` + `WalletViewModel`, home (balance, per-mint balances, pending, history), onboarding (create / restore), recovery-words backup and confirm, entry points (About row, `/wallet`). |
| M6 | Receive | Lightning: amount keypad, invoice QR, auto-poll, success. Ecash: paste, scan, from chat, then a review card (amount, mint, unknown-mint warning, lock status, DLEQ status), then claim. |
| M7 | Send | Ecash: amount, then review with fees and optional P2PK lock, then a token card (QR, copy, share, send in chat). Lightning: invoice paste/scan, then review with fee reserve, then paying, then success, failure, or pending. |
| M8 | Mints | List with balances, add (URL, info preview, warning), mint info, remove (blocked or confirmed when it holds funds), keyset rotation handling. |
| M9 | Chat integration | State-aware claimable payment cards (unclaimed, claimed, spent, yours, pending), auto-detect `cashuA`/`cashuB`, send ecash from a DM (P2PK to a known Nostr key), honest offline/double-spend copy, reclaim from history and from your own sent card. |
| M10 | Recovery and backup | NUT-13 restore (batches of 100, stop after 3 empty batches), NUT-27 publish on mint-list change, fetch on restore, manual mint entry fallback. |
| M11 | Edge-case hunt | Fault-injection tests (network drop mid mint/melt/swap, kill mid-op, double spend, malformed/spent/unknown tokens, offline mint, keyset rotation, unit mismatch, zero/dust/overflow, fees over amount, expired quotes, double taps, wrong/partial seed). Logged in `edge-cases.md`. Opt-in testnut integration suite. |
| M12 | Polish | Screen-by-screen review against Bitchat style and the wallet UX patterns, accessibility, haptics, copy. `SUMMARY.md`. |

## Package layout

```
app/src/main/java/com/bitchat/android/wallet/
  cashu/      protocol primitives and codecs (pure JVM, no Android)
  mint/       MintApi + HTTP client
  engine/     CashuWallet, operation journal, history, errors
  data/       WalletStore (SQLite sealed), mnemonic vault
  backup/     NUT-27 Nostr mint-list backup
  ui/         WalletSheet, flows, components, view model
```

## Risk notes

- Melt is the riskiest flow: inputs are committed before the Lightning outcome
  is known. Inputs stay `PENDING` until the quote reports `PAID` or `UNPAID`
  and the proofs are re-checked.
- Swaps are recovered with NUT-09: outputs are deterministic, so after an
  ambiguous failure the wallet asks the mint to re-sign the same outputs.
- Counters advance **before** a request leaves the device, so a crash can
  never reuse a secret.
