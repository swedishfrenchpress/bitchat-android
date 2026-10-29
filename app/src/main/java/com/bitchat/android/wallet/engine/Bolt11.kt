package com.bitchat.android.wallet.engine

import java.math.BigDecimal

/**
 * Just enough BOLT-11 to recognise an invoice and read the amount from its human-readable
 * part. The signature and tagged fields are not checked here; the mint's quote is the
 * authority on what will be paid.
 */
object Bolt11 {
    data class Invoice(val raw: String, val network: String, val amountMsat: Long?) {
        val amountSat: Long? get() = amountMsat?.let { (it + 999) / 1000 }
    }

    private val NETWORKS = listOf("bcrt", "tbs", "tb", "bc", "sb")
    private val BECH32 = Regex("^[qpzry9x8gf2tvdw0s3jn54khce6mua7l]+$")

    fun parse(input: String): Invoice? {
        var s = input.trim()
        if (s.startsWith("lightning:", ignoreCase = true)) s = s.substring(10)
        s = s.lowercase()
        if (!s.startsWith("ln") || s.length < 20 || s.length > 4096) return null
        val separator = s.lastIndexOf('1')
        if (separator < 4) return null
        val hrp = s.substring(2, separator)
        if (!BECH32.matches(s.substring(separator + 1))) return null
        val network = NETWORKS.firstOrNull { hrp.startsWith(it) } ?: return null
        val amountPart = hrp.substring(network.length)
        if (amountPart.isEmpty()) return Invoice(s, network, null)
        val multiplier = amountPart.last()
        val digits = if (multiplier.isDigit()) amountPart else amountPart.dropLast(1)
        if (digits.isEmpty() || !digits.all(Char::isDigit) || digits.length > 18 || digits.startsWith("0")) return null
        // Amount in BTC = digits * multiplier; 1 BTC = 1e11 msat.
        val btc = BigDecimal(digits).multiply(
            when (multiplier) {
                'm' -> BigDecimal("0.001")
                'u' -> BigDecimal("0.000001")
                'n' -> BigDecimal("0.000000001")
                'p' -> BigDecimal("0.000000000001")
                else -> if (multiplier.isDigit()) BigDecimal.ONE else return null
            }
        )
        val msat = btc.multiply(BigDecimal("100000000000"))
        val exact = runCatching { msat.longValueExact() }.getOrNull() ?: return null
        if (exact <= 0) return null
        return Invoice(s, network, exact)
    }
}
