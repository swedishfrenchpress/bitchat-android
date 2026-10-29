package com.bitchat.android.wallet.engine

import com.bitchat.android.wallet.cashu.Amounts
import com.bitchat.android.wallet.cashu.Bdhke
import com.bitchat.android.wallet.cashu.BlindSignature
import com.bitchat.android.wallet.cashu.BlindedMessage
import com.bitchat.android.wallet.cashu.DleqProof
import com.bitchat.android.wallet.cashu.Hex
import com.bitchat.android.wallet.cashu.Keyset
import com.bitchat.android.wallet.cashu.KeysetIds
import com.bitchat.android.wallet.cashu.MeltQuote
import com.bitchat.android.wallet.cashu.MeltQuoteState
import com.bitchat.android.wallet.cashu.MintInfo
import com.bitchat.android.wallet.cashu.MintQuote
import com.bitchat.android.wallet.cashu.MintQuoteState
import com.bitchat.android.wallet.cashu.MintUrl
import com.bitchat.android.wallet.cashu.Nut13
import com.bitchat.android.wallet.cashu.P2pk
import com.bitchat.android.wallet.cashu.Proof
import com.bitchat.android.wallet.cashu.ProofState
import com.bitchat.android.wallet.cashu.Secp256k1
import com.bitchat.android.wallet.cashu.Token
import com.bitchat.android.wallet.cashu.TokenCodec
import com.bitchat.android.wallet.cashu.TokenDecodeException
import com.bitchat.android.wallet.mint.CashuErrorCode
import com.bitchat.android.wallet.mint.MintApi
import com.bitchat.android.wallet.mint.MintApiFactory
import com.bitchat.android.wallet.mint.MintException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigInteger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The Cashu wallet engine: every operation that touches proofs or a mint goes through here.
 *
 * Safety model:
 * - Every state change is committed atomically to [store] before the network is touched.
 *   Inputs move to [ProofStatus.RESERVED] and the blinded outputs are journaled as an
 *   [Operation] in the same commit, and counters advance in that commit too, so a secret is
 *   never reused.
 * - An ambiguous failure (timeout, lost response, 5xx) is never reported as "failed". The
 *   operation stays in the journal and [recover] resolves it with NUT-09 restore and NUT-07
 *   state checks.
 * - Network calls run outside [mutex], so a slow Lightning payment never blocks other
 *   operations; reserved inputs keep them from being double-used.
 */
class CashuWallet(
    private val store: WalletStore,
    private val apiFactory: MintApiFactory,
    private val seedProvider: () -> ByteArray?,
    /** Private keys we can sign P2PK locks with besides our own refund keys (Nostr identity). */
    private val extraSigningKeys: () -> List<BigInteger> = { emptyList() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var ledger = Ledger()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val apis = ConcurrentHashMap<String, MintApi>()
    private val _state = MutableStateFlow(WalletState())
    val state: StateFlow<WalletState> = _state.asStateFlow()

    // MARK: - Lifecycle

    suspend fun load() = mutex.withLock {
        ledger = Ledger.from(store.load())
        publish()
    }

    /** Erase everything. Returns true only if storage confirmed the wipe. */
    suspend fun wipe(): Boolean = mutex.withLock {
        val ok = runCatching { store.wipe() }.getOrDefault(false)
        ledger = Ledger()
        apis.clear()
        inFlight.clear()
        refundKeyCache = emptyList()
        synchronized(cardCache) { cardCache.clear() }
        publish()
        ok
    }

    fun refreshSeedState() = publish()

    // MARK: - Mints

    data class MintPreview(val url: String, val info: MintInfo, val keysets: List<Keyset>) {
        val units: Set<String> get() = keysets.map { it.unit }.toSet()
    }

    /** Fetches and verifies a mint without storing it. */
    suspend fun previewMint(rawUrl: String): MintPreview {
        val url = MintUrl.normalizeOrNull(rawUrl) ?: throw WalletException(WalletError.MintNotFound)
        val api = api(url)
        val info = call { api.info() }
        val infos = call { api.keysets() }
        val known = ledger.mints[url]
        // Keys for active keysets, plus any inactive keyset we already hold.
        val wanted = infos.filter { it.active || known?.keyset(it.id) != null }
        val keysets = wanted.map { ks ->
            known?.keyset(ks.id)?.takeIf { it.keys.isNotEmpty() }?.copy(active = ks.active)
                ?: call { api.keys(ks.id) }.copy(
                    unit = ks.unit, active = ks.active, inputFeePpk = ks.inputFeePpk, finalExpiry = ks.finalExpiry,
                )
        }
        verifyKeysets(url, keysets)
        if (keysets.none { it.active }) throw WalletException(WalletError.MintKeysInvalid)
        return MintPreview(url, info, keysets)
    }

    suspend fun addMint(preview: MintPreview): MintRecord = mutex.withLock {
        val now = clock()
        val existing = ledger.mints[preview.url]
        val merged = mergeKeysets(existing?.keysets.orEmpty(), preview.keysets)
        val record = MintRecord(preview.url, preview.info, merged, existing?.addedAt ?: now, now)
        commitLocked(WalletChanges(putMints = listOf(record)))
        record
    }

    suspend fun addMint(rawUrl: String): MintRecord = addMint(previewMint(rawUrl))

    /** Refreshes keysets (rotation) and info. Network errors are swallowed: stale data stays. */
    suspend fun refreshMint(url: String): MintRecord? {
        val preview = runCatching { previewMint(url) }.getOrElse {
            if (it is CancellationException) throw it
            return null
        }
        return addMint(preview)
    }

    suspend fun removeMint(url: String, force: Boolean = false) = mutex.withLock {
        val proofs = ledger.proofs.values.filter { it.mintUrl == url }
        if (proofs.isNotEmpty() && !force) throw WalletException(WalletError.MintHasFunds)
        if (ledger.operations.values.any { it.mintUrl == url }) throw WalletException(WalletError.OperationInProgress)
        commitLocked(WalletChanges(removeMints = listOf(url), removeProofs = proofs.map { it.proof.secret }))
    }

    fun mint(url: String): MintRecord? = ledger.mints[url]

    // MARK: - Lightning receive (NUT-04 / NUT-23)

    suspend fun createMintQuote(mintUrl: String, amount: Long, unit: String = "sat", historyId: String = newId()): HistoryEntry {
        ledger.history[historyId]?.let { return it }
        val mint = requireMint(mintUrl)
        if (amount <= 0 || amount > TokenCodec.MAX_AMOUNT) throw WalletException(WalletError.InvalidAmount)
        mint.info?.let { info ->
            if (info.mintingDisabled) throw WalletException(WalletError.MintingDisabled)
            info.mintSetting("bolt11", unit)?.let { setting ->
                if ((setting.minAmount != null && amount < setting.minAmount) ||
                    (setting.maxAmount != null && amount > setting.maxAmount)
                ) throw WalletException(WalletError.AmountOutOfRange(setting.minAmount, setting.maxAmount))
            }
        }
        if (mint.activeKeyset(unit) == null) throw WalletException(WalletError.UnitUnsupported)
        val quote = call { api(mintUrl).mintQuote(amount, unit) }
        if (quote.unit != unit) throw WalletException(WalletError.UnitUnsupported)
        val now = clock()
        val entry = HistoryEntry(
            id = historyId, kind = HistoryKind.LIGHTNING_RECEIVE, status = HistoryStatus.PENDING,
            amount = amount, fee = 0, unit = unit, mintUrl = mintUrl, createdAt = now, updatedAt = now,
            note = HistoryNote.WAITING_FOR_PAYMENT, invoice = quote.request, quoteId = quote.quote,
            quoteExpiry = quote.expiry,
        )
        mutex.withLock { commitLocked(WalletChanges(putHistory = listOf(entry))) }
        return entry
    }

    /**
     * Checks a pending Lightning receive and mints once paid. Safe to call repeatedly and
     * concurrently; returns the latest entry.
     */
    suspend fun checkMintQuote(historyId: String): HistoryEntry {
        val entry = ledger.history[historyId] ?: throw WalletException(WalletError.Internal)
        if (!entry.isPending || entry.kind != HistoryKind.LIGHTNING_RECEIVE) return entry
        ledger.operations.values.firstOrNull { it.historyId == historyId }?.let { op ->
            resolveOperation(op)
            return ledger.history.getValue(historyId)
        }
        val quoteId = entry.quoteId ?: return entry
        val quote = call { api(entry.mintUrl).checkMintQuote(quoteId) }
        return when {
            quote.mintable > 0 -> mintPaidQuote(entry, quote)
            quote.state == MintQuoteState.ISSUED || ((quote.amountPaid ?: 0) > 0 && quote.mintable == 0L) ->
                finishHistory(historyId, HistoryStatus.FAILED, HistoryNote.ALREADY_SPENT)
            isExpired(entry.quoteExpiry) ->
                finishHistory(historyId, HistoryStatus.EXPIRED, HistoryNote.QUOTE_EXPIRED)
            else -> entry
        }
    }

    private suspend fun mintPaidQuote(entry: HistoryEntry, quote: MintQuote): HistoryEntry {
        val op = mutex.withLock {
            if (ledger.operations.values.any { it.historyId == entry.id }) throw WalletException(WalletError.OperationInProgress)
            val mint = requireMint(entry.mintUrl)
            val keyset = mint.activeKeyset(entry.unit) ?: throw WalletException(WalletError.UnitUnsupported)
            val amount = minOf(quote.mintable, entry.amount.takeIf { it > 0 } ?: quote.mintable)
            val (outputs, counters) = deterministicOutputs(keyset, Amounts.split(amount, keyset.amounts))
            val op = Operation(
                id = newId(), kind = OperationKind.MINT, mintUrl = entry.mintUrl, unit = entry.unit,
                historyId = entry.id, inputs = emptyList(), outputs = outputs, quoteId = quote.quote,
                createdAt = clock(),
            )
            commitLocked(WalletChanges(putOperations = listOf(op), putCounters = counters))
            op
        }
        return runOperation(op) {
            val signatures = api(op.mintUrl).mint(op.quoteId!!, op.outputs.map(::blinded))
            finalizeOutputs(op, signatures.pairWith(op.outputs), HistoryStatus.COMPLETED, HistoryNote.NONE)
        }
    }

    // MARK: - Ecash receive

    enum class DleqStatus { VERIFIED, ABSENT, INVALID, UNCHECKED }

    sealed interface LockStatus {
        data object None : LockStatus
        data class ToUs(val locktime: Long?) : LockStatus
        data class RefundableByUs(val locktime: Long) : LockStatus
        data class ToSomeoneElse(val locktime: Long?) : LockStatus
        data object Unsupported : LockStatus
    }

    data class TokenInspection(
        val raw: String,
        val token: Token,
        val mintKnown: Boolean,
        val dleq: DleqStatus,
        val lock: LockStatus,
        val alreadyClaimed: Boolean,
        val isOurPendingSend: Boolean,
        /** Claim fee, if the token's keysets are known. */
        val fee: Long?,
    ) {
        val receiveAmount: Long? get() = fee?.let { token.amount - it }
    }

    /** Offline analysis of a token: who can spend it and whether the mint signature checks out. */
    fun inspect(raw: String): TokenInspection {
        val token = decode(raw)
        val mint = ledger.mints[token.mint]
        val fingerprint = fingerprint(token)
        val alreadyClaimed = ledger.history.values.any {
            it.tokenFingerprint == fingerprint && it.kind == HistoryKind.ECASH_RECEIVE && it.status == HistoryStatus.COMPLETED
        }
        val ourSend = token.proofs.any { ledger.proofs[it.secret]?.status == ProofStatus.SENT_PENDING }
        val resolved = mint?.let { m -> runCatching { resolveKeysets(token.proofs, m) }.getOrNull() }
        val dleq = when {
            token.proofs.none { it.dleq != null } -> DleqStatus.ABSENT
            mint == null || resolved == null -> DleqStatus.UNCHECKED
            resolved.all { p ->
                val key = mint.keyset(p.keysetId)?.keyFor(p.amount)
                key != null && p.dleq != null && Bdhke.verifyProofDleq(p, key)
            } -> DleqStatus.VERIFIED
            else -> DleqStatus.INVALID
        }
        val fee = if (mint != null && resolved != null) Amounts.inputFee(resolved, mint.feePpk) else null
        return TokenInspection(raw, token, mint != null, dleq, lockStatus(token.proofs), alreadyClaimed, ourSend, fee)
    }

    /**
     * Claims a token into this wallet. The mint must already be added (the UI asks the person
     * first for unknown mints). Idempotent per [historyId].
     */
    suspend fun receive(raw: String, historyId: String = newId(), viaChat: Boolean = false): HistoryEntry {
        ledger.history[historyId]?.let { return it }
        val inspection = inspect(raw)
        if (inspection.alreadyClaimed) throw WalletException(WalletError.TokenAlreadyClaimed)
        if (inspection.dleq == DleqStatus.INVALID) throw WalletException(WalletError.TokenSignatureInvalid)
        if (inspection.isOurPendingSend) {
            val sendEntry = ledger.proofs[inspection.token.proofs.first().secret]?.ref?.let { ledger.history[it] }
            if (sendEntry != null) return reclaim(sendEntry.id)
        }
        val token = inspection.token
        val mint = ledger.mints[token.mint] ?: throw WalletException(WalletError.UnknownMint)
        val op = mutex.withLock {
            val inputs = signForSpend(resolveKeysets(token.proofs, mint), allowRefund = false)
            if (inputs.any { mint.keyset(it.keysetId)?.unit != token.unit }) throw WalletException(WalletError.UnitUnsupported)
            val keyset = mint.activeKeyset(token.unit) ?: throw WalletException(WalletError.UnitUnsupported)
            val fee = Amounts.inputFee(inputs, mint.feePpk)
            val net = token.amount - fee
            if (net <= 0) throw WalletException(WalletError.FeesExceedAmount(fee))
            val (outputs, counters) = deterministicOutputs(keyset, Amounts.split(net, keyset.amounts))
            val now = clock()
            val entry = HistoryEntry(
                id = historyId, kind = HistoryKind.ECASH_RECEIVE, status = HistoryStatus.PENDING,
                amount = net, fee = fee, unit = token.unit, mintUrl = mint.url, createdAt = now, updatedAt = now,
                note = HistoryNote.WAITING_FOR_MINT, memo = token.memo, viaChat = viaChat,
                tokenFingerprint = fingerprint(token), lockedToPeer = inspection.lock is LockStatus.ToUs,
            )
            val op = Operation(
                id = newId(), kind = OperationKind.RECEIVE, mintUrl = mint.url, unit = token.unit,
                historyId = historyId, inputs = inputs, outputs = outputs, createdAt = now,
            )
            commitLocked(WalletChanges(putOperations = listOf(op), putCounters = counters, putHistory = listOf(entry)))
            op
        }
        return runOperation(op) { swapAndFinalize(op, HistoryStatus.COMPLETED) }
    }

    // MARK: - Ecash send

    data class LockRequest(
        /** Compressed secp256k1 key of the recipient. */
        val pubkey: String,
        val locktimeSeconds: Long,
    )

    data class SendPreview(
        val mintUrl: String,
        val unit: String,
        val amount: Long,
        /** Our swap fee plus the recipient's claim fee, which we cover. */
        val fee: Long,
        /** Can be built from coins we already hold, without contacting the mint. */
        val offline: Boolean,
    ) {
        val total: Long get() = amount + fee
    }

    fun previewSend(mintUrl: String, amount: Long, unit: String = "sat", lock: LockRequest? = null): SendPreview {
        val plan = planSend(requireMint(mintUrl), amount, unit, lock)
        return SendPreview(mintUrl, unit, amount, plan.fee, plan.exact != null)
    }

    /** Exact amounts that could be sent offline right now, nearest below and above [target]. */
    fun offlineAmountsNear(mintUrl: String, unit: String, target: Long): Pair<Long?, Long?> {
        val proofs = available(mintUrl, unit)
        val sums = HashSet<Long>()
        // Subset sums over the 16 largest coins is plenty for a suggestion.
        val coins = proofs.map { it.amount }.sortedDescending().take(16)
        for (mask in 1 until (1 shl coins.size)) {
            var s = 0L
            for (i in coins.indices) if (mask and (1 shl i) != 0) s += coins[i]
            sums += s
        }
        return sums.filter { it < target }.maxOrNull() to sums.filter { it > target }.minOrNull()
    }

    private class SendPlan(val exact: List<Proof>?, val swapInputs: Amounts.Selection?, val sendAmounts: List<Long>, val fee: Long)

    private fun planSend(mint: MintRecord, amount: Long, unit: String, lock: LockRequest?): SendPlan {
        if (amount <= 0 || amount > TokenCodec.MAX_AMOUNT) throw WalletException(WalletError.InvalidAmount)
        val keyset = mint.activeKeyset(unit) ?: throw WalletException(WalletError.UnitUnsupported)
        val proofs = available(mint.url, unit)
        val balance = proofs.sumOf { it.amount }
        val fees = mint.feePpk
        if (lock == null) {
            // Offline path: coins summing to amount + the recipient's claim fee on those coins.
            for (claimFee in 0L..3L) {
                val match = Amounts.exactMatch(proofs, amount + claimFee) ?: continue
                if (Amounts.inputFee(match, fees) == claimFee) return SendPlan(match, null, emptyList(), claimFee)
            }
        }
        // Swap path: outputs worth amount + recipient's claim fee on those outputs.
        var sendAmounts = Amounts.split(amount, keyset.amounts)
        repeat(4) {
            val claimFee = Amounts.inputFee(sendAmounts.size, keyset.inputFeePpk)
            sendAmounts = Amounts.split(amount + claimFee, keyset.amounts)
        }
        val sendTotal = sendAmounts.sum()
        val selection = Amounts.select(proofs, sendTotal, fees, inactiveKeysets(mint))
            ?: throw WalletException(WalletError.InsufficientBalance(balance, sendTotal + Amounts.inputFee(1, keyset.inputFeePpk)))
        return SendPlan(null, selection, sendAmounts, selection.fee + (sendTotal - amount))
    }

    /**
     * Creates an outgoing token. Without a [lock] and with matching coins this works offline;
     * otherwise it swaps at the mint for exact denominations (and the P2PK lock).
     */
    suspend fun send(
        mintUrl: String,
        amount: Long,
        unit: String = "sat",
        lock: LockRequest? = null,
        memo: String? = null,
        viaChat: Boolean = false,
        historyId: String = newId(),
    ): HistoryEntry {
        ledger.history[historyId]?.let { return it }
        val cleanMemo = memo?.filterNot(Char::isISOControl)?.trim()?.take(MAX_MEMO)?.takeIf { it.isNotEmpty() }
        val prepared = mutex.withLock {
            val mint = requireMint(mintUrl)
            if (lock != null && mint.info?.supports(11) != true) throw WalletException(WalletError.MintFeatureMissing)
            val plan = planSend(mint, amount, unit, lock)
            val now = clock()
            val base = HistoryEntry(
                id = historyId, kind = HistoryKind.ECASH_SEND, status = HistoryStatus.PENDING,
                amount = amount, fee = plan.fee, unit = unit, mintUrl = mintUrl, createdAt = now, updatedAt = now,
                note = HistoryNote.WAITING_FOR_CLAIM, memo = cleanMemo, viaChat = viaChat,
                lockedUntil = lock?.locktimeSeconds, lockedToPeer = lock != null,
            )
            if (plan.exact != null) {
                val tokenObject = Token(mintUrl, unit, plan.exact, cleanMemo)
                val entry = base.copy(token = encode(tokenObject), tokenFingerprint = fingerprint(tokenObject))
                commitLocked(WalletChanges(
                    putProofs = plan.exact.map { stored(it, mintUrl, unit, ProofStatus.SENT_PENDING, historyId) },
                    putHistory = listOf(entry),
                ))
                return entry
            }
            val selection = plan.swapInputs!!
            val keyset = mint.activeKeyset(unit)!!
            val changeAmount = selection.change(plan.sendAmounts.sum())
            val (changeOutputs, counters) = deterministicOutputs(keyset, Amounts.split(changeAmount, keyset.amounts))
            var settings = emptyMap<String, String?>()
            val sendOutputs = if (lock == null) {
                val (outs, more) = deterministicOutputs(keyset, plan.sendAmounts, startAfter = counters)
                counters.putAll(more)
                outs.map { it.copy(forSend = true) }
            } else {
                val refundIndex = (ledger.settings[KEY_P2PK_COUNTER]?.toLongOrNull() ?: 0L)
                settings = mapOf(KEY_P2PK_COUNTER to (refundIndex + 1).toString())
                val refundKey = P2pk.compressedPublicKey(Nut13.p2pkPrivateKey(seed(), refundIndex))
                plan.sendAmounts.map { a ->
                    val secret = P2pk.secret(lock.pubkey, lock.locktimeSeconds, listOf(refundKey))
                    val r = Secp256k1.randomScalar()
                    PendingOutput(a, keyset.id, secret, r, Secp256k1.compressedHex(Bdhke.blind(secret, r)), forSend = true)
                }
            }
            // Ascending amounts so the mint cannot tell send from change by position (NUT-03).
            val outputs = (sendOutputs + changeOutputs).sortedBy { it.amount }
            val op = Operation(
                id = newId(), kind = OperationKind.SEND_SWAP, mintUrl = mintUrl, unit = unit, historyId = historyId,
                inputs = selection.proofs, outputs = outputs, createdAt = now,
            )
            commitLocked(WalletChanges(
                putProofs = selection.proofs.map { stored(it, mintUrl, unit, ProofStatus.RESERVED, op.id) },
                putOperations = listOf(op), putCounters = counters, putHistory = listOf(base), putSettings = settings,
            ))
            op
        }
        return runOperation(prepared) { swapAndFinalize(prepared, HistoryStatus.PENDING) }
    }

    /**
     * Updates pending sends from the mint (NUT-07): claimed tokens complete, unclaimed stay
     * pending. Returns the number of entries that changed.
     */
    suspend fun checkPendingSends(): Int {
        val pending = ledger.history.values.filter { it.kind == HistoryKind.ECASH_SEND && it.isPending && it.token != null }
        var changed = 0
        for (entry in pending) {
            val proofs = ledger.proofs.values.filter { it.ref == entry.id && it.status == ProofStatus.SENT_PENDING }
            if (proofs.isEmpty()) continue
            val states = runCatching { call { api(entry.mintUrl).checkState(proofs.map { it.proof.y() }) } }
                .getOrElse { if (it is CancellationException) throw it; continue }
            if (states.all { it.state == ProofState.SPENT }) {
                mutex.withLock {
                    commitLocked(WalletChanges(
                        removeProofs = proofs.map { it.proof.secret },
                        putHistory = listOf(entry.copy(status = HistoryStatus.COMPLETED, note = HistoryNote.CLAIMED, updatedAt = clock(), token = null)),
                    ))
                }
                changed++
            }
        }
        return changed
    }

    /** Takes back an unclaimed send by swapping its coins into this wallet. */
    suspend fun reclaim(historyId: String): HistoryEntry {
        val entry = ledger.history[historyId] ?: throw WalletException(WalletError.NothingToReclaim)
        if (entry.kind != HistoryKind.ECASH_SEND || !entry.isPending) throw WalletException(WalletError.NothingToReclaim)
        entry.lockedUntil?.let { until ->
            if (clock() / 1000 <= until) throw WalletException(WalletError.TokenLockNotExpired(until))
        }
        val op = mutex.withLock {
            if (ledger.operations.values.any { it.historyId == historyId }) throw WalletException(WalletError.OperationInProgress)
            val mint = requireMint(entry.mintUrl)
            val stored = ledger.proofs.values.filter { it.ref == historyId && it.status == ProofStatus.SENT_PENDING }
            if (stored.isEmpty()) throw WalletException(WalletError.NothingToReclaim)
            val inputs = signForSpend(stored.map { it.proof }, allowRefund = true)
            val keyset = mint.activeKeyset(entry.unit) ?: throw WalletException(WalletError.UnitUnsupported)
            val total = inputs.sumOf { it.amount }
            val fee = Amounts.inputFee(inputs, mint.feePpk)
            if (total - fee <= 0) throw WalletException(WalletError.FeesExceedAmount(fee))
            val (outputs, counters) = deterministicOutputs(keyset, Amounts.split(total - fee, keyset.amounts))
            val op = Operation(
                id = newId(), kind = OperationKind.RECLAIM, mintUrl = mint.url, unit = entry.unit, historyId = historyId,
                inputs = inputs, outputs = outputs, createdAt = clock(),
            )
            commitLocked(WalletChanges(
                putProofs = stored.map { it.copy(status = ProofStatus.RESERVED, ref = op.id) },
                putOperations = listOf(op), putCounters = counters,
                putHistory = listOf(entry.copy(note = HistoryNote.WAITING_FOR_MINT, updatedAt = clock())),
            ))
            op
        }
        return runOperation(op) { swapAndFinalize(op, HistoryStatus.RECLAIMED) }
    }

    // MARK: - Chat card status

    enum class CardStatus {
        CLAIMABLE, LOCKED_TO_US, LOCKED_TO_OTHER, CLAIMED_BY_US, CLAIM_QUEUED, SPENT,
        OUR_PENDING_SEND, OUR_SEND_CLAIMED, OUR_SEND_RECLAIMED, UNREADABLE,
    }

    private val cardCache = object : LinkedHashMap<String, Pair<Ledger, CardStatus>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Ledger, CardStatus>>?) = size > 256
    }

    /**
     * What a token in a conversation means to us, from local state only: no mint contact and
     * no signature math, so it is cheap enough to evaluate while a chat scrolls. Cached until
     * the wallet changes.
     */
    fun cardStatus(raw: String): CardStatus {
        val snapshot = ledger
        synchronized(cardCache) { cardCache[raw]?.takeIf { it.first === snapshot }?.let { return it.second } }
        val status = computeCardStatus(raw, snapshot)
        synchronized(cardCache) { cardCache[raw] = snapshot to status }
        return status
    }

    private fun computeCardStatus(raw: String, snapshot: Ledger): CardStatus {
        val token = TokenCodec.decodeOrNull(raw) ?: return CardStatus.UNREADABLE
        val fp = fingerprint(token)
        snapshot.history.values.filter { it.tokenFingerprint == fp }.maxByOrNull { it.updatedAt }?.let { entry ->
            return when (entry.kind) {
                HistoryKind.ECASH_SEND -> when (entry.status) {
                    HistoryStatus.PENDING -> CardStatus.OUR_PENDING_SEND
                    HistoryStatus.RECLAIMED -> CardStatus.OUR_SEND_RECLAIMED
                    HistoryStatus.COMPLETED -> CardStatus.OUR_SEND_CLAIMED
                    else -> CardStatus.OUR_PENDING_SEND
                }
                else -> when (entry.status) {
                    HistoryStatus.COMPLETED -> CardStatus.CLAIMED_BY_US
                    HistoryStatus.PENDING -> CardStatus.CLAIM_QUEUED
                    else -> CardStatus.SPENT
                }
            }
        }
        if (token.proofs.any { snapshot.proofs[it.secret]?.status == ProofStatus.SENT_PENDING }) return CardStatus.OUR_PENDING_SEND
        return when (lockStatus(token.proofs)) {
            is LockStatus.ToUs -> if (token.proofs.any { P2pk.kind(it.secret) != null }) CardStatus.LOCKED_TO_US else CardStatus.CLAIMABLE
            is LockStatus.ToSomeoneElse, LockStatus.Unsupported -> CardStatus.LOCKED_TO_OTHER
            is LockStatus.RefundableByUs -> CardStatus.OUR_PENDING_SEND
            LockStatus.None -> CardStatus.CLAIMABLE
        }
    }

    // MARK: - Lightning send (NUT-05 / NUT-08)

    data class MeltPreview(
        val mintUrl: String,
        val quote: MeltQuote,
        val inputFee: Long,
    ) {
        /** The most this can cost; unused fee reserve comes back as change. */
        val maxTotal: Long get() = quote.amount + quote.feeReserve + inputFee
    }

    suspend fun previewMelt(mintUrl: String, invoice: String, unit: String = "sat"): MeltPreview {
        val parsed = Bolt11.parse(invoice) ?: throw WalletException(WalletError.InvoiceInvalid)
        if (parsed.amountMsat == null) throw WalletException(WalletError.InvoiceAmountless)
        val mint = requireMint(mintUrl)
        if (mint.info?.meltingDisabled == true) throw WalletException(WalletError.MintFeatureMissing)
        mint.activeKeyset(unit) ?: throw WalletException(WalletError.UnitUnsupported)
        val quote = call { api(mintUrl).meltQuote(parsed.raw, unit) }
        if (quote.unit != unit || quote.amount <= 0 || quote.feeReserve < 0) throw WalletException(WalletError.MintRejected(null))
        if (quote.state == MeltQuoteState.PAID) throw WalletException(WalletError.InvoiceAlreadyPaid)
        val target = quote.amount + quote.feeReserve
        val proofs = available(mintUrl, unit)
        val selection = Amounts.select(proofs, target, mint.feePpk, inactiveKeysets(mint))
            ?: throw WalletException(WalletError.InsufficientBalance(proofs.sumOf { it.amount }, target))
        return MeltPreview(mintUrl, quote, selection.fee)
    }

    suspend fun melt(preview: MeltPreview, historyId: String = newId()): HistoryEntry {
        ledger.history[historyId]?.let { return it }
        val quote = preview.quote
        if (isExpired(quote.expiry, graceSeconds = 5)) throw WalletException(WalletError.QuoteExpired)
        val op = mutex.withLock {
            val mint = requireMint(preview.mintUrl)
            val keyset = mint.activeKeyset(quote.unit) ?: throw WalletException(WalletError.UnitUnsupported)
            val proofs = available(mint.url, quote.unit)
            val selection = Amounts.select(proofs, quote.amount + quote.feeReserve, mint.feePpk, inactiveKeysets(mint))
                ?: throw WalletException(WalletError.InsufficientBalance(proofs.sumOf { it.amount }, preview.maxTotal))
            val overpaid = selection.total - selection.fee - quote.amount
            val (blanks, counters) = deterministicOutputs(keyset, List(Amounts.blankOutputCount(overpaid)) { 0L })
            val now = clock()
            val entry = HistoryEntry(
                id = historyId, kind = HistoryKind.LIGHTNING_SEND, status = HistoryStatus.PENDING,
                amount = quote.amount, fee = quote.feeReserve + selection.fee, unit = quote.unit, mintUrl = mint.url,
                createdAt = now, updatedAt = now, note = HistoryNote.PAYMENT_PENDING, invoice = quote.request,
                quoteId = quote.quote, quoteExpiry = quote.expiry,
            )
            val op = Operation(
                id = newId(), kind = OperationKind.MELT, mintUrl = mint.url, unit = quote.unit, historyId = historyId,
                inputs = selection.proofs, outputs = blanks, quoteId = quote.quote, createdAt = now,
            )
            commitLocked(WalletChanges(
                putProofs = selection.proofs.map { stored(it, mint.url, quote.unit, ProofStatus.RESERVED, op.id) },
                putOperations = listOf(op), putCounters = counters, putHistory = listOf(entry),
            ))
            op
        }
        return runOperation(op) {
            val result = api(op.mintUrl).melt(op.quoteId!!, op.inputs.map { it.forMint() }, op.outputs.map(::blinded))
            settleMelt(op, result)
        }
    }

    // MARK: - Maintenance and recovery

    /**
     * Resolves every journaled operation and pending entry: finishes interrupted mints and
     * swaps, settles melts, retries deferred claims, and refreshes pending sends. Each item
     * is independent; one unreachable mint does not stop the rest.
     */
    suspend fun recover() {
        for (op in ledger.operations.values.toList()) {
            if (op.id in inFlight) continue
            runCatching { resolveOperation(op) }.onFailure { if (it is CancellationException) throw it }
        }
        for (entry in ledger.history.values.filter { it.kind == HistoryKind.LIGHTNING_RECEIVE && it.isPending }) {
            if (ledger.operations.values.any { it.historyId == entry.id }) continue
            runCatching { checkMintQuote(entry.id) }.onFailure { if (it is CancellationException) throw it }
        }
        runCatching { checkPendingSends() }.onFailure { if (it is CancellationException) throw it }
    }

    private suspend fun resolveOperation(op: Operation) {
        if (!inFlight.add(op.id)) return
        try {
            when (op.kind) {
                OperationKind.MELT -> resolveMelt(op)
                else -> resolveSigningOperation(op)
            }
        } catch (e: MintException.Rejected) {
            // A definite answer during recovery is handled exactly like one on first submission.
            runCatching { handleRejection(op, e) }
        } finally {
            inFlight.remove(op.id)
        }
    }

    /** Mint, receive, send-swap and reclaim: did the mint sign our outputs? */
    private suspend fun resolveSigningOperation(op: Operation) {
        val api = api(op.mintUrl)
        val restored = call { api.restore(op.outputs.map(::blinded)) }
        if (restored.isNotEmpty()) {
            val finalStatus = when (op.kind) {
                OperationKind.SEND_SWAP -> HistoryStatus.PENDING
                OperationKind.RECLAIM -> HistoryStatus.RECLAIMED
                else -> HistoryStatus.COMPLETED
            }
            val byB = op.outputs.associateBy { it.b.lowercase() }
            finalizeOutputs(op, restored.mapNotNull { (msg, sig) -> byB[msg.b.lowercase()]?.let { sig to it } }, finalStatus, HistoryNote.NONE)
            return
        }
        when (op.kind) {
            OperationKind.MINT -> {
                val quote = call { api.checkMintQuote(op.quoteId!!) }
                if (quote.mintable > 0) {
                    val signatures = call { api.mint(op.quoteId!!, op.outputs.map(::blinded)) }
                    finalizeOutputs(op, signatures.pairWith(op.outputs), HistoryStatus.COMPLETED, HistoryNote.NONE)
                } else if (quote.state == MintQuoteState.UNPAID && isExpired(quote.expiry)) {
                    abandon(op, HistoryStatus.EXPIRED, HistoryNote.QUOTE_EXPIRED)
                } else if (quote.state != MintQuoteState.UNPAID) {
                    abandon(op, HistoryStatus.FAILED, HistoryNote.ALREADY_SPENT)
                }
            }
            else -> {
                val states = call { api.checkState(op.inputs.map { it.y() }) }
                when {
                    states.any { it.state == ProofState.PENDING } -> Unit // mint is still busy; look again later
                    states.all { it.state == ProofState.UNSPENT } -> {
                        if (op.kind == OperationKind.SEND_SWAP) {
                            // Nothing was spent. Give the coins back rather than retrying a send nobody is waiting on.
                            abandon(op, HistoryStatus.FAILED, HistoryNote.MINT_REJECTED)
                        } else {
                            swapAndFinalize(op, if (op.kind == OperationKind.RECLAIM) HistoryStatus.RECLAIMED else HistoryStatus.COMPLETED)
                        }
                    }
                    else -> {
                        // Inputs are gone but the mint holds no signatures for our outputs: someone else spent them.
                        val note = if (op.kind == OperationKind.RECLAIM) HistoryNote.CLAIMED else HistoryNote.ALREADY_SPENT
                        val status = if (op.kind == OperationKind.RECLAIM) HistoryStatus.COMPLETED else HistoryStatus.FAILED
                        abandon(op, status, note, inputsSpent = true)
                    }
                }
            }
        }
    }

    private suspend fun resolveMelt(op: Operation) {
        val quote = call { api(op.mintUrl).checkMeltQuote(op.quoteId!!) }
        when (quote.state) {
            MeltQuoteState.PAID -> {
                val change = quote.change.takeIf { it.isNotEmpty() }?.pairWith(op.outputs, blank = true)
                    ?: runCatching { call { api(op.mintUrl).restore(op.outputs.map(::blinded)) } }.getOrDefault(emptyList())
                        .let { pairs -> val byB = op.outputs.associateBy { it.b.lowercase() }; pairs.mapNotNull { (m, s) -> byB[m.b.lowercase()]?.let { s to it } } }
                settleMelt(op, quote, change)
            }
            MeltQuoteState.UNPAID -> {
                val states = call { api(op.mintUrl).checkState(op.inputs.map { it.y() }) }
                if (states.none { it.state == ProofState.PENDING }) settleMelt(op, quote)
            }
            MeltQuoteState.PENDING -> Unit
        }
    }

    /** NUT-07 sweep for one mint: drops coins that were spent elsewhere (e.g. after a restore). */
    suspend fun checkProofStates(mintUrl: String): Int {
        val proofs = ledger.proofs.values.filter { it.mintUrl == mintUrl && it.status == ProofStatus.AVAILABLE }
        if (proofs.isEmpty()) return 0
        val states = call { api(mintUrl).checkState(proofs.map { it.proof.y() }) }
        val spent = proofs.zip(states).filter { it.second.state == ProofState.SPENT }.map { it.first.proof.secret }
        if (spent.isNotEmpty()) mutex.withLock {
            val stillAvailable = spent.filter { ledger.proofs[it]?.status == ProofStatus.AVAILABLE }
            commitLocked(WalletChanges(removeProofs = stillAvailable))
        }
        return spent.size
    }

    // MARK: - Restore (NUT-09 / NUT-13)

    data class RestoreProgress(val mintUrl: String, val keysetsDone: Int, val keysetsTotal: Int, val found: Long)

    data class RestoreResult(val mintUrl: String, val recovered: Map<String, Long>, val error: WalletError?)

    /**
     * Rebuilds balances from the seed: for every keyset of each mint, regenerate outputs in
     * batches of [batchSize] until [emptyBatches] consecutive batches come back empty, keep
     * the unspent proofs, and move counters past everything found.
     */
    suspend fun restore(
        mintUrls: List<String>,
        batchSize: Int = 100,
        emptyBatches: Int = 3,
        onProgress: (RestoreProgress) -> Unit = {},
    ): List<RestoreResult> = mintUrls.map { raw ->
        val url = MintUrl.normalizeOrNull(raw) ?: return@map RestoreResult(raw, emptyMap(), WalletError.MintNotFound)
        try {
            restoreMint(url, batchSize, emptyBatches, onProgress)
        } catch (e: WalletException) {
            RestoreResult(url, emptyMap(), e.error)
        }
    }

    private suspend fun restoreMint(url: String, batchSize: Int, emptyLimit: Int, onProgress: (RestoreProgress) -> Unit): RestoreResult {
        val api = api(url)
        val info = call { api.info() }
        if (!info.supports(9)) throw WalletException(WalletError.MintFeatureMissing)
        val keysets = call { api.keysets() }.filter { it.id.startsWith("00") || it.id.startsWith("01") }.map { ks ->
            call { api.keys(ks.id) }.copy(unit = ks.unit, active = ks.active, inputFeePpk = ks.inputFeePpk, finalExpiry = ks.finalExpiry)
        }
        verifyKeysets(url, keysets)
        addMint(MintPreview(url, info, keysets))
        val seed = seed()
        val recovered = HashMap<String, Long>()
        var found = 0L
        keysets.forEachIndexed { index, keyset ->
            var counter = 0L
            var empty = 0
            var next = ledger.counters[keyset.id] ?: 0L
            val proofs = ArrayList<Proof>()
            while (empty < emptyLimit) {
                val outputs = (counter until counter + batchSize).map { c ->
                    val d = Nut13.derive(seed, keyset.id, c)
                    PendingOutput(1, keyset.id, d.secret, d.r, Secp256k1.compressedHex(Bdhke.blind(d.secret, d.r))) to c
                }
                val restored = call { api.restore(outputs.map { blinded(it.first) }) }
                if (restored.isEmpty()) empty++ else {
                    empty = 0
                    val byB = outputs.associateBy { it.first.b.lowercase() }
                    restored.forEach { (msg, sig) ->
                        val (output, c) = byB[msg.b.lowercase()] ?: return@forEach
                        unblind(output, sig, keyset)?.let(proofs::add)
                        next = maxOf(next, c + 1)
                    }
                }
                counter += batchSize
            }
            val unspent = if (proofs.isEmpty()) emptyList() else {
                val states = proofs.chunked(500).flatMap { chunk -> call { api.checkState(chunk.map { it.y() }) } }
                proofs.zip(states).filter { it.second.state != ProofState.SPENT }.map { it.first }
            }
            mutex.withLock {
                val fresh = unspent.filter { it.secret !in ledger.proofs }
                commitLocked(WalletChanges(
                    putProofs = fresh.map { stored(it, url, keyset.unit, ProofStatus.AVAILABLE, null) },
                    putCounters = mapOf(keyset.id to maxOf(next, ledger.counters[keyset.id] ?: 0L)),
                ))
                val amount = fresh.sumOf { it.amount }
                if (amount > 0) recovered[keyset.unit] = (recovered[keyset.unit] ?: 0) + amount
                found += amount
            }
            onProgress(RestoreProgress(url, index + 1, keysets.size, found))
        }
        if (recovered.isNotEmpty()) mutex.withLock {
            val now = clock()
            commitLocked(WalletChanges(putHistory = recovered.map { (unit, amount) ->
                HistoryEntry(
                    id = newId(), kind = HistoryKind.ECASH_RECEIVE, status = HistoryStatus.COMPLETED, amount = amount,
                    fee = 0, unit = unit, mintUrl = url, createdAt = now, updatedAt = now, note = HistoryNote.RESTORED,
                )
            }))
        }
        return RestoreResult(url, recovered, null)
    }

    // MARK: - Internals: swap / finalize

    private suspend fun swapAndFinalize(op: Operation, status: HistoryStatus): HistoryEntry {
        val signatures = api(op.mintUrl).swap(op.inputs.map { it.forMint() }, op.outputs.map(::blinded))
        return finalizeOutputs(op, signatures.pairWith(op.outputs), status, HistoryNote.NONE)
    }

    /** Unblinds what the mint signed, stores it, closes the operation and its history entry. */
    private suspend fun finalizeOutputs(
        op: Operation,
        signed: List<Pair<BlindSignature, PendingOutput>>,
        status: HistoryStatus,
        note: HistoryNote,
    ): HistoryEntry = mutex.withLock {
        val mint = requireMint(op.mintUrl).let { m ->
            if (signed.all { m.keyset(it.first.keysetId) != null }) m else refreshKeysetsLocked(m)
        }
        val proofs = signed.map { (signature, output) ->
            val keyset = mint.keyset(signature.keysetId) ?: throw WalletException(WalletError.MintKeysInvalid)
            unblind(output, signature, keyset) ?: throw WalletException(WalletError.TokenSignatureInvalid)
        }
        val forSend = proofs.filterIndexed { i, _ -> signed[i].second.forSend }
        val keep = proofs - forSend.toSet()
        val entry = ledger.history[op.historyId]
        val now = clock()
        var history = entry?.copy(
            status = status,
            note = if (status == HistoryStatus.PENDING && op.kind == OperationKind.SEND_SWAP) HistoryNote.WAITING_FOR_CLAIM else note,
            updatedAt = now,
        )
        if (op.kind == OperationKind.SEND_SWAP && history != null) {
            val token = Token(op.mintUrl, op.unit, forSend, history.memo)
            history = history.copy(token = encode(token), tokenFingerprint = fingerprint(token))
        }
        if (op.kind == OperationKind.RECLAIM && history != null) history = history.copy(token = null)
        commitLocked(WalletChanges(
            removeProofs = op.inputs.map { it.secret },
            putProofs = keep.map { stored(it, op.mintUrl, op.unit, ProofStatus.AVAILABLE, null) } +
                forSend.map { stored(it, op.mintUrl, op.unit, ProofStatus.SENT_PENDING, op.historyId) },
            removeOperations = listOf(op.id),
            putHistory = listOfNotNull(history),
        ))
        history ?: throw WalletException(WalletError.Internal)
    }

    private suspend fun settleMelt(
        op: Operation,
        result: MeltQuote,
        changeOverride: List<Pair<BlindSignature, PendingOutput>>? = null,
    ): HistoryEntry {
        when (result.state) {
            MeltQuoteState.PENDING -> {
                mutex.withLock {
                    ledger.history[op.historyId]?.let { commitLocked(WalletChanges(putHistory = listOf(it.copy(note = HistoryNote.PAYMENT_PENDING, updatedAt = clock())))) }
                }
                throw WalletException(WalletError.PaymentPending)
            }
            MeltQuoteState.UNPAID -> {
                abandon(op, HistoryStatus.FAILED, HistoryNote.PAYMENT_FAILED)
                throw WalletException(WalletError.PaymentFailed)
            }
            MeltQuoteState.PAID -> Unit
        }
        val change = changeOverride ?: result.change.pairWith(op.outputs, blank = true)
        return mutex.withLock {
            val mint = requireMint(op.mintUrl)
            val changeProofs = change.mapNotNull { (sig, out) -> mint.keyset(sig.keysetId)?.let { unblind(out, sig, it) } }
            val paid = op.inputs.sumOf { it.amount } - changeProofs.sumOf { it.amount }
            val entry = ledger.history[op.historyId]?.copy(
                status = HistoryStatus.COMPLETED, note = HistoryNote.NONE, updatedAt = clock(),
                fee = (paid - (ledger.history[op.historyId]?.amount ?: 0)).coerceAtLeast(0), preimage = result.preimage,
            )
            commitLocked(WalletChanges(
                removeProofs = op.inputs.map { it.secret },
                putProofs = changeProofs.map { stored(it, op.mintUrl, op.unit, ProofStatus.AVAILABLE, null) },
                removeOperations = listOf(op.id),
                putHistory = listOfNotNull(entry),
            ))
            entry ?: throw WalletException(WalletError.Internal)
        }
    }

    /** Closes an operation that did not happen (or whose inputs are gone) and records why. */
    private suspend fun abandon(op: Operation, status: HistoryStatus, note: HistoryNote, inputsSpent: Boolean = false) = mutex.withLock {
        val entry = ledger.history[op.historyId]
        val changes = (if (inputsSpent) WalletChanges(removeProofs = op.inputs.map { it.secret }) else releaseInputs(op)) +
            WalletChanges(
                removeOperations = listOf(op.id),
                putHistory = listOfNotNull(entry?.let {
                    // A reclaim that found the coins claimed means the original send completed.
                    if (op.kind == OperationKind.RECLAIM) it.copy(status = status, note = note, updatedAt = clock(), token = if (inputsSpent) null else it.token)
                    else it.copy(status = status, note = note, updatedAt = clock())
                }),
            )
        commitLocked(changes)
    }

    /** Returns reserved inputs to where they came from. */
    private fun releaseInputs(op: Operation): WalletChanges {
        val restoreTo = if (op.kind == OperationKind.RECLAIM) ProofStatus.SENT_PENDING else ProofStatus.AVAILABLE
        val ref = if (op.kind == OperationKind.RECLAIM) op.historyId else null
        val mine = op.inputs.mapNotNull { ledger.proofs[it.secret] }
        // Received tokens' inputs were never ours, so there is nothing to release for RECEIVE.
        return WalletChanges(putProofs = mine.map { it.copy(status = restoreTo, ref = ref) })
    }

    /**
     * Runs the network part of an operation. Definite rejections close it with a clear
     * error; ambiguous failures leave it journaled for [recover] and surface as
     * [WalletError.OutcomeUnknown] (or [WalletError.MintUnreachable] for receives, which
     * retry on their own).
     */
    private suspend fun runOperation(op: Operation, block: suspend () -> HistoryEntry): HistoryEntry {
        inFlight.add(op.id)
        try {
            return block()
        } catch (e: MintException.Rejected) {
            return handleRejection(op, e)
        } catch (e: MintException) {
            val note = if (op.kind == OperationKind.MELT) HistoryNote.PAYMENT_PENDING else HistoryNote.WAITING_FOR_MINT
            mutex.withLock {
                ledger.history[op.historyId]?.let {
                    commitLocked(WalletChanges(putHistory = listOf(it.copy(note = note, updatedAt = clock()))))
                }
            }
            throw WalletException(
                when {
                    op.kind == OperationKind.MELT -> WalletError.PaymentPending
                    e is MintException.Unreachable && op.kind == OperationKind.RECEIVE -> WalletError.MintUnreachable
                    else -> WalletError.OutcomeUnknown
                },
                e,
            )
        } finally {
            inFlight.remove(op.id)
        }
    }

    /** Handles a definite answer from the mint. Returns only when it turned out to be a success. */
    private suspend fun handleRejection(op: Operation, e: MintException.Rejected): HistoryEntry {
        return when (e.code) {
            CashuErrorCode.OUTPUTS_PENDING, CashuErrorCode.PROOFS_PENDING, CashuErrorCode.QUOTE_PENDING ->
                // The mint is mid-flight on this; recovery settles it once it finishes.
                throw WalletException(if (op.kind == OperationKind.MELT) WalletError.PaymentPending else WalletError.OutcomeUnknown, e)
            CashuErrorCode.OUTPUTS_ALREADY_SIGNED -> handleOutputsAlreadySigned(op, e)
            CashuErrorCode.PROOFS_SPENT -> {
                if (op.kind == OperationKind.RECEIVE) {
                    abandon(op, HistoryStatus.FAILED, HistoryNote.ALREADY_SPENT)
                    throw WalletException(WalletError.TokenAlreadySpent, e)
                }
                if (op.kind == OperationKind.RECLAIM) {
                    abandon(op, HistoryStatus.COMPLETED, HistoryNote.CLAIMED, inputsSpent = true)
                    throw WalletException(WalletError.TokenAlreadySpent, e)
                }
                // Our own coins were spent elsewhere (same seed on another device): drop them.
                val states = runCatching { call { api(op.mintUrl).checkState(op.inputs.map { it.y() }) } }.getOrNull()
                mutex.withLock {
                    val spent = states?.zip(op.inputs)?.filter { it.first.state == ProofState.SPENT }?.map { it.second.secret }
                        ?: op.inputs.map { it.secret }
                    val released = releaseInputs(op).putProofs.filter { it.proof.secret !in spent }
                    commitLocked(WalletChanges(
                        removeProofs = spent, putProofs = released, removeOperations = listOf(op.id),
                        putHistory = listOfNotNull(ledger.history[op.historyId]?.copy(status = HistoryStatus.FAILED, note = HistoryNote.ALREADY_SPENT, updatedAt = clock())),
                    ))
                }
                throw WalletException(WalletError.TokenAlreadySpent, e)
            }
            else -> {
                val (status, note) = when (e.code) {
                    CashuErrorCode.QUOTE_EXPIRED -> HistoryStatus.EXPIRED to HistoryNote.QUOTE_EXPIRED
                    CashuErrorCode.LIGHTNING_PAYMENT_FAILED -> HistoryStatus.FAILED to HistoryNote.PAYMENT_FAILED
                    else -> HistoryStatus.FAILED to HistoryNote.MINT_REJECTED
                }
                if (op.kind == OperationKind.MINT && e.code == CashuErrorCode.QUOTE_NOT_PAID) {
                    // Not paid yet: forget the attempt, keep waiting for payment.
                    mutex.withLock {
                        commitLocked(WalletChanges(removeOperations = listOf(op.id),
                            putHistory = listOfNotNull(ledger.history[op.historyId]?.copy(note = HistoryNote.WAITING_FOR_PAYMENT))))
                    }
                    throw WalletException(WalletError.PaymentPending, e)
                }
                abandon(op, if (op.kind == OperationKind.RECLAIM) HistoryStatus.PENDING else status, if (op.kind == OperationKind.RECLAIM) HistoryNote.WAITING_FOR_CLAIM else note)
                throw WalletException(
                    when (e.code) {
                        CashuErrorCode.QUOTE_EXPIRED -> WalletError.QuoteExpired
                        CashuErrorCode.LIGHTNING_PAYMENT_FAILED -> WalletError.PaymentFailed
                        CashuErrorCode.INVOICE_ALREADY_PAID -> WalletError.InvoiceAlreadyPaid
                        CashuErrorCode.TRANSACTION_UNBALANCED -> WalletError.MintRejected(e.code)
                        CashuErrorCode.UNIT_MISMATCH, CashuErrorCode.UNIT_UNSUPPORTED -> WalletError.UnitUnsupported
                        CashuErrorCode.PROOF_VERIFICATION_FAILED ->
                            if (op.kind == OperationKind.RECEIVE) WalletError.TokenSignatureInvalid else WalletError.MintRejected(e.code)
                        else -> WalletError.MintRejected(e.code)
                    },
                    e,
                )
            }
        }
    }

    /**
     * 11003 on a first submission. Either our own request already went through (response
     * lost, inputs now spent, so the signatures are ours) or our counters collided with
     * outputs signed long ago, e.g. after a restore that stopped short of a gap. In that case
     * nothing happened: release everything and move the counters well past the collision.
     */
    private suspend fun handleOutputsAlreadySigned(op: Operation, e: MintException.Rejected): HistoryEntry {
        val api = api(op.mintUrl)
        val ours = if (op.kind == OperationKind.MINT) {
            runCatching { call { api.checkMintQuote(op.quoteId!!) } }.getOrNull()?.let { it.mintable == 0L && it.state != MintQuoteState.UNPAID }
        } else {
            runCatching { call { api.checkState(op.inputs.map { it.y() }) } }.getOrNull()?.all { it.state == ProofState.SPENT }
        }
        if (ours == true) {
            runCatching { resolveSigningOperation(op) }.onFailure { if (it is CancellationException) throw it }
            val entry = ledger.history[op.historyId]
            if (entry != null && ledger.operations[op.id] == null && entry.status != HistoryStatus.FAILED) return entry
            throw WalletException(WalletError.OutcomeUnknown, e)
        }
        if (ours == null) throw WalletException(WalletError.OutcomeUnknown, e)
        mutex.withLock {
            val bumped = op.outputs.map { it.keysetId }.distinct().associateWith { (ledger.counters[it] ?: 0L) + COUNTER_RESYNC_STEP }
            val entry = ledger.history[op.historyId]
            commitLocked(releaseInputs(op) + WalletChanges(
                putCounters = bumped,
                removeOperations = listOf(op.id),
                // A receive or mint the person can simply retry leaves no trace; a send records the failure.
                removeHistory = listOfNotNull(entry?.id?.takeIf { op.kind == OperationKind.RECEIVE }),
                putHistory = listOfNotNull(entry?.takeIf { op.kind != OperationKind.RECEIVE }?.let {
                    if (op.kind == OperationKind.MINT) it.copy(note = HistoryNote.WAITING_FOR_PAYMENT)
                    else if (op.kind == OperationKind.RECLAIM) it.copy(note = HistoryNote.WAITING_FOR_CLAIM)
                    else it.copy(status = HistoryStatus.FAILED, note = HistoryNote.MINT_REJECTED, updatedAt = clock())
                }),
            ))
        }
        throw WalletException(WalletError.CountersResynced, e)
    }

    // MARK: - Internals: outputs and signatures

    private fun deterministicOutputs(
        keyset: Keyset,
        amounts: List<Long>,
        startAfter: Map<String, Long> = emptyMap(),
    ): Pair<List<PendingOutput>, MutableMap<String, Long>> {
        val seed = seed()
        val start = startAfter[keyset.id] ?: ledger.counters[keyset.id] ?: 0L
        val outputs = amounts.mapIndexed { i, amount ->
            val derived = Nut13.derive(seed, keyset.id, start + i)
            PendingOutput(amount, keyset.id, derived.secret, derived.r, Secp256k1.compressedHex(Bdhke.blind(derived.secret, derived.r)))
        }
        return outputs to mutableMapOf(keyset.id to start + amounts.size)
    }

    private fun blinded(output: PendingOutput) = BlindedMessage(output.amount, output.keysetId, output.b)

    /** Signatures come back in output order (NUT-03/04); blank change may omit zero outputs. */
    private fun List<BlindSignature>.pairWith(outputs: List<PendingOutput>, blank: Boolean = false): List<Pair<BlindSignature, PendingOutput>> {
        if (!blank && size != outputs.size) throw MintException.Malformed("signature count mismatch")
        if (blank && size > outputs.size) throw MintException.Malformed("too much change")
        return mapIndexed { i, sig ->
            val output = outputs[i]
            if (!blank && (sig.amount != output.amount || sig.keysetId != output.keysetId)) {
                throw MintException.Malformed("signature does not match output")
            }
            sig to output
        }
    }

    /** Unblinds and verifies DLEQ when present. Null means the signature is not acceptable. */
    private fun unblind(output: PendingOutput, signature: BlindSignature, keyset: Keyset): Proof? {
        val key = keyset.keyFor(signature.amount) ?: return null
        val blindSignature = runCatching { Secp256k1.decodePoint(signature.c) }.getOrNull() ?: return null
        signature.dleq?.let { dleq ->
            if (!Bdhke.verifyDleq(dleq.e, dleq.s, key, Secp256k1.decodePoint(output.b), blindSignature)) return null
        }
        val c = Bdhke.unblind(blindSignature, output.r, key)
        return Proof(
            amount = signature.amount,
            keysetId = signature.keysetId,
            secret = output.secret,
            c = Secp256k1.compressedHex(c),
            dleq = signature.dleq?.let { DleqProof(it.e, it.s, Hex.encode(Secp256k1.scalarBytes(output.r))) },
        )
    }

    /** Maps short keyset IDs to full ones and checks every amount exists in its keyset. */
    private fun resolveKeysets(proofs: List<Proof>, mint: MintRecord): List<Proof> = proofs.map { p ->
        val keyset = mint.keyset(p.keysetId)
            ?: mint.keysets.filter { it.id.startsWith(p.keysetId) && p.keysetId.length == 16 }.let { matches ->
                if (matches.size > 1) throw WalletException(WalletError.TokenMalformed)
                matches.firstOrNull()
            }
            ?: throw WalletException(WalletError.MintKeysInvalid)
        if (keyset.keyFor(p.amount) == null) throw WalletException(WalletError.TokenMalformed)
        p.copy(keysetId = keyset.id)
    }

    private fun lockStatus(proofs: List<Proof>): LockStatus {
        val conditions = proofs.map { p ->
            when (P2pk.kind(p.secret)) {
                null -> null
                "P2PK" -> runCatching { P2pk.parse(p.secret) }.getOrNull() ?: return LockStatus.Unsupported
                else -> return LockStatus.Unsupported
            }
        }
        if (conditions.all { it == null }) return LockStatus.None
        val now = clock() / 1000
        val c = conditions.filterNotNull().first()
        if (conditions.filterNotNull().any { it.sigAll || it.nSigs > 1 }) return LockStatus.Unsupported
        val ours = signingKeyPairs()
        val canLock = c.lockKeys.any { k -> ours.any { P2pk.sameKey(it.first, k) } }
        val expired = c.locktime != null && now > c.locktime
        val canRefund = expired && c.refundKeys.any { k -> ours.any { P2pk.sameKey(it.first, k) } }
        val refundIsOurs = c.refundKeys.any { k -> ours.any { P2pk.sameKey(it.first, k) } }
        return when {
            canLock -> LockStatus.ToUs(c.locktime)
            canRefund || (expired && c.refundKeys.isEmpty()) -> LockStatus.ToUs(c.locktime)
            refundIsOurs && c.locktime != null -> LockStatus.RefundableByUs(c.locktime)
            else -> LockStatus.ToSomeoneElse(c.locktime)
        }
    }

    /** Adds NUT-11 witnesses to any locked proof we can unlock; fails if one cannot be. */
    private fun signForSpend(proofs: List<Proof>, allowRefund: Boolean): List<Proof> {
        val ours = signingKeyPairs()
        val now = clock() / 1000
        return proofs.map { p ->
            if (P2pk.kind(p.secret) == null) return@map p
            if (P2pk.kind(p.secret) != "P2PK") throw WalletException(WalletError.TokenLockUnsupported)
            val c = runCatching { P2pk.parse(p.secret) }.getOrNull() ?: throw WalletException(WalletError.TokenLockUnsupported)
            if (c.sigAll || c.nSigs > 1) throw WalletException(WalletError.TokenLockUnsupported)
            val expired = c.locktime != null && now > c.locktime
            val lockKey = ours.firstOrNull { (pub, _) -> c.lockKeys.any { P2pk.sameKey(it, pub) } }
            val refundKey = if (expired) ours.firstOrNull { (pub, _) -> c.refundKeys.any { P2pk.sameKey(it, pub) } } else null
            when {
                lockKey != null -> p.copy(witness = P2pk.witness(p.secret, listOf(lockKey.second)))
                refundKey != null -> p.copy(witness = P2pk.witness(p.secret, listOf(refundKey.second)))
                expired && c.refundKeys.isEmpty() -> p
                allowRefund && c.locktime != null && !expired &&
                    ours.any { (pub, _) -> c.refundKeys.any { P2pk.sameKey(it, pub) } } ->
                    throw WalletException(WalletError.TokenLockNotExpired(c.locktime))
                else -> throw WalletException(WalletError.TokenLockedToSomeoneElse)
            }
        }
    }

    /** (compressed pubkey, private key) pairs we can sign with: extra keys plus our refund keys. */
    private fun signingKeyPairs(): List<Pair<String, BigInteger>> {
        val refundCount = ledger.settings[KEY_P2PK_COUNTER]?.toLongOrNull() ?: 0L
        val refund = refundKeyCache.takeIf { it.size.toLong() == refundCount } ?: run {
            val seed = seedProvider()
            (if (seed == null) emptyList() else (0 until refundCount).map { Nut13.p2pkPrivateKey(seed, it) })
                .map { P2pk.compressedPublicKey(it) to it }
                .also { refundKeyCache = it }
        }
        return extraSigningKeys().map { P2pk.compressedPublicKey(it) to it } + refund
    }

    @Volatile
    private var refundKeyCache: List<Pair<String, BigInteger>> = emptyList()

    // MARK: - Internals: misc

    private fun verifyKeysets(url: String, keysets: List<Keyset>) {
        for (keyset in keysets) {
            if (!KeysetIds.matches(keyset)) throw WalletException(WalletError.MintKeysInvalid)
            runCatching { keyset.amounts.forEach { keyset.keyFor(it) } }.getOrElse { throw WalletException(WalletError.MintKeysInvalid) }
            // NUT-02 caution: a keyset ID already used by a different mint is an attack or a bug.
            if (ledger.mints.values.any { m -> m.url != url && m.keyset(keyset.id) != null }) {
                throw WalletException(WalletError.MintKeysInvalid)
            }
        }
    }

    private fun mergeKeysets(old: List<Keyset>, new: List<Keyset>): List<Keyset> {
        val byId = old.associateBy { it.id }.toMutableMap()
        new.forEach { byId[it.id] = it }
        // Keysets the mint no longer lists are inactive, not gone: we may still hold their coins.
        val listed = new.map { it.id }.toSet()
        return byId.values.map { if (it.id !in listed) it.copy(active = false) else it }
    }

    private suspend fun refreshKeysetsLocked(mint: MintRecord): MintRecord {
        val api = api(mint.url)
        val infos = call { api.keysets() }
        val keysets = infos.map { ks ->
            mint.keyset(ks.id)?.copy(active = ks.active) ?: call { api.keys(ks.id) }.copy(
                unit = ks.unit, active = ks.active, inputFeePpk = ks.inputFeePpk, finalExpiry = ks.finalExpiry,
            )
        }
        verifyKeysets(mint.url, keysets)
        val updated = mint.copy(keysets = mergeKeysets(mint.keysets, keysets), updatedAt = clock())
        commitLocked(WalletChanges(putMints = listOf(updated)))
        return updated
    }

    private fun inactiveKeysets(mint: MintRecord): Set<String> = mint.keysets.filter { !it.active }.map { it.id }.toSet()

    private fun available(mintUrl: String, unit: String): List<Proof> =
        ledger.proofs.values.filter { it.mintUrl == mintUrl && it.unit == unit && it.status == ProofStatus.AVAILABLE }.map { it.proof }

    private fun stored(proof: Proof, mintUrl: String, unit: String, status: ProofStatus, ref: String?) =
        StoredProof(proof, mintUrl, unit, status, ref)

    private fun requireMint(url: String): MintRecord = ledger.mints[url] ?: throw WalletException(WalletError.MintNotFound)

    private fun seed(): ByteArray = seedProvider() ?: throw WalletException(WalletError.WalletNotReady)

    private fun api(url: String): MintApi = apis.getOrPut(url) { apiFactory.create(url) }

    private fun decode(raw: String): Token = try {
        TokenCodec.decode(raw)
    } catch (_: TokenDecodeException.MultipleMints) {
        throw WalletException(WalletError.TokenMultipleMints)
    } catch (_: TokenDecodeException) {
        throw WalletException(WalletError.TokenMalformed)
    }

    private fun encode(token: Token): String = TokenCodec.encodeV4(token)

    private fun fingerprint(token: Token): String =
        Hex.encode(Secp256k1.sha256(token.proofs.map { it.secret }.sorted().joinToString("\n").toByteArray())).take(32)

    private fun isExpired(expiry: Long?, graceSeconds: Long = 0): Boolean =
        expiry != null && expiry > 0 && clock() / 1000 + graceSeconds >= expiry

    private suspend fun finishHistory(id: String, status: HistoryStatus, note: HistoryNote): HistoryEntry = mutex.withLock {
        val entry = ledger.history.getValue(id).copy(status = status, note = note, updatedAt = clock())
        commitLocked(WalletChanges(putHistory = listOf(entry)))
        entry
    }

    /** Maps transport failures for read-only calls. */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: MintException) {
        throw WalletException(
            when (e) {
                is MintException.Rejected -> when (e.code) {
                    CashuErrorCode.UNIT_UNSUPPORTED, CashuErrorCode.UNIT_MISMATCH -> WalletError.UnitUnsupported
                    CashuErrorCode.AMOUNT_OUT_OF_RANGE -> WalletError.AmountOutOfRange(null, null)
                    CashuErrorCode.MINTING_DISABLED -> WalletError.MintingDisabled
                    CashuErrorCode.INVOICE_ALREADY_PAID -> WalletError.InvoiceAlreadyPaid
                    CashuErrorCode.AMOUNTLESS_INVOICE -> WalletError.InvoiceAmountless
                    CashuErrorCode.QUOTE_EXPIRED -> WalletError.QuoteExpired
                    null -> if (e.status == 404) WalletError.MintNotFound else WalletError.MintRejected(null)
                    else -> WalletError.MintRejected(e.code)
                }
                is MintException.Malformed -> WalletError.MintRejected(null)
                else -> WalletError.MintUnreachable
            },
            e,
        )
    }

    private suspend fun commitLocked(changes: WalletChanges) {
        if (changes.isEmpty) return
        store.commit(changes)
        ledger = ledger.apply(changes)
        publish()
    }

    private fun publish() {
        val proofsByMint = ledger.proofs.values.groupBy { it.mintUrl }
        fun sum(list: List<StoredProof>, status: ProofStatus) =
            list.filter { it.status == status }.groupBy { it.unit }.mapValues { (_, v) -> v.sumOf { it.proof.amount } }
        val mints = ledger.mints.values.sortedBy { it.addedAt }.map { mint ->
            val proofs = proofsByMint[mint.url].orEmpty()
            MintBalance(mint, sum(proofs, ProofStatus.AVAILABLE), sum(proofs, ProofStatus.SENT_PENDING), sum(proofs, ProofStatus.RESERVED))
        }
        _state.value = WalletState(
            loaded = true,
            hasSeed = seedProvider() != null,
            mints = mints,
            history = ledger.history.values.sortedWith(compareByDescending<HistoryEntry> { it.createdAt }.thenByDescending { it.updatedAt }.thenBy { it.id }),
            settings = ledger.settings,
        )
    }

    suspend fun putSetting(key: String, value: String?) = mutex.withLock {
        commitLocked(WalletChanges(putSettings = mapOf(key to value)))
    }

    fun setting(key: String): String? = ledger.settings[key]

    companion object {
        const val KEY_P2PK_COUNTER = "p2pk_refund_counter"
        /** How far counters jump after colliding with outputs signed in a previous life of this seed. */
        private const val COUNTER_RESYNC_STEP = 1_000L
        private const val MAX_MEMO = 80

        fun newId(): String = UUID.randomUUID().toString()
    }
}
