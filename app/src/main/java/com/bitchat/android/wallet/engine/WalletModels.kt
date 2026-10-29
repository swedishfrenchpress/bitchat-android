package com.bitchat.android.wallet.engine

import com.bitchat.android.wallet.cashu.Keyset
import com.bitchat.android.wallet.cashu.MintInfo
import com.bitchat.android.wallet.cashu.MintUrl
import com.bitchat.android.wallet.cashu.Proof
import java.math.BigInteger

/** A mint the person chose to use, with the keysets we verified against their IDs. */
data class MintRecord(
    val url: String,
    val info: MintInfo?,
    val keysets: List<Keyset>,
    val addedAt: Long,
    val updatedAt: Long,
) {
    val host: String get() = MintUrl.host(url)
    val displayName: String get() = info?.name?.takeIf { it.isNotBlank() }?.take(40) ?: host

    fun keyset(id: String): Keyset? = keysets.firstOrNull { it.id == id }

    fun activeKeyset(unit: String): Keyset? =
        keysets.filter { it.active && it.unit == unit }.minByOrNull { it.inputFeePpk }

    val units: Set<String> get() = keysets.map { it.unit }.toSet()

    val feePpk: Map<String, Long> get() = keysets.associate { it.id to it.inputFeePpk }

    override fun toString(): String = "MintRecord($host, keysets=${keysets.size})"
}

enum class ProofStatus {
    /** Spendable by this wallet. */
    AVAILABLE,

    /** Committed as an input to an in-flight swap or melt. Not spendable until resolved. */
    RESERVED,

    /** Inside a token we handed out that has not been claimed yet. Reclaimable. */
    SENT_PENDING,
}

data class StoredProof(
    val proof: Proof,
    val mintUrl: String,
    val unit: String,
    val status: ProofStatus,
    /** Operation that reserved it, or history entry that sent it. */
    val ref: String? = null,
) {
    override fun toString(): String = "StoredProof(${proof.amount} $unit, $status)"
}

/** An output we blinded and asked a mint to sign. Kept until the outcome is known. */
data class PendingOutput(
    val amount: Long,
    val keysetId: String,
    val secret: String,
    val r: BigInteger,
    val b: String,
    /** Output destined for the outgoing token rather than our change. */
    val forSend: Boolean = false,
) {
    override fun toString(): String = "PendingOutput($amount, send=$forSend)"
}

enum class OperationKind { MINT, RECEIVE, SEND_SWAP, MELT, RECLAIM }

/**
 * Write-ahead journal entry. Written in the same transaction that reserves inputs and
 * advances counters, before any request leaves the device. Recovery replays from here.
 */
data class Operation(
    val id: String,
    val kind: OperationKind,
    val mintUrl: String,
    val unit: String,
    val historyId: String,
    val inputs: List<Proof>,
    val outputs: List<PendingOutput>,
    val quoteId: String? = null,
    val createdAt: Long,
) {
    override fun toString(): String = "Operation($kind, inputs=${inputs.size}, outputs=${outputs.size})"
}

enum class HistoryKind { LIGHTNING_RECEIVE, ECASH_RECEIVE, ECASH_SEND, LIGHTNING_SEND }

enum class HistoryStatus { PENDING, COMPLETED, FAILED, EXPIRED, RECLAIMED }

/** Why an entry failed or is stuck, mapped to copy by the UI. */
enum class HistoryNote {
    NONE,
    WAITING_FOR_PAYMENT,
    WAITING_FOR_MINT,
    WAITING_FOR_CLAIM,
    CLAIMED,
    ALREADY_SPENT,
    PAYMENT_FAILED,
    PAYMENT_PENDING,
    QUOTE_EXPIRED,
    MINT_REJECTED,
    LOCK_ACTIVE,
    RESTORED,
}

/**
 * One line of wallet history. Deliberately no counterparty identity (see `decisions.md`
 * D-015): only whether it went through chat.
 */
data class HistoryEntry(
    val id: String,
    val kind: HistoryKind,
    val status: HistoryStatus,
    val amount: Long,
    val fee: Long,
    val unit: String,
    val mintUrl: String,
    val createdAt: Long,
    val updatedAt: Long,
    val note: HistoryNote = HistoryNote.NONE,
    val memo: String? = null,
    /** Outgoing token, kept while it can still be reshared or reclaimed. */
    val token: String? = null,
    val invoice: String? = null,
    val quoteId: String? = null,
    val quoteExpiry: Long? = null,
    val viaChat: Boolean = false,
    /** Unix seconds after which a locked send can be reclaimed via our refund key. */
    val lockedUntil: Long? = null,
    val lockedToPeer: Boolean = false,
    val preimage: String? = null,
    /** Fingerprint of the token's secrets, to recognise a token we already claimed. */
    val tokenFingerprint: String? = null,
) {
    val isPending: Boolean get() = status == HistoryStatus.PENDING

    override fun toString(): String = "HistoryEntry($kind, $status, $amount $unit)"
}

data class MintBalance(
    val mint: MintRecord,
    val available: Map<String, Long>,
    val pendingOut: Map<String, Long>,
    val reserved: Map<String, Long>,
)

data class WalletState(
    val loaded: Boolean = false,
    val hasSeed: Boolean = false,
    val mints: List<MintBalance> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
) {
    fun available(unit: String): Long = mints.sumOf { it.available[unit] ?: 0 }

    fun pendingOut(unit: String): Long = mints.sumOf { it.pendingOut[unit] ?: 0 }

    val units: List<String>
        get() = mints.flatMap { it.available.keys + it.mint.units }.distinct()
            .sortedWith(compareBy<String> { it != "sat" }.thenBy { it })

    fun mint(url: String): MintBalance? = mints.firstOrNull { it.mint.url == url }

    val totalAvailableAllUnits: Map<String, Long>
        get() = units.associateWith(::available)
}

/** Everything persisted, as loaded at startup. */
data class WalletSnapshot(
    val mints: List<MintRecord> = emptyList(),
    val proofs: List<StoredProof> = emptyList(),
    val counters: Map<String, Long> = emptyMap(),
    val operations: List<Operation> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)
