package com.h9promax.ble.util

/**
 * Reusable utilities for converting raw BLE bytes.
 *
 * All conversions preserve the raw byte order exactly as received.
 * Data is never reinterpreted or converted into integers automatically.
 */
object HexUtils {

    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    /**
     * byte[] -> space separated HEX string (uppercase).
     */
    fun toHex(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder(bytes.size * 3)
        for (i in bytes.indices) {
            if (i > 0) sb.append(' ')
            val v = bytes[i].toInt() and 0xFF
            sb.append(HEX_CHARS[v ushr 4])
            sb.append(HEX_CHARS[v and 0x0F])
        }
        return sb.toString()
    }

    /**
     * byte[] -> comma separated decimal string.
     */
    fun toDecimal(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder()
        for (i in bytes.indices) {
            if (i > 0) sb.append(", ")
            sb.append(bytes[i].toInt() and 0xFF)
        }
        return sb.toString()
    }

    /**
     * byte[] -> ASCII representation where meaningful.
     * Non printable bytes are replaced with '.'.
     */
    fun toAscii(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(if (v in 32..126) v.toChar() else '.')
        }
        return sb.toString()
    }

    /**
     * HEX string -> byte[]. Accepts forms like "AA 01 02 03", "AA010203",
     * "aa 01 02 03". Throws [IllegalArgumentException] for odd-length input,
     * empty input after trimming, or non-hex characters.
     */
    fun fromHex(hex: String): ByteArray {
        val clean = hex.trim().replace(" ", "").replace("\t", "")
        if (clean.isEmpty()) {
            throw IllegalArgumentException("HEX string is empty")
        }
        if (clean.length % 2 != 0) {
            throw IllegalArgumentException("HEX string must have even length")
        }
        for (c in clean) {
            if (c !in '0'..'9' && c !in 'a'..'f' && c !in 'A'..'F') {
                throw IllegalArgumentException("Invalid HEX character: '$c'")
            }
        }
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            result[i] = ((hi shl 4) or lo).toByte()
        }
        return result
    }
}