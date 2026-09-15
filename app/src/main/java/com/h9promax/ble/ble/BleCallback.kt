package com.h9promax.ble.ble

import com.h9promax.ble.model.BleDevice
import com.h9promax.ble.model.BleService

/**
 * Lifecycle-safe callback interface used to deliver BLE events to the UI.
 * All methods have empty default implementations so callers only implement
 * what they need.
 */
interface BleCallback {

    // --- Bluetooth adapter state ---
    fun onBluetoothStateChanged(enabled: Boolean) {}

    // --- Scanning ---
    fun onScanStarted() {}
    fun onScanStopped(reason: ScanStopReason) {}
    fun onDeviceDiscovered(device: BleDevice) {}

    // --- Connection ---
    fun onConnectionStateChanged(address: String, state: BleConnectionState) {}

    // --- GATT ---
    fun onServicesDiscovered(services: List<BleService>) {}
    fun onCharacteristicRead(
        serviceUuid: String,
        characteristicUuid: String,
        bytes: ByteArray,
        status: Int,
        success: Boolean
    ) {}

    fun onCharacteristicWrite(
        serviceUuid: String,
        characteristicUuid: String,
        bytes: ByteArray,
        writeType: String,
        status: Int,
        success: Boolean
    ) {}

    fun onNotificationChanged(
        serviceUuid: String,
        characteristicUuid: String,
        enabled: Boolean,
        success: Boolean
    ) {}

    fun onCharacteristicChanged(
        serviceUuid: String,
        characteristicUuid: String,
        bytes: ByteArray
    ) {}

    fun onDescriptorRead(
        descriptorUuid: String,
        bytes: ByteArray,
        status: Int,
        success: Boolean
    ) {}

    fun onDescriptorWrite(
        descriptorUuid: String,
        bytes: ByteArray,
        status: Int,
        success: Boolean
    ) {}

    // --- Logging ---
    fun onLogChanged() {}
}