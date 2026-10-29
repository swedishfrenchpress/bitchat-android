package com.bitchat.android.wallet.cashu

import java.math.BigInteger

/** NUT-02 keyset ID derivation, used to verify that a mint's keys match its advertised IDs. */
internal object KeysetIds {

    fun deriveV1(keys: Map<BigInteger, String>): String {
        val concatenated = keys.entries.sortedBy { it.key }
            .fold(ByteArray(0)) { acc, entry -> acc + Hex.decode(entry.value) }
        return "00" + Hex.encode(Secp256k1.sha256(concatenated)).substring(0, 14)
    }

    fun deriveV2(
        keys: Map<BigInteger, String>,
        unit: String,
        inputFeePpk: Long?,
        finalExpiry: Long?,
    ): String {
        val preimage = StringBuilder()
        keys.entries.sortedBy { it.key }.joinTo(preimage, ",") { "${it.key}:${it.value.lowercase()}" }
        preimage.append("|unit:").append(unit.lowercase())
        if (inputFeePpk != null && inputFeePpk != 0L) preimage.append("|input_fee_ppk:").append(inputFeePpk)
        if (finalExpiry != null && finalExpiry != 0L) preimage.append("|final_expiry:").append(finalExpiry)
        return "01" + Hex.encode(Secp256k1.sha256(preimage.toString().toByteArray(Charsets.UTF_8)))
    }

    /** True when [keyset]'s ID matches its keys under the derivation its version byte names. */
    fun matches(keyset: Keyset): Boolean = runCatching {
        when {
            keyset.id.startsWith("00") && keyset.id.length == 16 -> deriveV1(keyset.keys) == keyset.id
            keyset.id.startsWith("01") && keyset.id.length == 66 ->
                deriveV2(keyset.keys, keyset.unit, keyset.inputFeePpk, keyset.finalExpiry) == keyset.id
            else -> false
        }
    }.getOrDefault(false)

    /** The 8-byte short form used in V4 tokens. */
    fun short(id: String): String = id.take(16)
}
