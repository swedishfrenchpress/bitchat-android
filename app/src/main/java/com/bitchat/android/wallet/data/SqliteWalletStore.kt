package com.bitchat.android.wallet.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.bitchat.android.services.AndroidConversationStorageCipher
import com.bitchat.android.services.ConversationStorageCipher
import com.bitchat.android.wallet.cashu.Hex
import com.bitchat.android.wallet.cashu.Secp256k1
import com.bitchat.android.wallet.engine.WalletChanges
import com.bitchat.android.wallet.engine.WalletSnapshot
import com.bitchat.android.wallet.engine.WalletStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wallet storage: one SQLite table of sealed records.
 *
 * Every payload is AES-GCM sealed with a dedicated Android Keystore key (the same envelope as
 * private conversations), with the row's kind and id as associated data so a record cannot be
 * moved to another row. Row ids are hashes of the natural key, so proof secrets and mint URLs
 * never appear in plaintext indexes. Destroying the key on wipe makes any residue in SQLite
 * pages, WAL frames, or freed blocks unreadable.
 */
class SqliteWalletStore internal constructor(
    context: Context,
    private val databaseName: String = DATABASE_NAME,
    private val cipher: ConversationStorageCipher = AndroidConversationStorageCipher(KEY_ALIAS),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WalletStore {

    constructor(context: Context) : this(context, DATABASE_NAME)

    private val appContext = context.applicationContext
    private val lock = Any()
    private var helper: Helper? = null

    /**
     * Set by [wipe] and never cleared. A straggling write after a panic wipe (an operation
     * finishing in the background) must fail rather than recreate the database under a new key.
     */
    @Volatile
    private var wiped = false

    private class Helper(context: Context, name: String) : SQLiteOpenHelper(context, name, null, VERSION) {
        init {
            setWriteAheadLoggingEnabled(true)
        }

        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.rawQuery("PRAGMA secure_delete = ON", null).use { it.moveToFirst() }
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE records (
                    kind TEXT NOT NULL,
                    id TEXT NOT NULL,
                    payload BLOB NOT NULL,
                    PRIMARY KEY (kind, id)
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private fun db(): SQLiteDatabase = synchronized(lock) {
        if (wiped) throw java.io.IOException("wallet store was wiped")
        (helper ?: Helper(appContext, databaseName).also { helper = it }).writableDatabase
    }

    override suspend fun load(): WalletSnapshot = withContext(dispatcher) {
        val mints = ArrayList<com.bitchat.android.wallet.engine.MintRecord>()
        val proofs = ArrayList<com.bitchat.android.wallet.engine.StoredProof>()
        val counters = HashMap<String, Long>()
        val operations = ArrayList<com.bitchat.android.wallet.engine.Operation>()
        val history = ArrayList<com.bitchat.android.wallet.engine.HistoryEntry>()
        val settings = HashMap<String, String>()
        db().rawQuery("SELECT kind, id, payload FROM records", null).use { cursor ->
            while (cursor.moveToNext()) {
                val kind = cursor.getString(0)
                val id = cursor.getString(1)
                val json = runCatching {
                    String(cipher.decrypt(cursor.getBlob(2), aad(kind, id)), Charsets.UTF_8)
                }.getOrElse {
                    // An unreadable row is skipped rather than crashing the wallet. Its content
                    // is never logged.
                    Log.w(TAG, "Skipping unreadable wallet record of kind $kind")
                    continue
                }
                runCatching {
                    when (kind) {
                        KIND_MINT -> mints += WalletRecordCodec.decodeMint(json)
                        KIND_PROOF -> proofs += WalletRecordCodec.decodeProof(json)
                        KIND_OPERATION -> operations += WalletRecordCodec.decodeOperation(json)
                        KIND_HISTORY -> history += WalletRecordCodec.decodeHistory(json)
                        KIND_COUNTER -> json.split('\n', limit = 2).let { counters[it[0]] = it[1].toLong() }
                        KIND_SETTING -> json.split('\n', limit = 2).let { settings[it[0]] = it[1] }
                    }
                }.onFailure { Log.w(TAG, "Skipping malformed wallet record of kind $kind") }
            }
        }
        WalletSnapshot(mints, proofs, counters, operations, history, settings)
    }

    override suspend fun commit(changes: WalletChanges) = withContext(dispatcher) {
        val db = db()
        db.beginTransaction()
        try {
            changes.removeMints.forEach { delete(db, KIND_MINT, it) }
            changes.putMints.forEach { put(db, KIND_MINT, it.url, WalletRecordCodec.encodeMint(it)) }
            changes.removeProofs.forEach { delete(db, KIND_PROOF, it) }
            changes.putProofs.forEach { put(db, KIND_PROOF, it.proof.secret, WalletRecordCodec.encodeProof(it)) }
            changes.putCounters.forEach { (keyset, value) -> put(db, KIND_COUNTER, keyset, "$keyset\n$value") }
            changes.removeOperations.forEach { delete(db, KIND_OPERATION, it) }
            changes.putOperations.forEach { put(db, KIND_OPERATION, it.id, WalletRecordCodec.encodeOperation(it)) }
            changes.removeHistory.forEach { delete(db, KIND_HISTORY, it) }
            changes.putHistory.forEach { put(db, KIND_HISTORY, it.id, WalletRecordCodec.encodeHistory(it)) }
            changes.putSettings.forEach { (key, value) ->
                if (value == null) delete(db, KIND_SETTING, key) else put(db, KIND_SETTING, key, "$key\n$value")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun wipe(): Boolean = withContext(dispatcher) {
        synchronized(lock) {
            wiped = true
            runCatching { helper?.close() }
            helper = null
        }
        val keyDestroyed = runCatching { cipher.destroyKey() }.isSuccess
        val deleted = appContext.deleteDatabase(databaseName) || !appContext.getDatabasePath(databaseName).exists()
        val leftovers = listOf("", "-wal", "-shm", "-journal").any { appContext.getDatabasePath(databaseName + it).exists() }
        keyDestroyed && deleted && !leftovers
    }

    private fun put(db: SQLiteDatabase, kind: String, naturalKey: String, json: String) {
        val id = rowId(kind, naturalKey)
        db.insertWithOnConflict(
            "records",
            null,
            ContentValues().apply {
                put("kind", kind)
                put("id", id)
                put("payload", cipher.encrypt(json.toByteArray(Charsets.UTF_8), aad(kind, id)))
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun delete(db: SQLiteDatabase, kind: String, naturalKey: String) {
        db.delete("records", "kind = ? AND id = ?", arrayOf(kind, rowId(kind, naturalKey)))
    }

    private fun rowId(kind: String, naturalKey: String): String =
        Hex.encode(Secp256k1.sha256("bitchat-wallet-row:$kind:".toByteArray(), naturalKey.toByteArray()))

    private fun aad(kind: String, id: String): ByteArray = "bitchat-wallet:v1:$kind:$id".toByteArray()

    companion object {
        private const val TAG = "SqliteWalletStore"
        internal const val DATABASE_NAME = "cashu_wallet.db"
        internal const val KEY_ALIAS = "bitchat_wallet_storage_v1"
        private const val VERSION = 1
        private const val KIND_MINT = "mint"
        private const val KIND_PROOF = "proof"
        private const val KIND_COUNTER = "counter"
        private const val KIND_OPERATION = "op"
        private const val KIND_HISTORY = "history"
        private const val KIND_SETTING = "setting"
    }
}
