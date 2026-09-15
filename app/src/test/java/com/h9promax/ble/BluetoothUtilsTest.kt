package com.h9promax.ble

import com.h9promax.ble.model.BleDevice
import com.h9promax.ble.util.BluetoothUtils
import com.h9promax.ble.util.BluetoothUtils.isWatchLike
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothUtilsTest {

    // -- isWatchLike ranking helper -- //

    @Test
    fun `h9 names are watch-like`() {
        assertTrue("H9 Pro Max".isWatchLike())
        assertTrue("H9 Pro".isWatchLike())
        assertTrue("H9".isWatchLike())
    }

    @Test
    fun `generic watch names are watch-like`() {
        assertTrue("Smart Watch".isWatchLike())
        assertTrue("Watch S1".isWatchLike())
        assertTrue("PK Smart Band".isWatchLike())
        assertTrue("JDY watch".isWatchLike())
        assertTrue("Mi Band".isWatchLike())
    }

    @Test
    fun `non watch names are not watch-like`() {
        assertFalse("iPhone 12".isWatchLike())
        assertFalse("BZ3209".isWatchLike())
        assertFalse("LAXASFIT".isWatchLike())
        assertFalse("0599".isWatchLike())
        assertFalse("".isWatchLike())
        assertFalse(null.isWatchLike())
    }

    // -- Deduplication (duplicate scan results) -- //

    @Test
    fun `duplicate scan results collapse to one device per address`() {
        val d1 = BleDevice("H9 Pro Max", "AA:BB:CC:DD:EE:FF", -50, true, listOf("0000-1234"))
        val d2 = BleDevice("H9 Pro Max", "AA:BB:CC:DD:EE:FF", -48, true, listOf("0000-1234"))
        val d3 = BleDevice("Other", "11:22:33:44:55:66", -70, null, emptyList())

        val result = BluetoothUtils.rankedAndDeduped(listOf(d1, d2, d3))

        assertEquals(2, result.size)
        // Duplicate updated with latest RSSI but still one entry.
        assertEquals(listOf("AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66"), result.map { it.address })
        val h9 = result.first { it.address == "AA:BB:CC:DD:EE:FF" }
        assertEquals(-48, h9.rssi)
    }

    @Test
    fun `watch-like devices sort before generic devices`() {
        val generic = BleDevice("iPhone 14", "AA:00:00:00:00:01", -60, true, emptyList())
        val watch = BleDevice("H9 Pro Max", "AA:00:00:00:00:02", -55, true, emptyList())

        val result = BluetoothUtils.rankedAndDeduped(listOf(generic, watch))

        assertEquals("H9 Pro Max", result[0].displayName())
    }

    @Test
    fun `within same group stronger rssi sorts first`() {
        val weak = BleDevice("H9", "AA:00:00:00:00:01", -80, true, emptyList())
        val strong = BleDevice("H9", "AA:00:00:00:00:02", -40, true, emptyList())
        val result = BluetoothUtils.rankedAndDeduped(listOf(weak, strong))
        assertEquals("AA:00:00:00:00:02", result[0].address)
    }
}