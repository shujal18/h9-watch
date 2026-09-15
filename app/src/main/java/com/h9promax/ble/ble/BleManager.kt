package com.h9promax.ble.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.util.Log
import com.h9promax.ble.model.BleService
import com.h9promax.ble.util.BluetoothUtils
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Application-wide coordinator for BLE operations.
 *
 * Guarantees:
 * - single scan at a time
 * - single GATT connection at a time
 * - no duplicate callback registration
 * - a shared session log for the whole app
 *
 * Call [init] once with the application context before use.
 */
object BleManager : BleCallback {

    private var appContext: Context? = null
    private val listeners = CopyOnWriteArraySet<BleCallback>()

    @Volatile
    private var scanner: BleScanner? = null
    private var gattConnection: BleGattConnection? = null
    private val logger = BlePacketLogger()

    private var bleEnabled = false
    private var adapter: BluetoothAdapter? = null

    // Session metadata gathered while connecting, used for the export.
    @Volatile
    private var sessionDeviceName: String? = null
    @Volatile
    private var sessionDeviceAddress: String? = null
    @Volatile
    private var sessionConnectTime: String? = null
    @Volatile
    private var sessionDisconnectTime: String? = null
    @Volatile
    private var sessionServices: List<BleService> = emptyList()

    // --- Initialisation ---------------------------------------------------

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        adapter = BluetoothUtils.adapter(app)
        bleEnabled = adapter?.isEnabled == true
        logger.attach(object : BleCallback {
            override fun onLogChanged() {
                for (l in listeners) {
                    try {
                        l.onLogChanged()
                    } catch (_: Exception) {
                    }
                }
            }
        })
        Log.d(TAG, "INIT adapter=${adapter != null} enabled=$bleEnabled")
    }

    fun register(listener: BleCallback) {
        listeners.add(listener)
    }

    fun unregister(listener: BleCallback) {
        listeners.remove(listener)
    }

    // --- Bluetooth state --------------------------------------------------

    fun isBluetoothEnabled(): Boolean {
        val ctx = appContext ?: return false
        return BluetoothUtils.adapter(ctx)?.isEnabled == true
    }

    fun bluetoothAdapter(): BluetoothAdapter? {
        adapter = BluetoothUtils.adapter(appContext ?: return adapter)
        return adapter
    }

    fun notifyBluetoothState(enabled: Boolean) {
        bleEnabled = enabled
        for (l in listeners) {
            try {
                l.onBluetoothStateChanged(enabled)
            } catch (_: Exception) {
            }
        }
    }

    // --- Scanning ---------------------------------------------------------

    fun startScan() {
        val context = appContext ?: run {
            Log.e(TAG, "startScan called before init()")
            return
        }
        val btAdapter = bluetoothAdapter() ?: run {
            Log.e(TAG, "Bluetooth adapter unavailable")
            logger.log("SCAN_ERROR", "Bluetooth adapter unavailable")
            return
        }
        val s = scanner ?: BleScanner(context, btAdapter, this, logger).also { scanner = it }
        s.startScan()
    }

    fun stopScan(reason: ScanStopReason = ScanStopReason.USER) {
        scanner?.stopScan(reason)
    }

    fun isScanning(): Boolean = scanner?.isActive() == true

    fun devicesList(): List<com.h9promax.ble.model.BleDevice> = scanner?.devicesSnapshot() ?: emptyList()

    // --- Connection -------------------------------------------------------

    fun connect(address: String) {
        // Ensure any existing connection is closed before opening a new one.
        if (gattConnection != null) {
            Log.w(TAG, "connect() while a connection exists; closing previous first")
            gattConnection?.close()
            gattConnection = null
        }
        val ctx = appContext ?: run {
            Log.e(TAG, "connect called before init")
            return
        }
        val btAdapter = bluetoothAdapter() ?: run {
            logger.log("CONNECT_FAILED", "Bluetooth adapter unavailable")
            return
        }
        val device = btAdapter.getRemoteDevice(address) ?: run {
            logger.log("CONNECT_FAILED", "Unknown device address $address")
            return
        }
        scanner?.markConnecting(address)
        val conn = BleGattConnection(ctx, device, this, logger)
        gattConnection = conn
        conn.connect()
    }

    fun disconnect() {
        gattConnection?.let {
            it.disconnect()
            gattConnection = null
        }
    }

    fun isConnected(): Boolean = gattConnection?.getState() == BleConnectionState.CONNECTED

    fun currentConnectionState(): BleConnectionState =
        gattConnection?.getState() ?: BleConnectionState.DISCONNECTED

    fun connectedAddress(): String? {
        val conn = gattConnection ?: return null
        if (conn.getState() == BleConnectionState.CONNECTED ||
            conn.getState() == BleConnectionState.CONNECTING
        ) {
            return conn.deviceAddress()
        }
        return null
    }

    fun findService(uuid: String) = gattConnection?.findService(uuid)

    fun findCharacteristic(serviceUuid: String, characteristicUuid: String): android.bluetooth.BluetoothGattCharacteristic? {
        val service = findService(serviceUuid) ?: return null
        return service.characteristics?.firstOrNull { it.uuid.toString() == characteristicUuid }
    }

    fun readCharacteristic(c: BluetoothGattCharacteristic): Boolean =
        gattConnection?.readCharacteristic(c) == true

    fun writeCharacteristic(
        c: BluetoothGattCharacteristic,
        value: ByteArray,
        noResponse: Boolean
    ): Boolean = gattConnection?.writeCharacteristic(c, value, noResponse) == true

    fun setNotifications(c: BluetoothGattCharacteristic, enable: Boolean): Boolean =
        gattConnection?.setNotifications(c, enable) == true

    fun readDescriptor(d: BluetoothGattDescriptor): Boolean =
        gattConnection?.readDescriptor(d) == true

    fun writeDescriptor(d: BluetoothGattDescriptor, value: ByteArray): Boolean =
        gattConnection?.writeDescriptor(d, value) == true

    fun closeCurrent() {
        gattConnection?.close()
        gattConnection = null
    }

    // --- Logging ----------------------------------------------------------

    fun logEntries(): List<com.h9promax.ble.model.BleLogEntry> = logger.entries()

    fun clearLog() {
        logger.clear()
    }

    fun logManual(event: String, detail: String) {
        logger.log(event, detail)
    }

    // --- Session metadata for export --------------------------------------

    fun sessionDeviceName(): String? = sessionDeviceName
    fun sessionDeviceAddress(): String? = sessionDeviceAddress
    fun sessionConnectTime(): String? = sessionConnectTime
    fun sessionDisconnectTime(): String? = sessionDisconnectTime
    fun sessionServices(): List<BleService> = sessionServices

    fun resetSessionMetadata() {
        sessionDeviceName = null
        sessionDeviceAddress = null
        sessionConnectTime = null
        sessionDisconnectTime = null
        sessionServices = emptyList()
    }

    // --- BleCallback forwarding -------------------------------------------

    override fun onScanStarted() {
        for (l in listeners) l.onScanStarted()
    }

    override fun onScanStopped(reason: ScanStopReason) {
        for (l in listeners) l.onScanStopped(reason)
    }

    override fun onDeviceDiscovered(device: com.h9promax.ble.model.BleDevice) {
        for (l in listeners) l.onDeviceDiscovered(device)
    }

    override fun onConnectionStateChanged(address: String, state: BleConnectionState) {
        if (state == BleConnectionState.CONNECTED || state == BleConnectionState.DISCONNECTED) {
            if (state == BleConnectionState.CONNECTED) {
                scanner?.markConnected(address)
                sessionDeviceAddress = address
                sessionConnectTime = com.h9promax.ble.util.TimeUtils.now()
                gattConnection?.let { sessionDeviceName = it.deviceName() }
            }
            if (state == BleConnectionState.DISCONNECTED) {
                scanner?.clearConnectionFlags()
                sessionDisconnectTime = com.h9promax.ble.util.TimeUtils.now()
            }
        }
        for (l in listeners) l.onConnectionStateChanged(address, state)
    }

    override fun onServicesDiscovered(services: List<BleService>) {
        sessionServices = services
        for (l in listeners) l.onServicesDiscovered(services)
    }

    override fun onCharacteristicRead(
        serviceUuid: String,
        characteristicUuid: String,
        bytes: ByteArray,
        status: Int,
        success: Boolean
    ) {
        for (l in listeners) {
            l.onCharacteristicRead(serviceUuid, characteristicUuid, bytes, status, success)
        }
    }

    override fun onCharacteristicWrite(
        serviceUuid: String,
        characteristicUuid: String,
        bytes: ByteArray,
        writeType: String,
        status: Int,
        success: Boolean
    ) {
        for (l in listeners) {
            l.onCharacteristicWrite(serviceUuid, characteristicUuid, bytes, writeType, status, success)
        }
    }

    override fun onNotificationChanged(
        serviceUuid: String,
        characteristicUuid: String,
        enabled: Boolean,
        success: Boolean
    ) {
        for (l in listeners) {
            l.onNotificationChanged(serviceUuid, characteristicUuid, enabled, success)
        }
    }

    override fun onCharacteristicChanged(
        serviceUuid: String,
        characteristicUuid: String,
        bytes: ByteArray
    ) {
        for (l in listeners) {
            l.onCharacteristicChanged(serviceUuid, characteristicUuid, bytes)
        }
    }

    override fun onDescriptorRead(descriptorUuid: String, bytes: ByteArray, status: Int, success: Boolean) {
        for (l in listeners) {
            l.onDescriptorRead(descriptorUuid, bytes, status, success)
        }
    }

    override fun onDescriptorWrite(descriptorUuid: String, bytes: ByteArray, status: Int, success: Boolean) {
        for (l in listeners) {
            l.onDescriptorWrite(descriptorUuid, bytes, status, success)
        }
    }

    override fun onLogChanged() {
        for (l in listeners) l.onLogChanged()
    }

    fun hasBluetoothFeature(context: Context): Boolean {
        val pm = context.packageManager
        return pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE)
    }

    private const val TAG = "H9BLE"
}