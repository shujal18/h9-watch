package com.h9promax.ble.model

/**
 * A structured entry in the BLE session log.
 */
data class BleLogEntry(
    val timestamp: String,
    val event: String,
    val detail: String,
    val rawHex: String? = null
)