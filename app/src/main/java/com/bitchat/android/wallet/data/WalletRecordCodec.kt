package com.bitchat.android.wallet.data

import com.bitchat.android.wallet.cashu.DleqProof
import com.bitchat.android.wallet.cashu.Keyset
import com.bitchat.android.wallet.cashu.MethodSetting
import com.bitchat.android.wallet.cashu.MintInfo
import com.bitchat.android.wallet.cashu.Proof
import com.bitchat.android.wallet.engine.HistoryEntry
import com.bitchat.android.wallet.engine.HistoryKind
import com.bitchat.android.wallet.engine.HistoryNote
import com.bitchat.android.wallet.engine.HistoryStatus
import com.bitchat.android.wallet.engine.MintRecord
import com.bitchat.android.wallet.engine.Operation
import com.bitchat.android.wallet.engine.OperationKind
import com.bitchat.android.wallet.engine.PendingOutput
import com.bitchat.android.wallet.engine.ProofStatus
import com.bitchat.android.wallet.engine.StoredProof
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger

/**
 * Hand-written JSON for persisted wallet records. Reflection-free so R8 cannot break it, and
 * explicit about every field so a schema change is a visible diff. Unknown enum values fall
 * back conservatively (e.g. an unknown proof status loads as RESERVED, never AVAILABLE).
 */
internal object WalletRecordCodec {

    fun encodeMint(m: MintRecord): String = JsonObject().apply {
        addProperty("url", m.url)
        m.info?.let { add("info", encodeInfo(it)) }
        add("keysets", JsonArray().apply { m.keysets.forEach { add(encodeKeyset(it)) } })
        addProperty("addedAt", m.addedAt)
        addProperty("updatedAt", m.updatedAt)
    }.toString()

    fun decodeMint(json: String): MintRecord = obj(json).let { o ->
        MintRecord(
            url = o.s("url")!!,
            info = (o.get("info") as? JsonObject)?.let(::decodeInfo),
            keysets = o.arr("keysets").map { decodeKeyset(it.asJsonObject) },
            addedAt = o.l("addedAt") ?: 0,
            updatedAt = o.l("updatedAt") ?: 0,
        )
    }

    fun encodeProof(p: StoredProof): String = JsonObject().apply {
        add("proof", proofJson(p.proof))
        addProperty("mint", p.mintUrl)
        addProperty("unit", p.unit)
        addProperty("status", p.status.name)
        p.ref?.let { addProperty("ref", it) }
    }.toString()

    fun decodeProof(json: String): StoredProof = obj(json).let { o ->
        StoredProof(
            proof = proofFrom(o.getAsJsonObject("proof")),
            mintUrl = o.s("mint")!!,
            unit = o.s("unit")!!,
            status = enumOr(o.s("status"), ProofStatus.RESERVED),
            ref = o.s("ref"),
        )
    }

    fun encodeOperation(op: Operation): String = JsonObject().apply {
        addProperty("id", op.id)
        addProperty("kind", op.kind.name)
        addProperty("mint", op.mintUrl)
        addProperty("unit", op.unit)
        addProperty("history", op.historyId)
        add("inputs", JsonArray().apply { op.inputs.forEach { add(proofJson(it)) } })
        add("outputs", JsonArray().apply {
            op.outputs.forEach { out ->
                add(JsonObject().apply {
                    addProperty("a", out.amount)
                    addProperty("k", out.keysetId)
                    addProperty("s", out.secret)
                    addProperty("r", out.r.toString(16))
                    addProperty("b", out.b)
                    if (out.forSend) addProperty("send", true)
                })
            }
        })
        op.quoteId?.let { addProperty("quote", it) }
        addProperty("createdAt", op.createdAt)
    }.toString()

    fun decodeOperation(json: String): Operation = obj(json).let { o ->
        Operation(
            id = o.s("id")!!,
            kind = OperationKind.valueOf(o.s("kind")!!),
            mintUrl = o.s("mint")!!,
            unit = o.s("unit")!!,
            historyId = o.s("history")!!,
            inputs = o.arr("inputs").map { proofFrom(it.asJsonObject) },
            outputs = o.arr("outputs").map { e ->
                val out = e.asJsonObject
                PendingOutput(
                    amount = out.l("a")!!,
                    keysetId = out.s("k")!!,
                    secret = out.s("s")!!,
                    r = BigInteger(out.s("r")!!, 16),
                    b = out.s("b")!!,
                    forSend = out.get("send")?.asBoolean == true,
                )
            },
            quoteId = o.s("quote"),
            createdAt = o.l("createdAt") ?: 0,
        )
    }

    fun encodeHistory(h: HistoryEntry): String = JsonObject().apply {
        addProperty("id", h.id)
        addProperty("kind", h.kind.name)
        addProperty("status", h.status.name)
        addProperty("amount", h.amount)
        addProperty("fee", h.fee)
        addProperty("unit", h.unit)
        addProperty("mint", h.mintUrl)
        addProperty("createdAt", h.createdAt)
        addProperty("updatedAt", h.updatedAt)
        addProperty("note", h.note.name)
        h.memo?.let { addProperty("memo", it) }
        h.token?.let { addProperty("token", it) }
        h.invoice?.let { addProperty("invoice", it) }
        h.quoteId?.let { addProperty("quote", it) }
        h.quoteExpiry?.let { addProperty("quoteExpiry", it) }
        if (h.viaChat) addProperty("viaChat", true)
        h.lockedUntil?.let { addProperty("lockedUntil", it) }
        if (h.lockedToPeer) addProperty("lockedToPeer", true)
        h.preimage?.let { addProperty("preimage", it) }
        h.tokenFingerprint?.let { addProperty("fingerprint", it) }
    }.toString()

    fun decodeHistory(json: String): HistoryEntry = obj(json).let { o ->
        HistoryEntry(
            id = o.s("id")!!,
            kind = HistoryKind.valueOf(o.s("kind")!!),
            status = enumOr(o.s("status"), HistoryStatus.PENDING),
            amount = o.l("amount") ?: 0,
            fee = o.l("fee") ?: 0,
            unit = o.s("unit") ?: "sat",
            mintUrl = o.s("mint") ?: "",
            createdAt = o.l("createdAt") ?: 0,
            updatedAt = o.l("updatedAt") ?: 0,
            note = enumOr(o.s("note"), HistoryNote.NONE),
            memo = o.s("memo"),
            token = o.s("token"),
            invoice = o.s("invoice"),
            quoteId = o.s("quote"),
            quoteExpiry = o.l("quoteExpiry"),
            viaChat = o.get("viaChat")?.asBoolean == true,
            lockedUntil = o.l("lockedUntil"),
            lockedToPeer = o.get("lockedToPeer")?.asBoolean == true,
            preimage = o.s("preimage"),
            tokenFingerprint = o.s("fingerprint"),
        )
    }

    private fun encodeInfo(i: MintInfo) = JsonObject().apply {
        i.name?.let { addProperty("name", it) }
        i.pubkey?.let { addProperty("pubkey", it) }
        i.version?.let { addProperty("version", it) }
        i.description?.let { addProperty("description", it) }
        i.descriptionLong?.let { addProperty("descriptionLong", it) }
        i.motd?.let { addProperty("motd", it) }
        i.iconUrl?.let { addProperty("iconUrl", it) }
        add("nuts", JsonArray().apply { i.supportedNuts.sorted().forEach(::add) })
        add("mintMethods", methods(i.mintMethods))
        add("meltMethods", methods(i.meltMethods))
        addProperty("mintingDisabled", i.mintingDisabled)
        addProperty("meltingDisabled", i.meltingDisabled)
    }

    private fun decodeInfo(o: JsonObject) = MintInfo(
        name = o.s("name"), pubkey = o.s("pubkey"), version = o.s("version"), description = o.s("description"),
        descriptionLong = o.s("descriptionLong"), motd = o.s("motd"), iconUrl = o.s("iconUrl"),
        supportedNuts = o.arr("nuts").map { it.asInt }.toSet(),
        mintMethods = methodsFrom(o.arr("mintMethods")),
        meltMethods = methodsFrom(o.arr("meltMethods")),
        mintingDisabled = o.get("mintingDisabled")?.asBoolean == true,
        meltingDisabled = o.get("meltingDisabled")?.asBoolean == true,
    )

    private fun methods(list: List<MethodSetting>) = JsonArray().apply {
        list.forEach { m ->
            add(JsonObject().apply {
                addProperty("method", m.method)
                addProperty("unit", m.unit)
                m.minAmount?.let { addProperty("min", it) }
                m.maxAmount?.let { addProperty("max", it) }
            })
        }
    }

    private fun methodsFrom(a: JsonArray) = a.map { e ->
        val m = e.asJsonObject
        MethodSetting(m.s("method")!!, m.s("unit")!!, m.l("min"), m.l("max"))
    }

    private fun encodeKeyset(k: Keyset) = JsonObject().apply {
        addProperty("id", k.id)
        addProperty("unit", k.unit)
        addProperty("active", k.active)
        addProperty("fee", k.inputFeePpk)
        k.finalExpiry?.let { addProperty("expiry", it) }
        add("keys", JsonObject().apply { k.keys.forEach { (amount, key) -> addProperty(amount.toString(), key) } })
    }

    private fun decodeKeyset(o: JsonObject) = Keyset(
        id = o.s("id")!!,
        unit = o.s("unit")!!,
        active = o.get("active")?.asBoolean == true,
        inputFeePpk = o.l("fee") ?: 0,
        finalExpiry = o.l("expiry"),
        keys = o.getAsJsonObject("keys").entrySet().associate { (amount, key) -> BigInteger(amount) to key.asString },
    )

    private fun proofJson(p: Proof) = JsonObject().apply {
        addProperty("amount", p.amount)
        addProperty("id", p.keysetId)
        addProperty("secret", p.secret)
        addProperty("C", p.c)
        p.dleq?.let { d ->
            add("dleq", JsonObject().apply {
                addProperty("e", d.e)
                addProperty("s", d.s)
                d.r?.let { addProperty("r", it) }
            })
        }
        p.witness?.let { addProperty("witness", it) }
    }

    private fun proofFrom(o: JsonObject) = Proof(
        amount = o.l("amount")!!,
        keysetId = o.s("id")!!,
        secret = o.s("secret")!!,
        c = o.s("C")!!,
        dleq = (o.get("dleq") as? JsonObject)?.let { DleqProof(it.s("e")!!, it.s("s")!!, it.s("r")) },
        witness = o.s("witness"),
    )

    private fun obj(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    private fun JsonObject.s(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.l(key: String): Long? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong

    private fun JsonObject.arr(key: String): JsonArray =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback
}
