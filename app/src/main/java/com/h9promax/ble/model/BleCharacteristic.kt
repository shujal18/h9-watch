package com.h9promax.ble.model

import android.bluetooth.BluetoothGattCharacteristic

/**
 * Data holder for a discovered GATT characteristic.
 *
 * Properties are stored both as the raw bitmask and a decoded list of
 * human readable property names (READ, WRITE, WRITE WITHOUT RESPONSE,
 * NOTIFY, INDICATE).
 */
data class BleCharacteristic(
    val uuid: String,
    val propertyMask: Int,
    val properties: List<String>,
    val permissions: List<String>,
    val serviceUuid: String,
    val descriptors: List<BleDescriptor> = emptyList()
) {
    fun supportsRead() = propertyMask and BluetoothGattCharacteristic.PROPERTY_READ != 0
    fun supportsWrite() = propertyMask and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
    fun supportsWriteNoResponse() =
        propertyMask and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
    fun supportsNotify() = propertyMask and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
    fun supportsIndicate() = propertyMask and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0

    companion object {
        fun propertyName(prop: Int): String {
            return when (prop) {
                BluetoothGattCharacteristic.PROPERTY_BROADCAST -> "BROADCAST"
                BluetoothGattCharacteristic.PROPERTY_READ -> "READ"
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE -> "WRITE WITHOUT RESPONSE"
                BluetoothGattCharacteristic.PROPERTY_WRITE -> "WRITE"
                BluetoothGattCharacteristic.PROPERTY_NOTIFY -> "NOTIFY"
                BluetoothGattCharacteristic.PROPERTY_INDICATE -> "INDICATE"
                BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE -> "SIGNED WRITE"
                BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS -> "EXTENDED PROPERTIES"
                else -> "UNKNOWN(0x${prop.toString(16).uppercase()})"
            }
        }

        fun propertiesOf(mask: Int): List<String> {
            val result = mutableListOf<String>()
            var bit = 0x01
            while (bit <= BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS) {
                if (mask and bit != 0) result.add(propertyName(bit))
                bit = bit shl 1
            }
            return result
        }

        fun permissionName(perm: Int): String {
            return when (perm) {
                BluetoothGattCharacteristic.PERMISSION_READ -> "READ"
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED -> "READ ENCRYPTED"
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM -> "READ ENCRYPTED MITM"
                BluetoothGattCharacteristic.PERMISSION_WRITE -> "WRITE"
                BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED -> "WRITE ENCRYPTED"
                BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM -> "WRITE ENCRYPTED MITM"
                BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED -> "WRITE SIGNED"
                BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM -> "WRITE SIGNED MITM"
                else -> "UNKNOWN(0x${perm.toString(16).uppercase()})"
            }
        }

        fun permissionsOf(permMask: Int): List<String> {
            if (permMask == 0) return emptyList()
            val result = mutableListOf<String>()
            if (permMask and BluetoothGattCharacteristic.PERMISSION_READ != 0) result.add("READ")
            if (permMask and BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED != 0) result.add("READ ENCRYPTED")
            if (permMask and BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM != 0) result.add("READ ENCRYPTED MITM")
            if (permMask and BluetoothGattCharacteristic.PERMISSION_WRITE != 0) result.add("WRITE")
            if (permMask and BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED != 0) result.add("WRITE ENCRYPTED")
            if (permMask and BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM != 0) result.add("WRITE ENCRYPTED MITM")
            if (permMask and BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED != 0) result.add("WRITE SIGNED")
            if (permMask and BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM != 0) result.add("WRITE SIGNED MITM")
            return result
        }
    }
}