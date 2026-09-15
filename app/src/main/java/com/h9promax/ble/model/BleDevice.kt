package com.h9promax.ble.model

/**
 * Represents a single BLE device discovered during a scan.
 * No assumptions about the H9 Pro Max are made here; all fields are
 * filled from the actual scan results.
 */
data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val isConnectable: Boolean?,
    val serviceUuids: List<String>,
    var isConnecting: Boolean = false,
    var isConnected: Boolean = false
) {
    fun displayName(): String = name?.takeIf { it.isNotBlank() } ?: "Unknown Device"
}