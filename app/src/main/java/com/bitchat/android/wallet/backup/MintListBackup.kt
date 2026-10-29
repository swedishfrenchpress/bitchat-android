package com.bitchat.android.wallet.backup

import com.bitchat.android.nostr.NostrEvent
import com.bitchat.android.nostr.NostrFilter
import com.bitchat.android.wallet.cashu.Hex
import com.bitchat.android.wallet.cashu.MintUrl
import com.bitchat.android.wallet.cashu.Nut13
import com.bitchat.android.wallet.cashu.P2pk
import com.bitchat.android.wallet.cashu.Secp256k1
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * NUT-27 encrypted mint-list backup on Nostr.
 *
 * The signing key is derived from the wallet seed (`SHA256(seed || "cashu-mint-backup")`), not
 * from the Bitchat identity, so a backup cannot be linked to a person's chat identity. The list
 * is NIP-44 v2 encrypted to that same key. The optional `client` tag is omitted on purpose: it
 * would announce which app produced the backup.
 */
object MintListBackup {
    const val KIND = 30078
    const val D_TAG = "mint-list"

    data class Keys(val privateKeyHex: String, val publicKeyHex: String)

    data class Backup(val mints: List<String>, val timestamp: Long)

    fun keys(seed: ByteArray): Keys {
        val key = Nut13.mintBackupPrivateKey(seed)
        return Keys(Hex.encode(Secp256k1.scalarBytes(key)), Secp256k1.xOnlyHex(P2pk.compressedPublicKey(key)))
    }

    fun buildEvent(seed: ByteArray, mints: List<String>, nowSeconds: Long): NostrEvent {
        val keys = keys(seed)
        val plaintext = JsonObject().apply {
            add("mints", JsonArray().apply { mints.distinct().forEach(::add) })
            addProperty("timestamp", nowSeconds)
        }.toString()
        val conversationKey = Nip44.conversationKey(Nut13.mintBackupPrivateKey(seed), keys.publicKeyHex)
        return NostrEvent(
            pubkey = keys.publicKeyHex,
            createdAt = nowSeconds.toInt(),
            kind = KIND,
            tags = listOf(listOf("d", D_TAG)),
            content = Nip44.encrypt(plaintext, conversationKey),
        ).sign(keys.privateKeyHex)
    }

    fun filter(seed: ByteArray): NostrFilter =
        NostrFilter.Builder().kinds(KIND).authors(keys(seed).publicKeyHex).tag("d", D_TAG).limit(5).build()

    /**
     * Decrypts the newest valid backup among [events]. Events by other authors, bad
     * signatures, and undecryptable content are ignored. Mint URLs are normalized and
     * anything that is not a valid mint URL is dropped.
     */
    fun newest(seed: ByteArray, events: List<NostrEvent>): Backup? {
        val keys = keys(seed)
        val conversationKey = Nip44.conversationKey(Nut13.mintBackupPrivateKey(seed), keys.publicKeyHex)
        return events
            .filter { it.kind == KIND && it.pubkey == keys.publicKeyHex && it.tags.any { t -> t.getOrNull(0) == "d" && t.getOrNull(1) == D_TAG } }
            .filter { runCatching { it.isValidSignature() }.getOrDefault(false) }
            .sortedByDescending { it.createdAt }
            .firstNotNullOfOrNull { event ->
                runCatching {
                    val o = JsonParser.parseString(Nip44.decrypt(event.content, conversationKey)).asJsonObject
                    val mints = o.getAsJsonArray("mints").mapNotNull { m -> m.takeIf { it.isJsonPrimitive }?.asString?.let(MintUrl::normalizeOrNull) }
                    Backup(mints.distinct().take(MAX_MINTS), o.get("timestamp")?.asLong ?: event.createdAt.toLong())
                }.getOrNull()
            }
    }

    private const val MAX_MINTS = 50
}
