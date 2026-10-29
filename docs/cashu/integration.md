# Cashu × Bitchat: ecash in a mesh and Nostr messenger

This document explains how Cashu ecash behaves inside Bitchat, what is honest to
promise the person using it, and what was built. It is the design rationale
behind the chat integration; `decisions.md` has the individual calls.

## 1. What a token is in a chat

A Cashu token is a bearer instrument: a string that *is* the money. Whoever
presents it to the mint first gets the value. Every property below follows
from that.

Bitchat moves text over four kinds of channel:

| Channel | Who can read a token sent there | Notes |
|---|---|---|
| Mesh DM (BLE / Wi-Fi Aware) | The recipient only (Noise end-to-end) | Relays forward ciphertext; may be held for store-and-forward |
| Nostr DM (NIP-17, mutual favorites) | The recipient only (gift-wrapped) | Relays hold ciphertext |
| Public mesh channel | Everyone in Bluetooth range, plus relaying peers | Plaintext broadcast |
| Geohash channel (Nostr) | Anyone subscribed to that geohash, anywhere | Public Nostr events |

So **a token posted publicly belongs to the fastest claimer**. Bitchat already
required `/pay <token> public` for this, and the wallet keeps that stance: its
send-in-chat entry exists only in DMs. Posting a token publicly stays a
deliberate `/pay … public`. The send flow also carries a last-chance dialog
("Whoever claims it first gets it.") for any future public entry point.
DMs are the place for money.

Tokens travel as plain message text (`cashuB…`, or legacy `cashuA…`). No new
packet type was added, so:
- iOS Bitchat and older Android builds still render tokens as payment chips and
  hand them to an external wallet;
- any Cashu wallet can redeem a token copied out of Bitchat;
- mesh limits are not a concern: a send produces at most 62 coins (swap outputs)
  or 24 (offline path), far below the 128 KiB message cap and the 60,000-char
  parser bound.

## 2. Payment cards

`CashuPaymentChip` (the existing chip) now shows a status line computed from the
wallet's **local** state only. There is no mint call and no signature math, and
results are cached per wallet snapshot, so long chats scroll cheaply.

| Status | When | Line |
|---|---|---|
| Claimable | A token we have no record of | tap to claim |
| Locked to us | P2PK lock we can sign | locked to you · tap to claim |
| Locked to someone else | P2PK lock we cannot sign | locked to someone else (muted) |
| Claimed by us | We swapped it | claimed (muted) |
| Claim queued | Claim journaled, mint not reached yet | claim queued · waiting for the mint |
| Spent | Our claim found it spent | already claimed (muted) |
| Our pending send | We sent it, not yet claimed | your token · unclaimed |
| Our send claimed | The recipient swapped it | claimed by recipient (muted) |
| Our send reclaimed | We took it back | you took it back (muted) |

Status lines are lowercase, like Bitchat's own chat system lines (D-026).

Tapping opens the claim review inside the wallet sheet, after onboarding if no
wallet exists yet; the claim then resumes automatically. Long-press keeps the
existing copy, other-wallet and web actions and adds **Take back** for our own
pending sends. Tokens are detected with the existing `CashuTokenDecoder`
(`cashuA`/`cashuB`, with or without a `cashu:` prefix, percent-encoded or not).

## 3. Offline and the double-spend problem

The mesh works without internet, but a mint is a server. Without the mint,
nobody can know whether a token is already spent. There are three levels of
assurance. The claim review states the one that matters in a single line: who
can spend the token right now.

1. **Bearer token, no DLEQ.** Nothing can be verified offline. Line: "unlocked ·
   claim it before the sender spends it".
2. **Bearer token with DLEQ (NUT-12).** The mint's signature is checked on the
   device, so the token is not counterfeit, but the sender may still have spent
   it elsewhere. Same line as above. A passing check is the quiet default; a
   failing one replaces it with "signatures don't match the mint · don't accept
   it" and hides Claim.
3. **Locked to the recipient (NUT-11) with DLEQ.** Genuine, and only the
   recipient can spend it until the lock time. The sender cannot double-spend
   because they cannot produce the recipient's signature. This is the strongest
   offline guarantee Cashu offers. Line: "locked to you until …".

Bitchat-sent tokens always include DLEQ proofs (`e`, `s`, `r`) so level 2 is
available to every recipient (D-022).

**Sending offline.** Without the mint, only coins we already hold can be sent,
and only if some subset sums exactly to the amount plus the recipient's claim
fee. While the mint is down, the review says "offline · uses coins you already
hold". If no subset fits, the failure screen offers the nearest amounts that
*can* be sent offline ("offline, you can send exactly 64 sat or 128 sat"). Locking needs a
swap, so a lock is never offered while the mint is unreachable.

**Receiving offline.** Claiming needs the mint. If it is unreachable the claim
is journaled ("Claim queued") and retried automatically on the next sync: on
wallet open and every 30 s while the wallet is open. The card shows the queued
state, and the result line repeats that the sender can still spend it until then.

## 4. Locking to a peer (P2PK)

The DM header gets a coin action (only once a wallet exists) that opens *Send
ecash* for that conversation. If Bitchat knows the peer's **main Nostr
identity**, the review offers "Lock to <name>":

- The key comes from favorites, which exchange npubs over the Noise-
  authenticated channel, so the binding between person and key is as strong as
  the favorite relationship.
- The lock carries `locktime = now + 7 days` and a refund key derived from our
  wallet seed (NUT-13 P2PK path). An unclaimed lock comes back to us after a
  week, even if we wiped and restored in between.
- The recipient's Bitchat wallet signs with its Nostr identity key
  automatically, so claiming a lock is a normal claim.
- **Off by default** (D-020): a recipient on iOS Bitchat or another wallet may
  not be able to sign with their Nostr key, which would park the funds for a
  week.
- **Not offered in geohash DMs.** Those conversations use per-geohash
  identities that the recipient's wallet does not sign with, so a lock there
  would strand the funds.
- Requires the mint to advertise NUT-11; otherwise the toggle is not shown. A
  mint without NUT-11 would treat the lock as anyone-can-spend, which is why the
  engine refuses to lock at such a mint.

## 5. Reclaiming

- A pending bearer send can be taken back at any time: History → Take back, the
  card's long-press menu, or simply pasting your own token into Claim. It is a
  race with the recipient, and the copy says so. If they won, the send flips to
  "Claimed".
- A locked send can be taken back after its lock time ("You can take this back
  after …").
- Pending sends are checked against the mint (NUT-07) on sync, so the sender's
  history and card turn into "claimed by recipient" once the token is redeemed.

## 6. Panic wipe

Triple-tapping the logo wipes the wallet with everything else (D-014): recovery
words, coins, pending tokens, history, and mint list, via Keystore key
destruction plus database and prefs deletion. The wipe reports into Bitchat's
"every stage confirmed" check. Consequences, stated in onboarding, backup, and
settings:

- Funds come back only through the recovery words (NUT-13 restore) plus the mint
  list (the NUT-27 backup on Nostr, or typed by hand).
- A seed restore also recovers **unclaimed sends**: their coins were never spent
  at the mint, so they are ours again (verified live on testnut).
- Locked tokens sent *to* us before the wipe were bound to our old Nostr
  identity, which the wipe replaces. We can no longer claim them; the sender's
  refund path returns them after 7 days.
- Locked tokens we *sent* before the wipe use random secrets (NUT-11), so a
  restore cannot find them. If the recipient never claims, those coins are lost
  to us. This is documented as a known limitation (see `SUMMARY.md`).

## 7. Privacy in hostile environments

What each party learns:

- **The mint** sees swap, mint, and melt requests with amounts and timing, and
  the `Y` values of coins whose state we check. It never sees who paid whom:
  blind signatures unlink issuance from redemption. DLEQ data is stripped
  before any proof goes to a mint. Mint traffic goes through Bitchat's Tor route
  by default (D-023), so the mint sees an exit node, not the device.
- **Relays and mesh peers** see only ciphertext for DMs. Public channels are
  public.
- **Nostr relays (backup)** see a kind-30078 event from a key derived from the
  wallet seed, unlinkable to the Bitchat identity, with NIP-44 encrypted
  content and no `client` tag. They can tell that someone backed up a Cashu
  mint list, and when. The backup can be turned off.
- **Someone holding the unlocked phone** sees the wallet only if they look for
  it: there is no header icon (About → Wallet, `/wallet`, or a payment card).
  Balances can be hidden behind a tap. History never names counterparties
  (D-015). Recovery words block screenshots on the sheet's own window. Copied
  tokens are marked sensitive so the clipboard preview hides them.
- **Forensics on a locked or wiped phone** face AES-GCM sealed rows with hashed
  row ids, under a Keystore key that panic destroys.

Remaining exposure, documented rather than hidden:
- Token strings stay in the (encrypted) conversation history after they are
  claimed. They are dead money by then, but the amount is visible in the chat.
- The mint list is metadata that can reveal affiliations, so only use mints you
  are comfortable being associated with.
- Checking pending sends links their coins together at the mint. It only runs
  while the wallet is open.

## 8. Not built (yet) and why

| Idea | Why not now |
|---|---|
| NUT-18 payment requests in chat ("request 21 sat") | Needs a cross-client message convention with iOS; worth a spec first |
| Locks to geohash identities | The recipient wallet would need to try per-geohash keys; low value, risk of stranding funds |
| Deterministic P2PK secrets (restorable locked sends) | No NUT standard yet; would diverge from other wallets |
| Lightning addresses / LNURL | Extra third-party HTTP per payment; privacy cost in hostile settings |
| Background claim of queued tokens without opening the wallet | Would need a WorkManager job touching the network on its own; deferred to keep network activity explicit |
| Wear OS wallet | Out of scope (D-017) |
| Fiat display | No price feed by design (D-016) |
