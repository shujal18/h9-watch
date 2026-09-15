package com.h9promax.ble

import com.h9promax.ble.model.BleCharacteristic
import com.h9promax.ble.model.BleDescriptor
import com.h9promax.ble.model.BleService
import com.h9promax.ble.util.SessionExporter
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionExporterTest {

    @Test
    fun `txt export contains device info, services and raw hex`() {
        val device = BleService(
            "0000-1234",
            "PRIMARY",
            listOf(
                BleCharacteristic(
                    uuid = "0000-ABCD",
                    propertyMask = 0x0E, // READ | WRITE | WRITE_NO_RESPONSE
                    properties = listOf("READ", "WRITE WITHOUT RESPONSE", "WRITE"),
                    permissions = listOf("READ"),
                    serviceUuid = "0000-1234",
                    descriptors = listOf(
                        BleDescriptor("00002902-0000-1000-8000-00805f9b34fb", listOf("READ", "WRITE"), "0000-ABCD")
                    )
                )
            )
        )
        val entries = listOf(
            com.h9promax.ble.model.BleLogEntry("15:42:10.112", "CONNECTED", "H9 Pro Max | AA:BB:CC:DD:EE:FF", null),
            com.h9promax.ble.model.BleLogEntry("15:42:11.210", "RX", "Service: 0000-1234 | Characteristic: 0000-ABCD", "AA 01 52 00 7F")
        )

        val txt = SessionExporter.buildTxt(
            deviceName = "H9 Pro Max",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            connectTime = "2026-09-15 15:30:21",
            disconnectTime = null,
            services = listOf(device),
            entries = entries
        )

        assertTrue(txt.contains("H9 Pro Max"))
        assertTrue(txt.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(txt.contains("0000-1234"))
        assertTrue(txt.contains("0000-ABCD"))
        assertTrue(txt.contains("AA 01 52 00 7F"))
        assertTrue(txt.contains("15:42:11.210"))
    }

    @Test
    fun `txt export handles empty session`() {
        val txt = SessionExporter.buildTxt(null, null, null, null, emptyList(), emptyList())
        assertTrue(txt.contains("(none recorded)"))
        assertTrue(txt.contains("(empty)"))
    }
}