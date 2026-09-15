package com.h9promax.ble.util

import com.h9promax.ble.model.BleLogEntry
import com.h9promax.ble.model.BleService
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds TXT and JSON exports of a BLE session. Raw packet bytes are
 * preserved unmodified (as HEX strings).
 */
object SessionExporter {

    /** Suggests a default file base name derived from the device. */
    fun defaultFileName(deviceAddress: String?): String {
        val safe = deviceAddress?.replace(":", "")?.lowercase() ?: "session"
        return "h9ble_${safe}_${TimeUtils.format(System.currentTimeMillis()).replace(":", "").replace(" ", "_")}"
    }

    fun buildTxt(
        deviceName: String?,
        deviceAddress: String?,
        connectTime: String?,
        disconnectTime: String?,
        services: List<BleService>,
        entries: List<BleLogEntry>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("H9 Pro Max BLE Explorer - Session Log")
        sb.appendLine("=====================================")
        sb.appendLine()
        sb.appendLine("Device Name:       ${deviceName ?: "n/a"}")
        sb.appendLine("Device Address:    ${deviceAddress ?: "n/a"}")
        sb.appendLine("Connection Time:   ${connectTime ?: "n/a"}")
        sb.appendLine("Disconnect Time:   ${disconnectTime ?: "n/a"}")
        sb.appendLine()
        sb.appendLine("SERVICES")
        sb.appendLine("--------")
        if (services.isEmpty()) {
            sb.appendLine("(none recorded)")
        } else {
            for (s in services) {
                sb.appendLine("Service: ${s.uuid} type=${s.type}")
                for (c in s.characteristics) {
                    sb.appendLine("  Characteristic: ${c.uuid}")
                    sb.appendLine("    Properties: ${c.properties.joinToString(", ")}")
                    sb.appendLine("    Permissions: ${c.permissions.joinToString(", ").ifBlank { "-" }}")
                    for (d in c.descriptors) {
                        sb.appendLine("    Descriptor: ${d.uuid} permissions=${d.permissions.joinToString(", ").ifBlank { "-" }}")
                    }
                }
            }
        }
        sb.appendLine()
        sb.appendLine("EVENT LOG")
        sb.appendLine("---------")
        if (entries.isEmpty()) {
            sb.appendLine("(empty)")
        } else {
            for (e in entries) {
                sb.appendLine("[${e.timestamp}] ${e.event}")
                if (e.detail.isNotBlank()) sb.appendLine("    ${e.detail}")
                if (e.rawHex != null) sb.appendLine("    HEX: ${e.rawHex}")
            }
        }
        sb.appendLine()
        sb.appendLine("Generated: ${TimeUtils.now()}")
        return sb.toString()
    }

    fun buildJson(
        deviceName: String?,
        deviceAddress: String?,
        connectTime: String?,
        disconnectTime: String?,
        services: List<BleService>,
        entries: List<BleLogEntry>
    ): String {
        val root = JSONObject()
        root.put("app", "H9ProMaxController")
        root.put("phase", 1)
        root.put("device_name", deviceName ?: JSONObject.NULL)
        root.put("device_address", deviceAddress ?: JSONObject.NULL)
        root.put("connect_time", connectTime ?: JSONObject.NULL)
        root.put("disconnect_time", disconnectTime ?: JSONObject.NULL)

        val servicesArr = JSONArray()
        for (s in services) {
            val sObj = JSONObject()
            sObj.put("uuid", s.uuid)
            sObj.put("type", s.type)
            val charsArr = JSONArray()
            for (c in s.characteristics) {
                val cObj = JSONObject()
                cObj.put("uuid", c.uuid)
                cObj.put("properties", JSONArray(c.properties))
                cObj.put("permissions", JSONArray(c.permissions))
                val descArr = JSONArray()
                for (d in c.descriptors) {
                    val dObj = JSONObject()
                    dObj.put("uuid", d.uuid)
                    dObj.put("permissions", JSONArray(d.permissions))
                    descArr.put(dObj)
                }
                cObj.put("descriptors", descArr)
                charsArr.put(cObj)
            }
            sObj.put("characteristics", charsArr)
            servicesArr.put(sObj)
        }
        root.put("services", servicesArr)

        val eventsArr = JSONArray()
        for (e in entries) {
            val eObj = JSONObject()
            eObj.put("timestamp", e.timestamp)
            eObj.put("event", e.event)
            eObj.put("detail", e.detail)
            e.rawHex?.let { eObj.put("rx_tx_hex", it) }
            eventsArr.put(eObj)
        }
        root.put("events", eventsArr)
        root.put("generated_at", TimeUtils.now())

        return root.toString(2)
    }
}