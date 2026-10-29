package com.bitchat.android.wallet.cashu

import java.io.ByteArrayOutputStream
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer

/**
 * The subset of CBOR (RFC 8949) that V4 tokens use: unsigned ints, byte/text strings, arrays,
 * maps, tags, and simple values. Decoding is bounded (depth, item count, string length) so
 * a hostile token pasted into chat cannot exhaust memory.
 */
internal sealed interface Cbor {
    data class UInt(val value: Long) : Cbor
    data class NegInt(val value: Long) : Cbor
    class Bytes(val value: ByteArray) : Cbor
    data class Text(val value: String) : Cbor
    data class Array(val items: List<Cbor>) : Cbor
    data class Map(val entries: List<Pair<Cbor, Cbor>>) : Cbor {
        operator fun get(key: String): Cbor? = entries.firstOrNull { (it.first as? Text)?.value == key }?.second
    }
    data class Simple(val value: Int) : Cbor
    data object Float : Cbor

    companion object {
        fun encode(value: Cbor): ByteArray = ByteArrayOutputStream().also { write(it, value) }.toByteArray()

        fun decode(bytes: ByteArray): Cbor = Reader(bytes).readComplete()

        fun map(vararg entries: Pair<String, Cbor?>): Map =
            Map(entries.mapNotNull { (k, v) -> v?.let { Text(k) to it } })

        private fun write(out: ByteArrayOutputStream, value: Cbor) {
            when (value) {
                is UInt -> head(out, 0, value.value)
                is NegInt -> head(out, 1, value.value)
                is Bytes -> { head(out, 2, value.value.size.toLong()); out.write(value.value) }
                is Text -> value.value.toByteArray(StandardCharsets.UTF_8).let { head(out, 3, it.size.toLong()); out.write(it) }
                is Array -> { head(out, 4, value.items.size.toLong()); value.items.forEach { write(out, it) } }
                is Map -> {
                    head(out, 5, value.entries.size.toLong())
                    value.entries.forEach { (k, v) -> write(out, k); write(out, v) }
                }
                is Simple -> head(out, 7, value.value.toLong())
                Float -> throw IllegalArgumentException("float encoding unsupported")
            }
        }

        private fun head(out: ByteArrayOutputStream, major: Int, argument: Long) {
            require(argument >= 0) { "negative CBOR argument" }
            val m = major shl 5
            when {
                argument < 24 -> out.write(m or argument.toInt())
                argument <= 0xff -> { out.write(m or 24); out.write(argument.toInt()) }
                argument <= 0xffff -> { out.write(m or 25); out.write(ByteBuffer.allocate(2).putShort(argument.toShort()).array()) }
                argument <= 0xffffffffL -> { out.write(m or 26); out.write(ByteBuffer.allocate(4).putInt(argument.toInt()).array()) }
                else -> { out.write(m or 27); out.write(ByteBuffer.allocate(8).putLong(argument).array()) }
            }
        }
    }

    private class Reader(private val bytes: ByteArray) {
        private var index = 0
        private var budget = MAX_ITEMS

        fun readComplete(): Cbor {
            val value = read(0)
            if (index != bytes.size) throw CborException("trailing bytes")
            return value
        }

        private fun read(depth: Int): Cbor {
            if (depth > MAX_DEPTH) throw CborException("too deep")
            if (--budget < 0) throw CborException("too many items")
            val head = next()
            val major = head ushr 5
            val info = head and 0x1f
            if (info == 31) return readIndefinite(major, depth)
            val argument = argument(info)
            return when (major) {
                0 -> UInt(argument)
                1 -> NegInt(argument)
                2 -> Bytes(take(argument))
                3 -> Text(utf8(take(argument)))
                4 -> {
                    checkCount(argument)
                    Array(List(argument.toInt()) { read(depth + 1) })
                }
                5 -> {
                    checkCount(argument * 2)
                    Map(List(argument.toInt()) { read(depth + 1) to read(depth + 1) })
                }
                6 -> read(depth + 1) // tags carry no meaning for tokens
                7 -> when (info) {
                    25, 26, 27 -> Float
                    else -> Simple(argument.toInt())
                }
                else -> throw CborException("bad major type")
            }
        }

        private fun readIndefinite(major: Int, depth: Int): Cbor {
            fun atBreak(): Boolean = (index < bytes.size && (bytes[index].toInt() and 0xff) == 0xff).also { if (it) index++ }
            return when (major) {
                2, 3 -> {
                    val out = ByteArrayOutputStream()
                    while (!atBreak()) {
                        val chunk = read(depth + 1)
                        when {
                            major == 2 && chunk is Bytes -> out.write(chunk.value)
                            major == 3 && chunk is Text -> out.write(chunk.value.toByteArray(StandardCharsets.UTF_8))
                            else -> throw CborException("bad chunk")
                        }
                        if (out.size() > MAX_STRING) throw CborException("string too long")
                    }
                    if (major == 2) Bytes(out.toByteArray()) else Text(utf8(out.toByteArray()))
                }
                4 -> Array(buildList { while (!atBreak()) add(read(depth + 1)) })
                5 -> Map(buildList { while (!atBreak()) add(read(depth + 1) to read(depth + 1)) })
                else -> throw CborException("bad indefinite item")
            }
        }

        private fun argument(info: Int): Long = when (info) {
            in 0..23 -> info.toLong()
            24 -> uint(1)
            25 -> uint(2)
            26 -> uint(4)
            27 -> uint(8).also { if (it < 0) throw CborException("integer too large") }
            else -> throw CborException("reserved info")
        }

        private fun uint(width: Int): Long {
            if (bytes.size - index < width) throw CborException("truncated")
            var value = 0L
            repeat(width) { value = (value shl 8) or (bytes[index++].toLong() and 0xff) }
            return value
        }

        private fun take(count: Long): ByteArray {
            if (count > MAX_STRING || count > bytes.size - index) throw CborException("truncated")
            return bytes.copyOfRange(index, index + count.toInt()).also { index += count.toInt() }
        }

        private fun next(): Int {
            if (index >= bytes.size) throw CborException("truncated")
            return bytes[index++].toInt() and 0xff
        }

        private fun checkCount(count: Long) {
            if (count < 0 || count > budget || count > bytes.size - index) throw CborException("too many items")
        }

        private fun utf8(raw: ByteArray): String = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(raw))
                .toString()
        } catch (_: CharacterCodingException) {
            throw CborException("invalid utf-8")
        }

        companion object {
            const val MAX_DEPTH = 16
            const val MAX_ITEMS = 50_000
            const val MAX_STRING = 64 * 1024L
        }
    }
}

internal class CborException(message: String) : Exception(message)
