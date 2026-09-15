package com.h9promax.ble.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.h9promax.ble.model.BleCharacteristic as ModelBleCharacteristic
import com.h9promax.ble.model.BleDescriptor as ModelBleDescriptor
import com.h9promax.ble.model.BleService
import com.h9promax.ble.util.HexUtils

/**
 * Manages a single GATT connection lifecycle safely: connect, service
 * discovery, read/write, notifications, and teardown.
 *
 * Only one connection should exist at a time (enforced by [BleManager]).
 *
 * All BLE operations here require the runtime permissions (BLUETOOTH_CONNECT
 * on API 31+, location earlier); the UI layer gates every entry point so
 * these calls only happen after permission checks.
 */
@SuppressLint("MissingPermission")
class BleGattConnection(
    private val context: Context,
    private val device: BluetoothDevice,
    private val gattCallback: BleCallback,
    private val logger: BlePacketLogger
) {

    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var state: BleConnectionState = BleConnectionState.DISCONNECTED
    private var isReading = false
    private var isWriting = false
    private var servicesCached = false

    private val connectionTimeoutRunnable = Runnable {
        if (state == BleConnectionState.CONNECTING) {
            Log.e(TAG, "CONNECTION_TIMEOUT ${device.name} ${device.address}")
            logger.log(
                "CONNECTION_FAILED",
                "${device.name} | ${device.address} | reason=TIMEOUT " +
                    "(${CONNECT_TIMEOUT_MS / 1000}s)"
            )
            setState(BleConnectionState.CONNECTION_FAILED)
            closeInternal()
        }
    }

    private val bluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(
                TAG,
                "onConnectionStateChange status=$status newState=$newState " +
                    "${BluetoothUtilsRef.profileState(newState)}"
            )
            logger.log(
                "CONNECT_STATE",
                "${device.name} | ${device.address} | newState=" +
                    "${BluetoothUtilsRef.profileState(newState)} | status=$status"
            )

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    handler.removeCallbacks(connectionTimeoutRunnable)
                    setState(BleConnectionState.CONNECTED)
                    logger.log("CONNECTED", "${device.name} | ${device.address}")
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.w(TAG, "Connected but status=$status")
                    }
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    handler.removeCallbacks(connectionTimeoutRunnable)
                    val previousState = state
                    when (previousState) {
                        BleConnectionState.CONNECTING -> {
                            // Device dropped during connection establishment.
                            logger.log(
                                "CONNECTION_FAILED",
                                "${device.name} | ${device.address} | status=$status"
                            )
                            setState(BleConnectionState.CONNECTION_FAILED)
                        }

                        BleConnectionState.CONNECTED -> {
                            logger.log(
                                "DISCONNECTED",
                                "${device.name} | ${device.address} | reason=remote | status=$status"
                            )
                            setState(BleConnectionState.DISCONNECTED)
                        }

                        else -> {
                            setState(BleConnectionState.DISCONNECTED)
                        }
                    }
                    closeUnusedGatt(gatt)
                }

                BluetoothProfile.STATE_CONNECTING -> {
                    // spare
                }

                BluetoothProfile.STATE_DISCONNECTING -> {
                    // spare
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                servicesCached = true
                val services = snapshotServices(gatt)
                logger.log(
                    "SERVICE_DISCOVERY_COMPLETED",
                    "Services: ${services.size}"
                )
                for (s in services) {
                    logger.log("SERVICE", "${s.uuid} type=${s.type} chars=${s.characteristics.size}")
                }
                gattCallback.onServicesDiscovered(services)
            } else {
                Log.e(TAG, "SERVICE_DISCOVERY_FAILED status=$status")
                logger.log(
                    "SERVICE_DISCOVERY_FAILED",
                    "status=$status"
                )
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            isReading = false
            val success = status == BluetoothGatt.GATT_SUCCESS
            val values = characteristic.value ?: ByteArray(0)
            val hex = HexUtils.toHex(values)
            Log.d(
                TAG,
                "GATT_READ_RESPONSE char=${characteristic.uuid} status=$status success=$success " +
                    "hex=$hex"
            )
            if (success) {
                logger.logPacket(
                    "RX",
                    characteristic.service?.uuid?.toString().orEmpty(),
                    characteristic.uuid.toString(),
                    values,
                    "read_response"
                )
            } else {
                logger.log("GATT_READ_FAILED", "status=$status char=${characteristic.uuid}")
            }
            gattCallback.onCharacteristicRead(
                characteristic.service?.uuid?.toString() ?: "",
                characteristic.uuid.toString(),
                values,
                status,
                success
            )
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            isWriting = false
            val success = status == BluetoothGatt.GATT_SUCCESS
            val values = characteristic.value ?: ByteArray(0)
            val writeTypeName = when (characteristic.writeType) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT -> "WRITE"
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE -> "WRITE_NO_RESPONSE"
                else -> "WRITE_TYPE(${characteristic.writeType})"
            }
            Log.d(
                TAG,
                "GATT_WRITE_RESPONSE char=${characteristic.uuid} status=$status " +
                    "success=$success type=$writeTypeName"
            )
            if (success) {
                logger.logPacket(
                    "TX",
                    characteristic.service?.uuid?.toString().orEmpty(),
                    characteristic.uuid.toString(),
                    values,
                    "write_type=$writeTypeName"
                )
            } else {
                logger.log(
                    "GATT_WRITE_FAILED",
                    "status=$status char=${characteristic.uuid}"
                )
            }
            gattCallback.onCharacteristicWrite(
                characteristic.service?.uuid?.toString() ?: "",
                characteristic.uuid.toString(),
                values,
                writeTypeName,
                status,
                success
            )
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val values = characteristic.value ?: ByteArray(0)
            logger.logPacket(
                "RX",
                characteristic.service?.uuid?.toString().orEmpty(),
                characteristic.uuid.toString(),
                values,
                "notification"
            )
            gattCallback.onCharacteristicChanged(
                characteristic.service?.uuid?.toString() ?: "",
                characteristic.uuid.toString(),
                values
            )
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            val values = descriptor.value ?: ByteArray(0)
            Log.d(TAG, "GATT_DESCRIPTOR_READ desc=${descriptor.uuid} status=$status success=$success")
            logger.logHex(
                if (success) "GATT_DESCRIPTOR_READ_RESPONSE" else "GATT_DESCRIPTOR_READ_FAILED",
                "Descriptor: ${descriptor.uuid} | status=$status",
                null
            )
            if (success) {
                logger.logPacket(
                    "RX",
                    "",
                    descriptor.uuid.toString(),
                    values,
                    "descriptor_read"
                )
            }
            gattCallback.onDescriptorRead(descriptor.uuid.toString(), values, status, success)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            val values = descriptor.value ?: ByteArray(0)
            Log.d(TAG, "GATT_DESCRIPTOR_WRITE desc=${descriptor.uuid} status=$status success=$success")
            logger.logHex(
                if (success) "GATT_DESCRIPTOR_WRITE_RESPONSE" else "GATT_DESCRIPTOR_WRITE_FAILED",
                "Descriptor: ${descriptor.uuid} | status=$status",
                null
            )
            gattCallback.onDescriptorWrite(descriptor.uuid.toString(), values, status, success)
        }
    }

    fun getState(): BleConnectionState = state

    fun deviceAddress(): String = device.address

    fun deviceName(): String? = device.name

    fun connect() {
        if (state == BleConnectionState.CONNECTING || state == BleConnectionState.CONNECTED) {
            Log.w(TAG, "connect() ignored, state=$state")
            return
        }
        setState(BleConnectionState.CONNECTING)
        logger.log("CONNECT_STARTED", "${device.name} | ${device.address}")
        Log.d(TAG, "CONNECT_STARTED ${device.name} ${device.address}")
        val btGatt = device.connectGatt(context, false, bluetoothGattCallback)
        gatt = btGatt
        if (btGatt == null) {
            Log.e(TAG, "connectGatt returned null")
            setState(BleConnectionState.CONNECTION_FAILED)
            return
        }
        handler.removeCallbacks(connectionTimeoutRunnable)
        handler.postDelayed(connectionTimeoutRunnable, CONNECT_TIMEOUT_MS)
    }

    fun disconnect() {
        if (state != BleConnectionState.CONNECTED &&
            state != BleConnectionState.CONNECTING
        ) {
            Log.d(TAG, "disconnect() ignored, state=$state")
            return
        }
        if (state == BleConnectionState.CONNECTED) {
            setState(BleConnectionState.DISCONNECTING)
            logger.log("DISCONNECT_STARTED", "${device.name} | ${device.address}")
        }
        handler.removeCallbacks(connectionTimeoutRunnable)
        // Refresh empty GATT cache is optional; call disconnect and close.
        gatt?.disconnect()
        closeInternal()
        logger.log("DISCONNECTED", "${device.name} | ${device.address} | reason=user")
        setState(BleConnectionState.DISCONNECTED)
    }

    fun readCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        val current = gatt ?: return false
        if (state != BleConnectionState.CONNECTED) {
            logger.log("GATT_READ_FAILED", "Not connected")
            return false
        }
        if (isReading) {
            logger.log("GATT_READ_FAILED", "Another read already in progress")
            return false
        }
        isReading = true
        logger.log(
            "GATT_READ_REQUEST",
            "Characteristic: ${characteristic.uuid} | " +
                "Service: ${characteristic.service?.uuid}"
        )
        Log.d(TAG, "GATT_READ_REQUEST char=${characteristic.uuid}")
        return try {
            current.readCharacteristic(characteristic)
        } catch (e: Exception) {
            isReading = false
            logger.log("GATT_READ_FAILED", "exception=${e.message}")
            false
        }
    }

    fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeNoResponse: Boolean
    ): Boolean {
        val current = gatt ?: return false
        if (state != BleConnectionState.CONNECTED) {
            logger.log("GATT_WRITE_FAILED", "Not connected")
            return false
        }
        if (isWriting) {
            logger.log("GATT_WRITE_FAILED", "Another write already in progress")
            return false
        }
        isWriting = true
        characteristic.writeType = if (writeNoResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        characteristic.value = value
        logger.log(
            "GATT_WRITE_REQUEST",
            "Characteristic: ${characteristic.uuid} | " +
                "Service: ${characteristic.service?.uuid} | type=" +
                (if (writeNoResponse) "WRITE_NO_RESPONSE" else "WRITE")
        )
        Log.d(TAG, "GATT_WRITE_REQUEST char=${characteristic.uuid} hex=${HexUtils.toHex(value)}")
        return try {
            current.writeCharacteristic(characteristic)
        } catch (e: Exception) {
            isWriting = false
            logger.log("GATT_WRITE_FAILED", "exception=${e.message}")
            false
        }
    }

    fun setNotifications(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ): Boolean {
        val current = gatt ?: return false
        if (state != BleConnectionState.CONNECTED) {
            logger.log("NOTIFICATION_${if (enable) "ENABLE" else "DISABLE"}_FAILED", "Not connected")
            return false
        }

        // Check for a Client Characteristic Configuration Descriptor.
        val cccd = characteristic.getDescriptor(CCCD_UUID)
        if (cccd == null) {
            Log.w(TAG, "No CCCD descriptor found for ${characteristic.uuid}")
            logger.log(
                "NOTIFICATION_${if (enable) "ENABLE" else "DISABLE"}_FAILED",
                "Characteristic: ${characteristic.uuid} | no CCCD descriptor"
            )
            gattCallback.onNotificationChanged(
                characteristic.service?.uuid?.toString() ?: "",
                characteristic.uuid.toString(),
                enable,
                false
            )
            return false
        }

        val value = when {
            enable && characteristic.properties and
                BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 ->
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE

            enable -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }

        // Subscribe first, then write the CCCD.
        val notifyOk = try {
            current.setCharacteristicNotification(characteristic, enable)
        } catch (e: Exception) {
            Log.e(TAG, "setCharacteristicNotification exception ${e.message}")
            false
        }
        Log.d(
            TAG,
            "setCharacteristicNotification(${characteristic.uuid}, $enable) => $notifyOk"
        )
        if (!notifyOk) {
            logger.log(
                "NOTIFICATION_${if (enable) "ENABLE" else "DISABLE"}_FAILED",
                "setCharacteristicNotification returned false"
            )
            gattCallback.onNotificationChanged(
                characteristic.service?.uuid?.toString() ?: "",
                characteristic.uuid.toString(),
                enable,
                false
            )
            return false
        }

        cccd.value = value
        return try {
            val ok = current.writeDescriptor(cccd)
            if (ok) {
                logger.log(
                    "NOTIFICATION_${if (enable) "ENABLE" else "DISABLE"}",
                    "Service: ${characteristic.service?.uuid} | " +
                        "Characteristic: ${characteristic.uuid} | CCCD: ${cccd.uuid}"
                )
                Log.d(
                    TAG,
                    "NOTIFICATION_${if (enable) "ENABLE" else "DISABLE"} ${characteristic.uuid}"
                )
            } else {
                logger.log(
                    "NOTIFICATION_${if (enable) "ENABLE" else "DISABLE"}_FAILED",
                    "writeDescriptor(CCCD) returned false"
                )
            }
            true
        } catch (e: Exception) {
            logger.log(
                "NOTIFICATION_${if (enable) "ENABLE" else "DISABLE"}_FAILED",
                "exception=${e.message}"
            )
            false
        }
    }

    fun readDescriptor(descriptor: BluetoothGattDescriptor): Boolean {
        val current = gatt ?: return false
        if (state != BleConnectionState.CONNECTED) {
            logger.log("GATT_DESCRIPTOR_READ_FAILED", "Not connected")
            return false
        }
        logger.log(
            "GATT_DESCRIPTOR_READ_REQUEST",
            "Descriptor: ${descriptor.uuid} | Characteristic: ${descriptor.characteristic?.uuid} "
        )
        Log.d(TAG, "GATT_DESCRIPTOR_READ_REQUEST desc=${descriptor.uuid}")
        return try {
            current.readDescriptor(descriptor)
        } catch (e: Exception) {
            logger.log("GATT_DESCRIPTOR_READ_FAILED", "exception=${e.message}")
            false
        }
    }

    fun writeDescriptor(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean {
        val current = gatt ?: return false
        if (state != BleConnectionState.CONNECTED) {
            logger.log("GATT_DESCRIPTOR_WRITE_FAILED", "Not connected")
            return false
        }
        logger.log(
            "GATT_DESCRIPTOR_WRITE_REQUEST",
            "Descriptor: ${descriptor.uuid} | HEX: ${HexUtils.toHex(value)}"
        )
        descriptor.value = value
        return try {
            current.writeDescriptor(descriptor)
        } catch (e: Exception) {
            logger.log("GATT_DESCRIPTOR_WRITE_FAILED", "exception=${e.message}")
            false
        }
    }

    fun close() {
        handler.removeCallbacks(connectionTimeoutRunnable)
        closeInternal()
    }

    private fun closeInternal() {
        val current = gatt ?: return
        gatt = null
        isReading = false
        isWriting = false
        servicesCached = false
        try {
            current.disconnect()
        } catch (_: Exception) {
        }
        try {
            // Refresh the local GATT cache on API >= 21 is not available publicly;
            // just close.
            current.close()
        } catch (_: Exception) {
        }
    }

    private fun closeUnusedGatt(gatt: BluetoothGatt) {
        try {
            gatt.close()
        } catch (_: Exception) {
        }
    }

    private fun setState(newState: BleConnectionState) {
        if (state == newState) return
        state = newState
        gattCallback.onConnectionStateChanged(device.address, newState)
    }

    private fun snapshotServices(gatt: BluetoothGatt): List<BleService> {
        val services = gatt.services ?: return emptyList()
        val result = mutableListOf<BleService>()
        for (service in services) {
            val chars = mutableListOf<ModelBleCharacteristic>()
            for (c in service.characteristics) {
                val descriptors = c.descriptors.map { d ->
                    ModelBleDescriptor(
                        uuid = d.uuid.toString(),
                        permissions = ModelBleDescriptor.permissionNames(d.permissions),
                        characteristicUuid = c.uuid.toString()
                    )
                }
                chars.add(
                    ModelBleCharacteristic(
                        uuid = c.uuid.toString(),
                        propertyMask = c.properties,
                        properties = ModelBleCharacteristic.propertiesOf(c.properties),
                        permissions = ModelBleCharacteristic.permissionsOf(c.permissions),
                        serviceUuid = service.uuid.toString(),
                        descriptors = descriptors
                    )
                )
            }
            result.add(
                BleService(
                    uuid = service.uuid.toString(),
                    type = BleService.typeName(service.type),
                    characteristics = chars
                )
            )
        }
        return result
    }

    /**
     * Find a BluetoothGattService by raw UUID string.
     */
    fun findService(uuid: String): BluetoothGattService? = gatt?.getService(uuidOf(uuid))

    private fun uuidOf(id: String) = java.util.UUID.fromString(id)

    companion object {
        private const val TAG = "H9BLE"
        const val CONNECT_TIMEOUT_MS = 15_000L
        private val CCCD_UUID =
            java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

/**
 * Local indirection to keep profile state labels close to the connection code.
 */
private object BluetoothUtilsRef {
    fun profileState(state: Int): String = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN($state)"
    }
}