package com.h9promax.ble.model

import android.bluetooth.BluetoothGattDescriptor

/**
 * Data holder for a GATT descriptor.
 */
data class BleDescriptor(
    val uuid: String,
    val permissions: List<String>,
    val characteristicUuid: String
) {
    companion object {
        fun permissionNames(permMask: Int): List<String> {
            if (permMask == 0) return emptyList()
            val result = mutableListOf<String>()
            if (permMask and BluetoothGattDescriptor.PERMISSION_READ != 0) {
                result.add("READ")
            }
            if (permMask and BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED != 0) {
                result.add("READ ENCRYPTED")
            }
            if (permMask and BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM != 0) {
                result.add("READ ENCRYPTED MITM")
            }
            if (permMask and BluetoothGattDescriptor.PERMISSION_WRITE != 0) {
                result.add("WRITE")
            }
            if (permMask and BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED != 0) {
                result.add("WRITE ENCRYPTED")
            }
            if (permMask and BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM != 0) {
                result.add("WRITE ENCRYPTED MITM")
            }
            if (permMask and BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED != 0) {
                result.add("WRITE SIGNED")
            }
            if (permMask and BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED_MITM != 0) {
                result.add("WRITE SIGNED MITM")
            }
            return result
        }
    }
}