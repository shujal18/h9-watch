package com.h9promax.ble.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.h9promax.ble.model.BleDevice

/**
 * Bluetooth helper utilities.
 */
object BluetoothUtils {

    private val watchKeywords = listOf(
        "h9",
        "h9 pro",
        "h9 pro max",
        "smart watch",
        "watch",
        "band"
    )

    /**
     * True when a device name suggests a smartwatch (used only to order the
     * device list; nothing is filtered out).
     */
    fun String?.isWatchLike(): Boolean {
        val name = this?.lowercase() ?: return false
        return watchKeywords.any { name.contains(it) }
    }

    /**
     * Deduplicates by MAC address (updates RSSI/name of the existing entry)
     * and sorts watch-like names first, then by RSSI (strongest first).
     */
    fun rankedAndDeduped(devices: List<BleDevice>): List<BleDevice> {
        val map = LinkedHashMap<String, BleDevice>()
        for (d in devices) {
            val prev = map[d.address]
            map[d.address] = if (prev == null) {
                d
            } else {
                prev.copy(
                    name = d.name ?: prev.name,
                    rssi = d.rssi,
                    isConnectable = d.isConnectable ?: prev.isConnectable,
                    serviceUuids = d.serviceUuids.ifEmpty { prev.serviceUuids }
                )
            }
        }
        return map.values.sortedWith(
            compareByDescending<BleDevice> { it.displayName().isWatchLike() }
                .thenByDescending { it.rssi }
        )
    }

    fun bluetoothManager(context: Context): BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    fun adapter(context: Context): BluetoothAdapter? =
        bluetoothManager(context)?.adapter

    /**
     * Human readable bluetooth state description.
     */
    fun stateName(state: Int): String = when (state) {
        BluetoothAdapter.STATE_OFF -> "OFF"
        BluetoothAdapter.STATE_TURNING_ON -> "TURNING ON"
        BluetoothAdapter.STATE_ON -> "ON"
        BluetoothAdapter.STATE_TURNING_OFF -> "TURNING OFF"
        else -> "UNKNOWN($state)"
    }

    fun profileStateName(state: Int): String = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN($state)"
    }
}