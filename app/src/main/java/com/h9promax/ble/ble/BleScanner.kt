package com.h9promax.ble.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.h9promax.ble.model.BleDevice
import com.h9promax.ble.util.BluetoothUtils

/**
 * Thin wrapper around BLE scanning with a timeout, deduplication and
 * lifecycle-safety. Uses the modern BluetoothLeScanner when available and
 * falls back to startLeScan on devices where it is unreliable.
 *
 * Scanning requires runtime permissions (BLUETOOTH_SCAN on API 31+, location
 * on older versions); the UI layer gates entry points so these calls only
 * happen after permission checks.
 */
@SuppressLint("MissingPermission")
class BleScanner(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val callback: BleCallback,
    private val logger: BlePacketLogger
) {

    private val handler = Handler(Looper.getMainLooper())
    private val devices = LinkedHashMap<String, BleDevice>()
    private var isScanning = false
    private var leScanFallbackActive = false
    private var legacyCallback: BluetoothAdapter.LeScanCallback? = null
    private var stopReason: ScanStopReason = ScanStopReason.USER

    private val timeoutRunnable = Runnable {
        if (isScanning) {
            Log.d(TAG, "SCAN_TIMEOUT auto stopping scan after ${SCAN_TIMEOUT_MS}ms")
            stopScanInternal(ScanStopReason.TIMEOUT)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device ?: return
            val connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result.isConnectable
            } else {
                null
            }
            val uuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
            val rssi = result.rssi
            val name = result.scanRecord?.deviceName ?: device.name

            addOrUpdate(
                BleDevice(
                    name = name,
                    address = device.address,
                    rssi = rssi,
                    isConnectable = connectable,
                    serviceUuids = uuids
                )
            )
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "SCAN_FAILED errorCode=$errorCode")
            logger.log("SCAN_FAILED", "errorCode=$errorCode")
            stopScanInternal(ScanStopReason.ERROR)
        }
    }

    fun isActive(): Boolean = isScanning

    fun startScan() {
        if (isScanning) {
            Log.d(TAG, "SCAN_START ignored, already scanning")
            return
        }
        devices.clear()
        isScanning = true
        stopReason = ScanStopReason.USER
        Log.d(TAG, "SCAN_STARTED")
        logger.log("SCAN_STARTED", "Starting BLE scan (timeout ${SCAN_TIMEOUT_MS / 1000}s)")

        val scanner = getLeScanner()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        if (scanner != null && scanApiAvailable()) {
            try {
                scanner.startScan(null, settings, scanCallback)
                Log.d(TAG, "SCAN_STARTED via BluetoothLeScanner")
            } catch (e: Exception) {
                Log.e(TAG, "SCAN_START_ERROR via leScanner: ${e.message}", e)
                logger.log("SCAN_ERROR", "BluetoothLeScanner error: ${e.message}")
                startLegacyScan()
            }
        } else {
            startLegacyScan()
        }

        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, SCAN_TIMEOUT_MS)
    }

    fun stopScan(reason: ScanStopReason, silentLog: Boolean = false) {
        if (!isScanning) return
        stopScanInternal(reason, silentLog)
    }

    private fun stopScanInternal(reason: ScanStopReason, silentLog: Boolean = false) {
        isScanning = false
        stopReason = reason
        handler.removeCallbacks(timeoutRunnable)

        val scanner = getLeScanner()
        if (!leScanFallbackActive && scanner != null) {
            try {
                scanner.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.e(TAG, "stopScan error: ${e.message}")
            }
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    adapter.bluetoothLeScanner?.stopScan(scanCallback)
                }
            } catch (_: Exception) {
            }
            stopLegacyScan()
        }

        leScanFallbackActive = false
        if (!silentLog) {
            Log.d(TAG, "SCAN_STOPPED reason=${reason.label}")
            logger.log("SCAN_STOPPED", "reason=${reason.label}")
        }
        callback.onScanStopped(reason)
    }

    fun devicesSnapshot(): List<BleDevice> =
        BluetoothUtils.rankedAndDeduped(devices.values.toList())

    private fun addOrUpdate(device: BleDevice) {
        val previous = devices[device.address]
        if (previous != null) {
            // Update RSSI/name and keep connection flags.
            val updated = previous.copy(
                name = device.name ?: previous.name,
                rssi = device.rssi,
                isConnectable = device.isConnectable ?: previous.isConnectable,
                serviceUuids = device.serviceUuids.ifEmpty { previous.serviceUuids }
            )
            devices[device.address] = updated
            Log.d(TAG, "DEVICE_UPDATED ${updated.displayName()} ${updated.address} rssi=${updated.rssi}")
        } else {
            devices[device.address] = device
            Log.d(
                TAG,
                "DEVICE_DISCOVERED ${device.displayName()} ${device.address} " +
                    "rssi=${device.rssi} connectable=${device.isConnectable} uuids=${device.serviceUuids}"
            )
            logger.log(
                "DEVICE_FOUND",
                "${device.displayName()} | ${device.address} | RSSI ${device.rssi} dBm | " +
                    "connectable=${device.isConnectable ?: "n/a"}" +
                    if (device.serviceUuids.isNotEmpty()) " | UUIDs: ${device.serviceUuids.joinToString(" ")}" else ""
            )
        }
        callback.onDeviceDiscovered(devices[device.address]!!)
    }

    fun markConnected(address: String) {
        devices[address]?.let {
            devices[address] = it.copy(isConnected = true)
        }
    }

    fun markConnecting(address: String) {
        devices[address]?.let {
            devices[address] = it.copy(isConnecting = true)
        }
    }

    fun clearConnectionFlags() {
        for ((key, value) in devices) {
            devices[key] = value.copy(isConnecting = false, isConnected = false)
        }
    }

    private fun getLeScanner() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) adapter.bluetoothLeScanner else null

    private fun scanApiAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    private fun startLegacyScan() {
        Log.d(TAG, "SCAN_STARTED via legacy startLeScan fallback")
        val callback = BluetoothAdapter.LeScanCallback { device, rssi, _ ->
            val name = device?.name
            if (device != null) {
                addOrUpdate(
                    BleDevice(
                        name = name,
                        address = device.address,
                        rssi = rssi,
                        isConnectable = null,
                        serviceUuids = emptyList()
                    )
                )
            }
        }
        legacyCallback = callback
        leScanFallbackActive = true
        adapter.startLeScan(callback)
    }

    private fun stopLegacyScan() {
        legacyCallback?.let {
            try {
                adapter.stopLeScan(it)
            } catch (_: Exception) {
            }
        }
        legacyCallback = null
    }

    companion object {
        private const val TAG = "H9BLE"
        const val SCAN_TIMEOUT_MS = 20_000L
    }
}