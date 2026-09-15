package com.h9promax.ble

import com.h9promax.ble.util.HexUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HexUtilsTest {

    @Test
    fun `space separated hex to bytes`() {
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0x01, 0xFF.toByte()),
            HexUtils.fromHex("AA 01 FF")
        )
    }

    @Test
    fun `compact hex to bytes`() {
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0x01, 0xFF.toByte()),
            HexUtils.fromHex("AA01FF")
        )
    }

    @Test
    fun `lowercase hex to bytes`() {
        assertArrayEquals(
            byteArrayOf(0xaa.toByte(), 0x01, 0x02, 0x03),
            HexUtils.fromHex("aa 01 02 03")
        )
    }

    @Test
    fun `bytes to space separated hex`() {
        assertEquals(
            "AA 01 FF",
            HexUtils.toHex(byteArrayOf(0xAA.toByte(), 0x01, 0xFF.toByte()))
        )
    }

    @Test
    fun `bytes to decimal`() {
        assertEquals(
            "1, 0, 79, 42, 124",
            HexUtils.toDecimal(byteArrayOf(0x01, 0x00, 0x4F, 0x2A, 0x7C))
        )
    }

    @Test
    fun `bytes to ascii replaces non printable`() {
        assertEquals(
            "..O*|",
            HexUtils.toAscii(byteArrayOf(0x01, 0x00, 0x4F, 0x2A, 0x7C))
        )
    }

    @Test
    fun `round trip preserves byte order`() {
        val original = byteArrayOf(0x00, 0x0F, 0x10, 0xFF.toByte(), 0x80.toByte())
        assertEquals(HexUtils.toHex(original), HexUtils.toHex(HexUtils.fromHex(HexUtils.toHex(original))))
    }

    @Test
    fun `does not silently swap byte order`() {
        val bytes = HexUtils.fromHex("12 34 AB")
        assertEquals("12 34 AB", HexUtils.toHex(bytes))
        assertEquals(0x12, bytes[0].toInt() and 0xFF)
        assertEquals(0x34, bytes[1].toInt() and 0xFF)
        assertEquals(0xAB, bytes[2].toInt() and 0xFF)
    }

    @Test
    fun `empty hex rejected`() {
        assertThrows(IllegalArgumentException::class.java) { HexUtils.fromHex("") }
        assertThrows(IllegalArgumentException::class.java) { HexUtils.fromHex("   ") }
    }

    @Test
    fun `odd-length hex rejected`() {
        assertThrows(IllegalArgumentException::class.java) { HexUtils.fromHex("AA 0") }
        assertThrows(IllegalArgumentException::class.java) { HexUtils.fromHex("ABC") }
    }

    @Test
    fun `non hex characters rejected`() {
        assertThrows(IllegalArgumentException::class.java) { HexUtils.fromHex("ZZ 01") }
        assertThrows(IllegalArgumentException::class.java) { HexUtils.fromHex("1G 2F") }
        assertThrows(IllegalArgumentException::class.java) { HexUtils.fromHex("AA 01 0-") }
    }
}