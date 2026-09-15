package com.h9promax.ble

import android.bluetooth.BluetoothGattCharacteristic
import com.h9promax.ble.model.BleCharacteristic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the characteristic property decoding that drives the UI
 * (READ / WRITE / WRITE WITHOUT RESPONSE / NOTIFY / INDICATE).
 */
class BleCharacteristicTest {

    @Test
    fun `properties decoded from bitmask`() {
        val mask = BluetoothGattCharacteristic.PROPERTY_READ or
            BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_NOTIFY
        val props = BleCharacteristic.propertiesOf(mask)
        assertTrue(props.contains("READ"))
        assertTrue(props.contains("WRITE"))
        assertTrue(props.contains("NOTIFY"))
        assertFalse(props.contains("WRITE WITHOUT RESPONSE"))
    }

    @Test
    fun `write without response and indicate decoded`() {
        val mask = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
            BluetoothGattCharacteristic.PROPERTY_INDICATE
        val props = BleCharacteristic.propertiesOf(mask)
        assertTrue(props.contains("WRITE WITHOUT RESPONSE"))
        assertTrue(props.contains("INDICATE"))
    }

    @Test
    fun `capability helpers`() {
        val readWrite = BleCharacteristic(
            uuid = "u",
            propertyMask = BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_WRITE,
            properties = emptyList(),
            permissions = emptyList(),
            serviceUuid = "s"
        )
        assertTrue(readWrite.supportsRead())
        assertTrue(readWrite.supportsWrite())
        assertFalse(readWrite.supportsWriteNoResponse())
        assertFalse(readWrite.supportsNotify())
        assertFalse(readWrite.supportsIndicate())
    }

    @Test
    fun `notify characteristic has notify capability`() {
        val notifyChar = BleCharacteristic(
            uuid = "u",
            propertyMask = BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            properties = listOf("NOTIFY"),
            permissions = emptyList(),
            serviceUuid = "s"
        )
        assertTrue(notifyChar.supportsNotify())
        assertFalse(notifyChar.supportsRead())
        assertFalse(notifyChar.supportsWrite())
    }

    @Test
    fun `permissions never guessed from uuid`() {
        // A characteristic that LOOKS like a standard health characteristic
        // must still not be assumed readable/writable without a mask.
        val fakeHealth = BleCharacteristic(
            uuid = "00002a37-0000-1000-8000-00805f9b34fb", // Heart Rate Measurement shape
            propertyMask = 0x00,
            properties = emptyList(),
            permissions = emptyList(),
            serviceUuid = "0000180d-0000-1000-8000-00805f9b34fb"
        )
        assertFalse(fakeHealth.supportsRead())
        assertFalse(fakeHealth.supportsWrite())
        assertFalse(fakeHealth.supportsNotify())
        assertFalse(fakeHealth.supportsIndicate())
        assertEquals(0, fakeHealth.permissions.size)
    }
}