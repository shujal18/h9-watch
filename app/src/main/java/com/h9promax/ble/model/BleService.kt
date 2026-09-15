package com.h9promax.ble.model

import android.bluetooth.BluetoothGattService

/**
 * Data holder for a discovered GATT service.
 */
data class BleService(
    val uuid: String,
    val type: String,
    val characteristics: List<BleCharacteristic> = emptyList()
) {
    companion object {
        fun typeName(type: Int): String = when (type) {
            BluetoothGattService.SERVICE_TYPE_PRIMARY -> "PRIMARY"
            BluetoothGattService.SERVICE_TYPE_SECONDARY -> "SECONDARY"
            else -> "UNKNOWN($type)"
        }
    }
}