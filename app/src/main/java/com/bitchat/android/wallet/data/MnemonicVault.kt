package com.bitchat.android.wallet.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bitchat.android.wallet.cashu.Bip39

/**
 * Holds the wallet's recovery words in their own encrypted preferences file, apart from the
 * Bitchat identity, so the wallet can be wiped or replaced without touching identity keys.
 * The derived seed is cached in memory only. Nothing here is ever logged.
 */
class MnemonicVault internal constructor(private val prefs: SharedPreferences) {
    @Volatile
    private var cachedSeed: ByteArray? = null
    private val lock = Any()

    fun hasWallet(): Boolean = prefs.contains(KEY_MNEMONIC)

    fun words(): List<String>? = prefs.getString(KEY_MNEMONIC, null)?.split(' ')

    /** BIP-39 seed for the stored words; computed once per process (PBKDF2 is deliberately slow). */
    fun seed(): ByteArray? {
        cachedSeed?.let { return it }
        return synchronized(lock) {
            cachedSeed ?: words()?.let(Bip39::toSeed)?.also { cachedSeed = it }
        }
    }

    /** Stores validated [words]. Synchronous: the wallet must not exist without its words. */
    fun store(words: List<String>): Boolean {
        require(Bip39.validate(words) == Bip39.Validation.Valid) { "invalid recovery words" }
        synchronized(lock) {
            cachedSeed = null
            return prefs.edit().putString(KEY_MNEMONIC, words.joinToString(" ")).remove(KEY_BACKED_UP).commit()
        }
    }

    var backedUp: Boolean
        get() = prefs.getBoolean(KEY_BACKED_UP, false)
        set(value) = prefs.edit { putBoolean(KEY_BACKED_UP, value) }

    fun clear(): Boolean = synchronized(lock) {
        cachedSeed?.fill(0)
        cachedSeed = null
        prefs.edit().clear().commit() && !prefs.contains(KEY_MNEMONIC)
    }

    companion object {
        private const val PREFS_NAME = "bitchat_wallet_secrets"
        private const val KEY_MNEMONIC = "mnemonic"
        private const val KEY_BACKED_UP = "backed_up"

        // EncryptedSharedPreferences is deprecated upstream but is what Bitchat's identity
        // storage uses; staying on it keeps one at-rest scheme for secrets in the app.
        @Suppress("DEPRECATION")
        fun create(context: Context): MnemonicVault {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return MnemonicVault(prefs)
        }

        /** Deletes the preferences file itself after clearing, so nothing is left on disk. */
        fun deleteFile(context: Context): Boolean = context.deleteSharedPreferences(PREFS_NAME)
    }
}
