package com.h9promax.ble.util

/**
 * Pure decision logic for whether BLE scanning may start. Kept dependency-free
 * so it can be unit tested on the JVM.
 */
enum class ScanGate {
    OK,
    NO_BLUETOOTH_ADAPTER,
    BLUETOOTH_DISABLED,
    PERMISSIONS_MISSING,
    LOCATION_DISABLED
}

object ScanGates {

    /**
     * Determines the scan-gate problem given the current device state.
     *
     * @param bluetoothLowEnergySupported whether the device supports BLE
     * @param hasAdapter whether a BluetoothAdapter is available
     * @param bluetoothEnabled whether Bluetooth is turned on
     * @param hasPermissions whether runtime BLE permissions are granted
     * @param locationEnabled whether Location services are on (only relevant
     *                        for API < 31; pass true on API >= 31)
     */
    fun evaluate(
        bluetoothLowEnergySupported: Boolean,
        hasAdapter: Boolean,
        bluetoothEnabled: Boolean,
        hasPermissions: Boolean,
        locationEnabled: Boolean
    ): ScanGate {
        if (!bluetoothLowEnergySupported) return ScanGate.NO_BLUETOOTH_ADAPTER
        if (!hasAdapter) return ScanGate.NO_BLUETOOTH_ADAPTER
        if (!bluetoothEnabled) return ScanGate.BLUETOOTH_DISABLED
        if (!hasPermissions) return ScanGate.PERMISSIONS_MISSING
        if (!locationEnabled) return ScanGate.LOCATION_DISABLED
        return ScanGate.OK
    }
}