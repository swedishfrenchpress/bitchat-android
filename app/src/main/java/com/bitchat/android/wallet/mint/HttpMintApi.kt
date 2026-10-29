package com.bitchat.android.wallet.mint

import com.bitchat.android.wallet.cashu.BlindSignature
import com.bitchat.android.wallet.cashu.BlindedMessage
import com.bitchat.android.wallet.cashu.DleqProof
import com.bitchat.android.wallet.cashu.Keyset
import com.bitchat.android.wallet.cashu.KeysetInfo
import com.bitchat.android.wallet.cashu.MeltQuote
import com.bitchat.android.wallet.cashu.MeltQuoteState
import com.bitchat.android.wallet.cashu.MethodSetting
import com.bitchat.android.wallet.cashu.MintInfo
import com.bitchat.android.wallet.cashu.MintQuote
import com.bitchat.android.wallet.cashu.MintQuoteState
import com.bitchat.android.wallet.cashu.MintUrl
import com.bitchat.android.wallet.cashu.Proof
import com.bitchat.android.wallet.cashu.ProofState
import com.bitchat.android.wallet.cashu.ProofStateResult
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.math.BigInteger
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OkHttp implementation of [MintApi].
 *
 * JSON is built and read by hand rather than through Gson reflection, so release builds need
 * no extra keep rules and unknown fields are ignored per the NUTs. [clientProvider] is asked
 * for a client on every call so a Tor mode change takes effect immediately.
 */
class HttpMintApi(
    url: String,
    private val clientProvider: () -> OkHttpClient,
) : MintApi {
    override val url: String = MintUrl.normalize(url)

    override suspend fun info(): MintInfo = parseInfo(get("/v1/info"))

    override suspend fun keysets(): List<KeysetInfo> =
        get("/v1/keysets").array("keysets").map { element ->
            val o = element.asJsonObject
            KeysetInfo(
                id = o.str("id") ?: malformed("keyset id"),
                unit = o.str("unit") ?: malformed("keyset unit"),
                active = o.bool("active") ?: true,
                inputFeePpk = o.long("input_fee_ppk") ?: 0,
                finalExpiry = o.long("final_expiry"),
            )
        }

    override suspend fun keys(keysetId: String): Keyset {
        val list = get("/v1/keys/${path(keysetId)}").array("keysets")
        val o = list.map { it.asJsonObject }.firstOrNull { it.str("id") == keysetId }
            ?: malformed("requested keyset missing")
        val keys = (o.get("keys") as? JsonObject ?: malformed("keys")).entrySet().associate { (amount, key) ->
            val value = runCatching { BigInteger(amount) }.getOrNull()?.takeIf { it.signum() > 0 }
                ?: malformed("key amount")
            value to (key.takeIf { it.isJsonPrimitive }?.asString?.lowercase() ?: malformed("key"))
        }
        return Keyset(
            id = keysetId,
            unit = o.str("unit") ?: malformed("keyset unit"),
            active = o.bool("active") ?: true,
            inputFeePpk = o.long("input_fee_ppk") ?: 0,
            finalExpiry = o.long("final_expiry"),
            keys = keys,
        )
    }

    override suspend fun mintQuote(amount: Long, unit: String): MintQuote =
        parseMintQuote(post("/v1/mint/quote/bolt11", JsonObject().apply {
            addProperty("amount", amount)
            addProperty("unit", unit)
        }))

    override suspend fun checkMintQuote(quote: String): MintQuote =
        parseMintQuote(get("/v1/mint/quote/bolt11/${path(quote)}"))

    override suspend fun mint(quote: String, outputs: List<BlindedMessage>): List<BlindSignature> {
        val response = post("/v1/mint/bolt11", JsonObject().apply {
            addProperty("quote", quote)
            add("outputs", outputs.toJson())
        })
        return parseSignatures(response.arrayOrNull("signatures") ?: response.array("promises"))
    }

    override suspend fun meltQuote(request: String, unit: String): MeltQuote =
        parseMeltQuote(post("/v1/melt/quote/bolt11", JsonObject().apply {
            addProperty("request", request)
            addProperty("unit", unit)
        }))

    override suspend fun checkMeltQuote(quote: String): MeltQuote =
        parseMeltQuote(get("/v1/melt/quote/bolt11/${path(quote)}"))

    override suspend fun melt(quote: String, inputs: List<Proof>, outputs: List<BlindedMessage>): MeltQuote =
        parseMeltQuote(post("/v1/melt/bolt11", JsonObject().apply {
            addProperty("quote", quote)
            add("inputs", JsonArray().apply { inputs.forEach { add(it.toJson()) } })
            if (outputs.isNotEmpty()) add("outputs", outputs.toJson())
        }, timeoutSeconds = MELT_TIMEOUT_SECONDS))

    override suspend fun swap(inputs: List<Proof>, outputs: List<BlindedMessage>): List<BlindSignature> {
        val response = post("/v1/swap", JsonObject().apply {
            add("inputs", JsonArray().apply { inputs.forEach { add(it.toJson()) } })
            add("outputs", outputs.toJson())
        })
        return parseSignatures(response.arrayOrNull("signatures") ?: response.array("promises"))
    }

    override suspend fun checkState(ys: List<String>): List<ProofStateResult> {
        val response = post("/v1/checkstate", JsonObject().apply {
            add("Ys", JsonArray().apply { ys.forEach(::add) })
        })
        val states = response.array("states").map { element ->
            val o = element.asJsonObject
            ProofStateResult(
                y = o.str("Y") ?: malformed("state Y"),
                state = when (o.str("state")) {
                    "UNSPENT" -> ProofState.UNSPENT
                    "PENDING" -> ProofState.PENDING
                    "SPENT" -> ProofState.SPENT
                    else -> malformed("proof state")
                },
                witness = o.str("witness"),
            )
        }
        // Order must match the request; reorder defensively by Y rather than trust it.
        val byY = states.associateBy { it.y.lowercase() }
        return ys.map { byY[it.lowercase()] ?: malformed("state missing for a requested Y") }
    }

    override suspend fun restore(outputs: List<BlindedMessage>): List<Pair<BlindedMessage, BlindSignature>> {
        val response = post("/v1/restore", JsonObject().apply { add("outputs", outputs.toJson()) })
        val returnedOutputs = response.array("outputs").map { element ->
            val o = element.asJsonObject
            BlindedMessage(
                amount = o.long("amount") ?: malformed("restore amount"),
                keysetId = o.str("id") ?: malformed("restore id"),
                b = o.str("B_") ?: malformed("restore B_"),
            )
        }
        val signatures = parseSignatures(response.arrayOrNull("signatures") ?: response.array("promises"))
        if (returnedOutputs.size != signatures.size) malformed("restore length mismatch")
        val requested = outputs.associateBy { it.b.lowercase() }
        return returnedOutputs.zip(signatures).map { (output, signature) ->
            // Pair with our own output so amount and keyset come from what we derived.
            val ours = requested[output.b.lowercase()] ?: malformed("restore returned unknown output")
            ours to signature
        }
    }

    // MARK: - Transport

    private suspend fun get(path: String): JsonObject =
        execute(Request.Builder().url(url + path).get().build(), DEFAULT_TIMEOUT_SECONDS)

    private suspend fun post(path: String, body: JsonObject, timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS): JsonObject =
        execute(
            Request.Builder().url(url + path).post(body.toString().toRequestBody(JSON)).build(),
            timeoutSeconds,
        )

    private suspend fun execute(request: Request, timeoutSeconds: Long): JsonObject {
        val client = clientProvider().newBuilder()
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            // A silently replayed swap or melt would turn a lost response into a confusing
            // second answer. The wallet's own journal decides when to retry.
            .retryOnConnectionFailure(request.method == "GET")
            .build()
        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw MintException.Unreachable(e)
        }
        response.use {
            val text = try {
                it.body.source().let { source ->
                    if (source.request(MAX_RESPONSE_BYTES + 1)) throw MintException.Malformed("response too large")
                    source.buffer.readUtf8()
                }
            } catch (e: IOException) {
                throw MintException.Unreachable(e)
            }
            if (!it.isSuccessful) throw errorFor(it.code, text)
            return runCatching { JsonParser.parseString(text).asJsonObject }
                .getOrElse { throw MintException.Malformed("not a json object") }
        }
    }

    private fun errorFor(status: Int, body: String): MintException {
        if (status == 429) return MintException.RateLimited()
        if (status >= 500) return MintException.ServerError(status)
        val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
        return MintException.Rejected(code = json?.long("code")?.toInt(), status = status, detail = json?.str("detail"))
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
        })
        continuation.invokeOnCancellation { runCatching { cancel() } }
    }

    // MARK: - Parsing

    private fun parseInfo(o: JsonObject): MintInfo {
        val nuts = o.get("nuts") as? JsonObject ?: JsonObject()
        val supported = HashSet<Int>()
        nuts.entrySet().forEach { (key, value) ->
            val nut = key.toIntOrNull() ?: return@forEach
            val obj = value as? JsonObject ?: return@forEach
            val flag = obj.get("supported")
            val isSupported = when {
                flag == null -> obj.has("methods") && obj.bool("disabled") != true
                flag.isJsonPrimitive && flag.asJsonPrimitive.isBoolean -> flag.asBoolean
                flag.isJsonArray -> flag.asJsonArray.size() > 0
                else -> false
            }
            if (isSupported) supported += nut
        }
        fun methods(nut: String) = (nuts.get(nut) as? JsonObject)?.arrayOrNull("methods")?.mapNotNull { element ->
            val m = element as? JsonObject ?: return@mapNotNull null
            MethodSetting(
                method = m.str("method") ?: return@mapNotNull null,
                unit = m.str("unit") ?: return@mapNotNull null,
                minAmount = m.long("min_amount"),
                maxAmount = m.long("max_amount"),
            )
        }.orEmpty()
        return MintInfo(
            name = o.str("name"),
            pubkey = o.str("pubkey"),
            version = o.str("version"),
            description = o.str("description"),
            descriptionLong = o.str("description_long"),
            motd = o.str("motd"),
            iconUrl = o.str("icon_url"),
            supportedNuts = supported,
            mintMethods = methods("4"),
            meltMethods = methods("5"),
            mintingDisabled = (nuts.get("4") as? JsonObject)?.bool("disabled") == true,
            meltingDisabled = (nuts.get("5") as? JsonObject)?.bool("disabled") == true,
        )
    }

    private fun parseMintQuote(o: JsonObject): MintQuote = MintQuote(
        quote = o.str("quote") ?: malformed("quote id"),
        request = o.str("request") ?: malformed("quote request"),
        amount = o.long("amount") ?: 0,
        unit = o.str("unit") ?: "sat",
        state = when (o.str("state")) {
            "PAID" -> MintQuoteState.PAID
            "ISSUED" -> MintQuoteState.ISSUED
            else -> if (o.bool("paid") == true) MintQuoteState.PAID else MintQuoteState.UNPAID
        },
        expiry = o.long("expiry"),
        amountPaid = o.long("amount_paid"),
        amountIssued = o.long("amount_issued"),
    )

    private fun parseMeltQuote(o: JsonObject): MeltQuote = MeltQuote(
        quote = o.str("quote") ?: malformed("melt quote id"),
        request = o.str("request") ?: "",
        amount = o.long("amount") ?: malformed("melt amount"),
        unit = o.str("unit") ?: "sat",
        feeReserve = o.long("fee_reserve") ?: 0,
        state = when (o.str("state")) {
            "PAID" -> MeltQuoteState.PAID
            "PENDING" -> MeltQuoteState.PENDING
            "UNPAID" -> MeltQuoteState.UNPAID
            else -> if (o.bool("paid") == true) MeltQuoteState.PAID else MeltQuoteState.UNPAID
        },
        expiry = o.long("expiry"),
        preimage = o.str("payment_preimage"),
        change = o.arrayOrNull("change")?.let(::parseSignatures).orEmpty(),
    )

    private fun parseSignatures(array: JsonArray): List<BlindSignature> = array.map { element ->
        val o = element as? JsonObject ?: malformed("signature")
        BlindSignature(
            amount = o.long("amount") ?: malformed("signature amount"),
            keysetId = o.str("id") ?: malformed("signature id"),
            c = o.str("C_") ?: malformed("signature C_"),
            dleq = (o.get("dleq") as? JsonObject)?.let { d ->
                val e = d.str("e")
                val s = d.str("s")
                if (e != null && s != null) DleqProof(e, s) else null
            },
        )
    }

    private fun List<BlindedMessage>.toJson(): JsonArray = JsonArray().also { array ->
        forEach { output ->
            array.add(JsonObject().apply {
                addProperty("amount", output.amount)
                addProperty("id", output.keysetId)
                addProperty("B_", output.b)
            })
        }
    }

    private fun Proof.toJson(): JsonObject = JsonObject().apply {
        addProperty("amount", amount)
        addProperty("id", keysetId)
        addProperty("secret", secret)
        addProperty("C", c)
        witness?.let { addProperty("witness", it) }
    }

    private fun path(segment: String): String = URLEncoder.encode(segment, "UTF-8")

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val DEFAULT_TIMEOUT_SECONDS = 45L
        /** Lightning payments can take a while; a timeout here is not a failure (see CashuWallet.melt). */
        private const val MELT_TIMEOUT_SECONDS = 120L
        private const val MAX_RESPONSE_BYTES = 4L * 1024 * 1024

        private fun malformed(what: String): Nothing = throw MintException.Malformed(what)

        private fun JsonObject.str(key: String): String? =
            get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

        private fun JsonObject.long(key: String): Long? =
            get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.let { runCatching { it.asBigDecimal.longValueExact() }.getOrNull() }

        private fun JsonObject.bool(key: String): Boolean? =
            get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

        private fun JsonObject.arrayOrNull(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray

        private fun JsonObject.array(key: String): JsonArray = arrayOrNull(key) ?: malformed(key)
    }
}
