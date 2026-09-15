package com.h9promax.ble

import com.h9promax.ble.util.ScanGates
import com.h9promax.ble.util.ScanGate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Scenario tests for scan gating: permission denial, Bluetooth disabled,
 * Bluetooth unavailable, location requirements, and the happy path.
 */
class ScanGatesTest {

    private fun gate(
        bleSupported: Boolean = true,
        hasAdapter: Boolean = true,
        bluetoothEnabled: Boolean = true,
        hasPermissions: Boolean = true,
        locationEnabled: Boolean = true
    ) = ScanGates.evaluate(
        bluetoothLowEnergySupported = bleSupported,
        hasAdapter = hasAdapter,
        bluetoothEnabled = bluetoothEnabled,
        hasPermissions = hasPermissions,
        locationEnabled = locationEnabled
    )

    @Test
    fun `scan allowed when everything is ready`() {
        assertEquals(ScanGate.OK, gate())
    }

    @Test
    fun `scan blocked when bluetooth is off`() {
        assertEquals(ScanGate.BLUETOOTH_DISABLED, gate(bluetoothEnabled = false))
    }

    @Test
    fun `scan blocked when bluetooth unavailable`() {
        assertEquals(ScanGate.NO_BLUETOOTH_ADAPTER, gate(hasAdapter = false))
        assertEquals(ScanGate.NO_BLUETOOTH_ADAPTER, gate(bleSupported = false))
    }

    @Test
    fun `scan blocked on permission denial`() {
        assertEquals(ScanGate.PERMISSIONS_MISSING, gate(hasPermissions = false))
    }

    @Test
    fun `scan blocked when location disabled on older android`() {
        assertEquals(ScanGate.LOCATION_DISABLED, gate(locationEnabled = false))
    }
}