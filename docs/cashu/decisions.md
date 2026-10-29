# Cashu wallet: decision log

Decisions made while building the wallet without the developer available.
Each entry gives the choice, why, and what would change it. Newest last.

### D-001: Branch from `main`
`main` was the checked-out working branch and holds all merged Cashu work
(chips, `/pay`, decoder). The wallet branch `cashu-wallet`
starts there. Nothing is pushed and `main` is never modified.

### D-002: Pure Kotlin protocol on BouncyCastle, no CDK/FFI
The older fork wallet used CDK FFI binaries. Native blobs conflict with the
reproducible-build policy (`docs/reproducible-builds.md`), add per-ABI size,
and are harder to audit. Bitchat already does secp256k1 + BIP-340 on
BouncyCastle (`NostrCrypto`). The Cashu primitives (BDHKE, DLEQ, BIP-32/39,
NUT-13) are small and fully covered by the NUT test vectors.
*Revisit if* a reproducible, pure-JVM Cashu library becomes available.

### D-003: The wallet is a sheet with an internal flow stack
Every Bitchat secondary surface is a `BitchatBottomSheet`; there is no
navigation graph. The wallet is one full-height sheet hosting a small,
explicit flow stack (home, then send, receive, mints, history, recovery).
Flows are modeled as state machines with sealed UI states and rendered with
Bitchat components.

### D-004: Entry points are discreet
- About → Settings → **Wallet** row.
- `/wallet` command.
- Tapping a payment card in chat.
- A "send ecash" action in the DM composer menu (only once a wallet exists).

There is no header icon. The header has no free slot, and a permanently
visible wallet glyph is a tell in hostile environments.

### D-005: No preconfigured mints in release builds
Choosing a custodian is the user's decision. Debug builds offer the testnut
mint (fake sats) as a one-tap suggestion to make testing easy. Release builds
suggest nothing.

### D-006: Units
Every proof carries its keyset's unit. Balances are grouped per (mint, unit).
Lightning flows default to `sat`; other units (testnut has `usd`) can be
received, held, and sent as ecash, and are formatted with the existing
minor-unit table from `CashuTokenDecoder.formatDisplayAmount`. A token is
never mixed across units.

### D-007: Storage is sealed SQLite plus encrypted prefs for the mnemonic
This mirrors `ConversationRepository`. Proofs, operations, history, and
mints are stored as AES-GCM sealed rows (Android Keystore key, row identity as
associated data) in a dedicated database. The mnemonic lives in its own
`EncryptedSharedPreferences` file. SQLite transactions make "reserve inputs +
journal the operation" atomic, which crash recovery depends on.

### D-008: 12-word BIP-39 seed, no passphrase, backup nudged rather than forced
The wallet is created with a fresh 12-word mnemonic (NUT-13). People can
receive before backing up; once a balance exists, a persistent nudge stays on
the home screen until the words are confirmed, so people get in first and
backup health stays visible. No BIP-39 passphrase, for compatibility
with other Cashu wallets' restore.

### D-009: NUT-27 Nostr mint-list backup on by default, with a toggle
NUT-27 standardizes a seed-derived backup key, a kind-30078 event, and NIP-44
self-encryption, so a restore works in other wallets such as cashu.me. The key
is derived from the wallet seed and is **unlinkable to the Bitchat Nostr
identity**. Content is encrypted. Publishing goes through Bitchat's relay
manager, so it follows the Tor setting. It is on by default because losing
the mint list makes a seed restore much harder. A wallet-settings toggle turns
it off.

### D-010: Default test suite uses an in-process fake mint; testnut is opt-in
`docs/testing-conventions.md` forbids internet in the default suite. A
`FakeMint` in test sources implements the mint side with real BDHKE/DLEQ
crypto and fault injection (drop the response after committing, return 5xx,
rotate keysets, expire quotes). Live tests against `testnut.cashu.space` run
with `-Pcashu.testnut=true`.

### D-011: Send V4 (`cashuB`) tokens, decode both
V3 is deprecated but still common, so it is decoded but never produced.

### D-012: DLEQ
DLEQ proofs returned by the mint are verified, and a failure aborts the
operation. Sent tokens include DLEQ (`e`, `s`, `r`) so receivers can check the
mint signature offline. DLEQ is stripped before proofs go to a mint.

### D-013: P2PK to peers uses their Nostr key, with a 7-day refund path
For DM recipients whose Nostr public key Bitchat knows (favorites, Nostr DMs),
sends can be locked (NUT-11) to that key. Locked sends include
`locktime = now + 7 days` and a `refund` key derived from **our wallet seed**
(NUT-13 P2PK path `m/129373'/10'/0'/0'/i`), so a lock that never gets claimed
(for example, after the recipient's panic wipe) comes back to us even after
our own wipe and restore. Seven days covers people who are offline for a long
weekend without parking funds forever. Locking requires the mint to advertise
NUT-11 and needs a swap, so it only works online.

### D-014: Panic wipe erases the wallet entirely
Seed, proofs, history, mint list, and pending tokens are erased by destroying
the Keystore key and deleting the database and prefs. The wipe reports into
`wipeComplete` like every other stage. The existing panic dialog copy is
unchanged; the wallet's own backup screens explain that panic erases the
wallet and only the recovery words bring it back. Changing the panic dialog
would also invalidate its translations.

### D-015: History stores no counterpart identity
History records amount, fee, mint, status, timestamps, and for pending sends
the token (needed to reshare or reclaim). It does **not** store nicknames,
peer IDs, or conversation IDs, only a `via chat` flag. A seized, unlocked
phone then shows payments but not who they were with.

### D-016: No fiat conversion
No price feed: it would be an extra network call and fingerprint. Amounts are
shown in sats or the token's unit.

### D-017: Wear OS is out of scope
The wallet stays in `app/`. Nothing is added to the Wear shared-source include
list.

### D-018: Mint HTTP inherits Tor through `OkHttpProvider`
The client is derived from `OkHttpProvider.httpClient()` with longer timeouts
(melts can take a while). On a melt timeout the wallet polls the quote state
instead of assuming failure.

### D-019: Unknown mints require explicit confirmation
Adding a mint by URL or by claiming a token from an unknown mint shows the
mint's info (name, host, supported features) and a clear warning that the
mint holds the funds. Nothing is fetched from a mint until the person asks to
add or claim.

### D-020: Locking to a peer is opt-in per send
The review screen offers "Lock to <peer>" whenever the peer's Nostr key is
known and the mint enforces NUT-11, but it starts **off**. A locked token can
only be claimed by a wallet that signs with the recipient's Nostr key. Bitchat
Android does that automatically; iOS Bitchat and most standalone wallets do not
yet, so a default lock would park the funds until the 7-day refund. Locking is
the right choice for offline payments between two Bitchat Android users, and
the copy says so.

### D-021: Debug-only wallet host
`app/src/debug/.../WalletPreviewActivity` hosts the wallet sheet without the
mesh onboarding (which requires Bluetooth and system location services). It
exists for on-device QA and PR screenshots, follows the existing debug-hook
pattern, and is never in release builds.

### D-022: Tokens carry DLEQ proofs
Outgoing tokens include `e`, `s`, `r` per proof, so a recipient on the mesh can
check the mint signature without internet. That makes a QR denser (about 3x per
proof). Tokens above 1,800 characters skip the QR and offer copy/share.

### D-023: Mint traffic follows Bitchat's Tor setting
Bitchat defaults to Tor (Arti). Mint calls go through the same SOCKS route, so
a mint sees a Tor exit, not the device. On a cold Tor circuit the first call
can time out; the copy says "Can't reach the mint… check your connection or
Tor" and Try again works once the circuit is up. Live testing on an emulator
confirmed both the timeout and the retry.

### D-024: Partially spent tokens are salvaged
If a claim fails because some of the token's coins were already spent (the
sender double-spent part of it), the wallet checks each coin's state and claims
the unspent remainder. The person sees how much was salvaged instead of an
all-or-nothing failure.

### D-025: A mint that returns invalid DLEQ proofs is flagged, not trusted silently
NUT-12 requires verifying DLEQ proofs from the mint. Failing the operation
outright would strand coins the mint already swapped. Instead the wallet keeps
the unblinded coins without the bad proof, marks the mint as suspect, and shows
a warning on it. A bad DLEQ can be a tagging attempt, so the person should move
their funds elsewhere.
