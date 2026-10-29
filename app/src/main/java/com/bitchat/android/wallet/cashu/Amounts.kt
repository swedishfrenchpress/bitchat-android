package com.bitchat.android.wallet.cashu

/** Denomination splitting, NUT-02 fees and coin selection. */
internal object Amounts {

    /** Power-of-two split restricted to the denominations [available] in the keyset, largest first. */
    fun split(amount: Long, available: List<Long>): List<Long> {
        require(amount >= 0) { "negative amount" }
        if (amount == 0L) return emptyList()
        val denominations = available.filter { it > 0 }.sortedDescending()
        require(denominations.isNotEmpty()) { "keyset has no denominations" }
        val result = ArrayList<Long>()
        var remaining = amount
        for (d in denominations) {
            while (remaining >= d) {
                result += d
                remaining -= d
                require(result.size <= 10_000) { "too many outputs" }
            }
        }
        require(remaining == 0L) { "amount not representable" }
        return result
    }

    /** Sum of per-input fees, rounded up: `ceil(sum(input_fee_ppk) / 1000)`. */
    fun inputFee(proofs: List<Proof>, feePpkByKeyset: Map<String, Long>): Long {
        val ppk = proofs.sumOf { feePpkByKeyset[it.keysetId] ?: 0L }
        return (ppk + 999) / 1000
    }

    fun inputFee(count: Int, feePpk: Long): Long = (count * feePpk + 999) / 1000

    /**
     * NUT-08 blank outputs needed to receive up to [overpaid] back. One per bit, at least one
     * whenever anything could come back.
     */
    fun blankOutputCount(overpaid: Long): Int {
        if (overpaid <= 0) return 0
        val ceilLog2 = if (overpaid == 1L) 0 else 64 - java.lang.Long.numberOfLeadingZeros(overpaid - 1)
        return maxOf(ceilLog2, 1)
    }

    data class Selection(val proofs: List<Proof>, val total: Long, val fee: Long) {
        /** What is left after paying [target] and the input fee. */
        fun change(target: Long): Long = total - fee - target
    }

    /**
     * Picks proofs whose sum covers [target] plus their own input fee. Prefers proofs from
     * inactive keysets (NUT-02 asks wallets to rotate those out), then largest first so the
     * input count (and with it the fee) stays low. Returns null if the balance is short.
     */
    fun select(
        proofs: List<Proof>,
        target: Long,
        feePpkByKeyset: Map<String, Long>,
        inactiveKeysets: Set<String> = emptySet(),
    ): Selection? {
        if (target <= 0) return null
        val ordered = proofs.sortedWith(
            compareByDescending<Proof> { it.keysetId in inactiveKeysets }.thenByDescending { it.amount }
        )
        // Greedy pass on largest-first, then trim: drop the smallest picks that are not needed.
        val picked = ArrayList<Proof>()
        var total = 0L
        for (proof in ordered) {
            if (total >= target + inputFee(picked, feePpkByKeyset)) break
            picked += proof
            total += proof.amount
        }
        if (total < target + inputFee(picked, feePpkByKeyset)) return null
        // Inactive-keyset proofs stay picked: spending them is the point of preferring them.
        for (proof in picked.filter { it.keysetId !in inactiveKeysets }.sortedBy { it.amount }) {
            val without = picked - proof
            val withoutTotal = total - proof.amount
            if (without.isNotEmpty() && withoutTotal >= target + inputFee(without, feePpkByKeyset)) {
                picked.remove(proof)
                total = withoutTotal
            }
        }
        return Selection(picked.toList(), total, inputFee(picked, feePpkByKeyset))
    }

    /**
     * Finds proofs that sum to exactly [target], for sending without the mint (offline). Bounded
     * subset search over at most 24 candidates, so it is cheap and never stalls the UI.
     */
    fun exactMatch(proofs: List<Proof>, target: Long): List<Proof>? {
        if (target <= 0) return null
        val candidates = proofs.filter { it.amount <= target }.sortedByDescending { it.amount }.take(24)
        // Greedy usually works for power-of-two wallets.
        var remaining = target
        val greedy = ArrayList<Proof>()
        for (p in candidates) {
            if (p.amount <= remaining) {
                greedy += p
                remaining -= p.amount
            }
            if (remaining == 0L) return greedy
        }
        fun search(start: Int, left: Long, acc: MutableList<Proof>): List<Proof>? {
            if (left == 0L) return acc.toList()
            for (i in start until candidates.size) {
                val p = candidates[i]
                if (p.amount > left) continue
                acc += p
                search(i + 1, left - p.amount, acc)?.let { return it }
                acc.removeAt(acc.lastIndex)
            }
            return null
        }
        return search(0, target, ArrayList())
    }

    fun checkedSum(values: Iterable<Long>): Long {
        var total = 0L
        for (v in values) {
            require(v >= 0) { "negative amount" }
            total = Math.addExact(total, v)
        }
        return total
    }
}
