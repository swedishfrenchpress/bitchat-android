# Cashu wallet: edge cases

Each case lists what was tried, what happened, the fix if one was needed, and
the test that keeps it fixed. Test classes are under
`app/src/test/java/com/bitchat/android/wallet/`. The fake mint
(`testsupport/FakeMint.kt`) signs with real keys and injects faults; "live"
means the opt-in testnut suite (`TestnutIntegrationTest`, run with
`CASHU_TESTNUT=1 ./gradlew :app:testDebugUnitTest --tests '*TestnutIntegrationTest' --rerun`).

## Network and process failures

| # | Case | Result | Fix | Test |
|---|---|---|---|---|
| 1 | Network drops **mid-mint**: the mint signs, the response is lost | Shown as "Checking with the mint"; nothing minted twice; balance appears on recovery | Outputs are deterministic and journaled before the call; recovery asks NUT-09 restore for the same outputs | `CashuWalletTest` · lost mint response is recovered with restore and never double mints |
| 2 | Network drops **mid-swap** (claim): the mint spends inputs, response lost, then the app is killed | Claim finishes after restart | Journal survives restart; recovery restores the outputs | `CashuWalletTest` · lost swap response during claim is recovered after an app restart |
| 3 | Network drops **mid-melt**: Lightning paid, response lost | "Payment pending", never "failed"; settles to paid with change after restart | Recovery reads the melt quote state and restores the blank change outputs | `CashuWalletTest` · melt with a lost response is settled from the quote state |
| 4 | Melt stays **PENDING** at the mint, then pays or fails | Funds stay reserved, then complete or return in full | `resolveMelt` settles on PAID/UNPAID and checks inputs before releasing | `CashuWalletTest` · pending payment holds funds until the mint settles either way |
| 5 | Mint returns a **malformed** body after acting | Reported as outcome unknown; recovery completes it | `Malformed` is treated as ambiguous, never as rejection | `WalletEdgeCaseTest` · malformed swap response is resolved through restore |
| 6 | **App killed** between journaling and sending | Recovery checks restore, then input states, and retries or releases | Every op is "possibly sent" once journaled; the resolution is idempotent | `CashuWalletTest` · lost swap response during claim… (restart) |
| 7 | **Storage fails** while reserving inputs | Nothing reaches the mint, balance unchanged | Commit before network, always | `CashuWalletTest` · storage failure stops the operation before anything reaches the mint |
| 8 | OkHttp **silently replays** a POST after a connection failure | Could turn a lost response into a confusing second answer | `retryOnConnectionFailure` disabled for POSTs; the journal decides retries | `HttpMintApiTest` · dropped connection on a swap is unreachable and is not retried silently |
| 9 | **Mint offline** while claiming | "Claim queued", completes when the mint returns | Claim stays journaled; recovery retries | `CashuWalletTest` · claim survives the mint going offline and finishes on recovery |
| 10 | **Mint offline** while sending | Exact coins still go out offline; otherwise suggests sendable amounts | Offline exact-coin path plus `offlineAmountsNear` | `CashuWalletTest` · exact-coin send works offline and the recipient claims it |
| 11 | **Tor** circuit cold on the first mint call (emulator, live) | "Can't reach the mint"; Try again succeeded | Copy names Tor as a likely cause; nothing to fix in code | Manual, on device |

## Double spends and token validity

| # | Case | Result | Fix | Test |
|---|---|---|---|---|
| 12 | Claiming the **same token twice** | "Already yours: you claimed this token before." | Token fingerprint recorded in history | `CashuWalletTest` · claiming twice is recognised and a spent token is refused |
| 13 | Claiming a token **someone else claimed** (fake and live mint) | "Already claimed: someone already claimed this token, so nothing was added." | 11001 maps to a definite failure | same test; live: `TestnutIntegrationTest` · mint, send, receive, double spend, reclaim |
| 14 | Token **partly double-spent** by the sender | Originally the whole claim failed. Now the unspent remainder is claimed and noted "Part was already spent" | Salvage via NUT-07 (D-024) | `WalletEdgeCaseTest` · partially double-spent token claims the unspent remainder |
| 15 | **Malformed** token text, truncated CBOR, 5,000-deep nesting | "Not a valid token"; no crash or stack overflow | Bounded CBOR reader (depth, items, string size) | `TokenCodecTest` · truncated and garbage CBOR fails closed |
| 16 | **Forged DLEQ** in a received token | Review shows "didn't check out"; claim refused | DLEQ verified before swapping | `CashuWalletTest` · malformed, unknown-mint and forged tokens fail closed |
| 17 | Mint returns a **bad DLEQ** on its own response | Originally risked stranding coins the mint already swapped. Now the coins are kept without the proof and the mint is flagged as suspect | D-025 | `WalletEdgeCaseTest` · mint returning a bad DLEQ keeps the coins and flags the mint |
| 18 | Token from an **unknown mint** | Review warns and asks "Trust and claim" | Unknown mints need explicit confirmation | `CashuWalletTest` · …unknown-mint… |
| 19 | **Multi-mint** V3 token | "Token mixes mints: ask the sender for one token per mint" | Explicit rejection | `TokenCodecTest` · multi-mint v3 tokens are rejected explicitly |
| 20 | V4 token with **short keyset IDs** | Claimed normally | Short IDs resolved against the mint; ambiguity rejected | `WalletEdgeCaseTest` · v4 tokens with short keyset ids resolve against the mint |
| 21 | Token **locked to someone else** (fake and live mint) | "Locked to someone else"; mint enforces it | NUT-11 lock check before swap | `CashuWalletTest` · locked send is only claimable by its key…; live P2PK test |
| 22 | Taking back a **lock before its time** | "Not yet: you can take this back after …" | Locktime check | same test |
| 23 | Pasting **our own pending token** into Claim | Offered as "Take back"; reclaimed | Receive detects our own pending send | `CashuWalletTest` · pasting our own pending token takes it back; on device |

## Amounts, fees and units

| # | Case | Result | Fix | Test |
|---|---|---|---|---|
| 24 | **Zero / negative** amounts | Refused before any mint call | Validation in engine and keypad (no leading zero) | `CashuWalletTest` · amount limits and zero amounts…; `TokenCodecTest` · zero, negative and overflowing amounts are rejected |
| 25 | **Overflowing** token totals (> 2.1e15) and non-integer amounts | "Not a valid token" | `MAX_AMOUNT` bound with checked sums | `TokenCodecTest` · zero, negative and overflowing… |
| 26 | Keypad entry of 13+ digits | Rejected with shake and haptic | `AmountDigits` 12-digit cap | `WalletEdgeCaseTest` · amount digits refuse leading zeros and absurd lengths |
| 27 | **Dust**: fee ≥ value (1 sat coin at 1000 ppk) | "Fees would take it all" | Net amount checked before swapping | `CashuWalletTest` · insufficient balance and dust that fees would eat are refused |
| 28 | **Fees** (testnut 100 ppk): recipient gets less than typed | The sender covers the claim fee, so the recipient gets exactly the typed amount; review shows fees and total | Send outputs sized for amount + claim fee | `CashuWalletTest` · with fees the sender covers the claim…; live send |
| 29 | Amount over **mint limits** | "Amount not allowed"; inline line on the keypad | NUT-04 limits enforced | `CashuWalletTest` · amount limits… |
| 30 | **Insufficient** balance | Inline "More than your … balance" with a Max pill; engine refuses with numbers | | `CashuWalletTest` · insufficient balance… ; on device |
| 31 | **Unit mismatch** (usd token labelled sat) | "Unit not supported"; never mixed | Keyset unit must match token unit | `CashuWalletTest` · tokens in another unit are held separately and never mixed |
| 32 | **usd** balance next to sats | Kept and shown separately (live) | | `TestnutIntegrationTest` · usd unit is minted and kept apart from sats |
| 33 | Sats rendered as **"0.00 sat"** (found on device) | Android invents a 2-digit currency for "SAT" | Only real ISO currencies get minor digits | On device; formatting shared with the chip decoder's table |

## Quotes and invoices

| # | Case | Result | Fix | Test |
|---|---|---|---|---|
| 34 | **Expired mint quote** never paid | History "Expired unpaid"; nothing minted | | `CashuWalletTest` · unpaid quote stays pending…; `WalletEdgeCaseTest` · unpaid quote that expires… |
| 35 | **Expired melt quote** at Pay | "Quote expired… Nothing was spent", with "Get a new quote" | Expiry checked before reserving; review counts down | `CashuWalletTest` · expired melt quote and already paid invoice are refused |
| 36 | Invoice **already paid** (testnut auto-pays its own invoices; found on device) | "Already paid… Nothing was spent." History note originally said "Refused by the mint" | Dedicated history note | On device |
| 37 | Lightning **payment fails** | "Payment failed… Your ecash is back in your wallet." | Inputs released only after the definite failure | `CashuWalletTest` · failed lightning payment returns the funds |
| 38 | Not an invoice / bad multiplier / bad charset | "That doesn't look like a Lightning invoice." | `Bolt11.parse` | `WalletEdgeCaseTest` · bolt11 parsing reads amounts and rejects junk |

## Double taps and concurrency

| # | Case | Result | Fix | Test |
|---|---|---|---|---|
| 39 | **Double tap** on Create token / Pay / Claim | One operation | Each flow uses one history id; the engine returns the existing entry; buttons switch to loading | `CashuWalletTest` · send retried with the same id is idempotent |
| 40 | Recovery running while the same op is in flight | Skipped | In-flight set | Covered by recovery tests |
| 41 | Recovery **silently skipped** an op it was asked to resolve (found in review) | `handleRejection` called the guarded resolver while the op was marked in-flight | Unguarded resolution for rejections | Covered by the 11003 resync test |

## Keysets and mints

| # | Case | Result | Fix | Test |
|---|---|---|---|---|
| 42 | **Keyset rotation** | Old coins stay spendable and are spent first; new coins come from the active keyset | Inactive keysets kept and merged; selection prefers them | `CashuWalletTest` · keyset rotation keeps old coins spendable and spends them first |
| 43 | Selection trimmed away the inactive coins it preferred (found by test) | Fixed | Trimming skips inactive-keyset coins | same test; `P2pkAndAmountsTest` · selection covers amount… |
| 44 | Mint keys **don't match** their keyset ID | "Mint looks wrong"; mint not added | IDs verified (v1/v2) on add and refresh | `CashuWalletTest` · mint whose keys do not match their id is refused |
| 45 | Keyset ID **reused** by a different mint | Refused | NUT-02 collision check | `verifyKeysets` (engine) |
| 46 | Removing a mint **with funds** | Confirmation with the amount at stake | | `CashuWalletTest` · removing a mint with funds needs confirmation |
| 47 | Plain **http://** mint URL | Refused except .onion and loopback | `MintUrl.normalize` | `TokenCodecTest` · mint urls normalize…; `HttpMintApiTest` |

## Recovery words and restore

| # | Case | Result | Fix | Test |
|---|---|---|---|---|
| 48 | **Wrong seed** restore | "No unspent ecash at these mints"; nothing added | | `CashuWalletTest` · restore with a different seed finds nothing |
| 49 | **Partial seed**: 11 words, misspelled word, swapped words | "11 of 12 words", "Word 3 isn't a recovery word", "These words don't form a valid phrase" | BIP-39 validation with positions | `Nut13Test` · mnemonic validation pinpoints the problem |
| 50 | Restore with spent and unspent coins (fake and live mint) | Only unspent restored; counters move past everything found | NUT-07 after NUT-09 | `CashuWalletTest` · seed restore recovers unspent coins…; live restore |
| 51 | Restore brings back **unclaimed sends** (found live) | Their coins were never spent, so they come back | Expected behavior; documented | `TestnutIntegrationTest` · seed restore recovers the balance from testnut |
| 52 | **Counter collision** after a short restore or the same seed on two phones | Mint answers 11003. Originally this risked a wrong success | Check whether the inputs were really spent; if not, release and jump counters 1,000 ahead | `CashuWalletTest` · counter collision after a short restore is resynced instead of losing funds |
| 53 | Backup check with a phrase that **repeats a word** | Could offer fewer than 4 choices | Decoys topped up from the wordlist | `WalletEdgeCaseTest` · backup confirmation demands the right word… |
| 54 | NUT-27 backup from **another seed**, forged content, or older events | Ignored; the newest valid event wins | Signature, author, d-tag and MAC checked | `WalletPersistenceRobolectricTest` · NUT-27 backup round trips… |

## Privacy and storage

| # | Case | Result | Fix | Test |
|---|---|---|---|---|
| 55 | Recovery words **screenshottable** (found on device) | `FLAG_SECURE` was on the activity, but the sheet renders in its own dialog window | Flag the sheet window | On device (screencap) |
| 56 | Copied token **visible in the clipboard preview** (found on device) | Android 13+ showed the token on screen | Clip marked `EXTRA_IS_SENSITIVE` | On device |
| 57 | Proof secrets or mint URLs in plaintext on disk | None found in the DB or WAL | Sealed rows, hashed row ids | `WalletPersistenceRobolectricTest` · nothing sensitive is stored in plaintext |
| 58 | Sealed row **copied** onto another row | Rejected and skipped | Row identity as AES-GCM associated data | `WalletPersistenceRobolectricTest` · a record moved to another row is rejected |
| 59 | **Panic wipe** with funds | Database, key and words gone; residue unreadable | Keystore key destroyed | `WalletPersistenceRobolectricTest` · wipe deletes the database…; `CashuWalletTest` · wipe erases everything |
| 60 | Panic wipe when **no wallet was ever created** | Reports success without touching the Keystore | Only touch what exists | `WalletPersistenceRobolectricTest` · panic wipe with no wallet ever created reports success |
| 61 | DLEQ data **leaking to the mint** | Never sent | `Proof.forMint()`; the fake mint fails the test if DLEQ arrives | Every engine test (FakeMint assertion) |
| 62 | Secrets in **logs** | Model `toString`s redact secrets, amounts are kept; mint `detail` text is never logged | | `Nut13Test` · secret redaction keeps material out of logs |
| 63 | **Panic during background work**: the initial load, a settings write or the backup publish commits *after* the wipe (found by test) | Would have recreated the database under a fresh Keystore key, leaving post-panic data on disk | Panic cancels all wallet background work, and a wiped store refuses every later write | `WalletPanicWipeInstrumentedTest` (on device, real Keystore) |
| 64 | "Wallet exists" was true for people who only **opened** the wallet sheet (found on device) | The encrypted prefs file appears before any words exist, so the DM header would show the ecash action to non-users | A plain marker written only when words are stored; removed on panic | `WalletServiceRobolectricTest` · wallet marker tracks stored words, not the encrypted file |
| 65 | Token minted on a keyset the receiver **has not seen yet** (mint rotated keys; found in review) | Was refused as "Mint looks wrong" | Refresh the mint's keysets before judging the token | `WalletEdgeCaseTest` · token from a keyset the receiver has not seen yet… |
| 66 | Mint reports a melt **PAID without change** signatures (found in review) | The unused fee reserve (8 of 10 sat in the test) was lost | Ask for the blank outputs' signatures via NUT-09 restore | `WalletEdgeCaseTest` · paid melt without change in the response recovers the change… |
| 67 | **Add mint never closed** after saving (found on device; regression from in-flow back) | The flow finished by calling back, which the flow itself swallows while saving | Flows that finish on their own pop the page directly | `WalletNavigationRobolectricTest` · adding a mint closes the add page once it is saved |
| 68 | **12-digit amount at 2× font scale** | Fits: the hero amount auto-sizes instead of clipping; the mint limit shows inline | Step-based text auto-size | On device |

