package com.h9promax.ble.ble

import android.util.Log
import com.h9promax.ble.model.BleLogEntry
import com.h9promax.ble.util.HexUtils
import com.h9promax.ble.util.TimeUtils
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Central in-memory log for the current BLE session.
 *
 * Every event is written to Logcat under the "H9BLE" tag and stored so it
 * can be displayed in the UI and exported. Thread safe.
 */
class BlePacketLogger(private val listeners: MutableList<BleCallback> = mutableListOf()) {

    private val entries = CopyOnWriteArrayList<BleLogEntry>()

    fun attach(listener: BleCallback) {
        if (listener !in listeners) {
            listeners.add(listener)
        }
    }

    fun detach(listener: BleCallback) {
        listeners.remove(listener)
    }

    fun entries(): List<BleLogEntry> = entries.toList()

    fun clear() {
        entries.clear()
        Log.d(TAG, "LOG_CLEARED")
        notifyChanged()
    }

    fun log(event: String, detail: String) {
        logInt(event, detail, null)
    }

    fun logHex(event: String, detail: String, bytes: ByteArray?) {
        logInt(event, detail, bytes?.let { HexUtils.toHex(it) })
    }

    /**
     * @param speaker non-null when this is a protocol RX/TX packet line.
     */
    fun logPacket(
        event: String,
        serviceUuid: String,
        characteristicUuid: String,
        bytes: ByteArray,
        extra: String = ""
    ) {
        val hex = HexUtils.toHex(bytes)
        val detail = StringBuilder()
        if (serviceUuid.isNotBlank() || characteristicUuid.isNotBlank()) {
            detail.append("Service: ").append(serviceUuid.ifBlank { "?" })
            detail.append(" | Characteristic: ").append(characteristicUuid.ifBlank { "?" })
            if (extra.isNotBlank()) detail.append(" | ").append(extra)
        } else if (extra.isNotBlank()) {
            detail.append(extra)
        }
        val ascii = HexUtils.toAscii(bytes)
        val decimal = HexUtils.toDecimal(bytes)
        Log.d(TAG, "$event ${TimeUtils.nowMillis()} $detail HEX=$hex ASCII=$ascii DEC=$decimal")
        entries.add(
            BleLogEntry(
                timestamp = TimeUtils.nowMillis(),
                event = event,
                detail = detail.toString(),
                rawHex = hex
            )
        )
        notifyChanged()
    }

    private fun logInt(event: String, detail: String, hex: String?) {
        val line = if (hex != null) "$detail HEX=$hex" else detail
        Log.d(TAG, "$event ${TimeUtils.nowMillis()} $line")
        entries.add(BleLogEntry(TimeUtils.nowMillis(), event, detail, hex))
        notifyChanged()
    }

    private fun notifyChanged() {
        for (l in listeners) {
            try {
                l.onLogChanged()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val TAG = "H9BLE"
    }
}