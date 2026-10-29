package com.bitchat.android.wallet.engine

/**
 * Durable wallet storage. [commit] is atomic: either every change in [WalletChanges] is
 * persisted or none is. The engine only acts on the network after a commit returns.
 */
interface WalletStore {
    suspend fun load(): WalletSnapshot
    suspend fun commit(changes: WalletChanges)
    /** Erases everything, including key material protecting it. Returns true only if verified. */
    suspend fun wipe(): Boolean
}

/** One atomic batch of changes. Proofs are keyed by secret, which is unique per proof. */
data class WalletChanges(
    val putMints: List<MintRecord> = emptyList(),
    val removeMints: List<String> = emptyList(),
    val putProofs: List<StoredProof> = emptyList(),
    val removeProofs: List<String> = emptyList(),
    val putCounters: Map<String, Long> = emptyMap(),
    val putOperations: List<Operation> = emptyList(),
    val removeOperations: List<String> = emptyList(),
    val putHistory: List<HistoryEntry> = emptyList(),
    val removeHistory: List<String> = emptyList(),
    /** A null value removes the setting. */
    val putSettings: Map<String, String?> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = putMints.isEmpty() && removeMints.isEmpty() && putProofs.isEmpty() &&
            removeProofs.isEmpty() && putCounters.isEmpty() && putOperations.isEmpty() &&
            removeOperations.isEmpty() && putHistory.isEmpty() && removeHistory.isEmpty() &&
            putSettings.isEmpty()

    operator fun plus(other: WalletChanges) = WalletChanges(
        putMints + other.putMints,
        removeMints + other.removeMints,
        putProofs + other.putProofs,
        removeProofs + other.removeProofs,
        putCounters + other.putCounters,
        putOperations + other.putOperations,
        removeOperations + other.removeOperations,
        putHistory + other.putHistory,
        removeHistory + other.removeHistory,
        putSettings + other.putSettings,
    )
}

/** In-memory ledger mirrored by the engine; also a [WalletStore] for tests and previews. */
internal data class Ledger(
    val mints: Map<String, MintRecord> = emptyMap(),
    val proofs: Map<String, StoredProof> = emptyMap(),
    val counters: Map<String, Long> = emptyMap(),
    val operations: Map<String, Operation> = emptyMap(),
    val history: Map<String, HistoryEntry> = emptyMap(),
    val settings: Map<String, String> = emptyMap(),
) {
    fun apply(c: WalletChanges): Ledger = Ledger(
        mints = (mints - c.removeMints.toSet()) + c.putMints.associateBy { it.url },
        proofs = (proofs - c.removeProofs.toSet()) + c.putProofs.associateBy { it.proof.secret },
        counters = counters + c.putCounters,
        operations = (operations - c.removeOperations.toSet()) + c.putOperations.associateBy { it.id },
        history = (history - c.removeHistory.toSet()) + c.putHistory.associateBy { it.id },
        settings = c.putSettings.entries.fold(settings) { acc, (k, v) -> if (v == null) acc - k else acc + (k to v) },
    )

    fun snapshot() = WalletSnapshot(
        mints.values.toList(), proofs.values.toList(), counters,
        operations.values.toList(), history.values.toList(), settings,
    )

    companion object {
        fun from(s: WalletSnapshot) = Ledger(
            s.mints.associateBy { it.url },
            s.proofs.associateBy { it.proof.secret },
            s.counters,
            s.operations.associateBy { it.id },
            s.history.associateBy { it.id },
            s.settings,
        )
    }
}

class InMemoryWalletStore(initial: WalletSnapshot = WalletSnapshot()) : WalletStore {
    private val lock = Any()
    private var ledger = Ledger.from(initial)

    /** Test hook: throw from the next commit to simulate a full disk. */
    @Volatile
    var failNextCommit: Boolean = false

    override suspend fun load(): WalletSnapshot = synchronized(lock) { ledger.snapshot() }

    override suspend fun commit(changes: WalletChanges) {
        synchronized(lock) {
            if (failNextCommit) {
                failNextCommit = false
                throw java.io.IOException("simulated storage failure")
            }
            ledger = ledger.apply(changes)
        }
    }

    override suspend fun wipe(): Boolean {
        synchronized(lock) { ledger = Ledger() }
        return true
    }

    fun snapshot(): WalletSnapshot = synchronized(lock) { ledger.snapshot() }
}
