package com.h9promax.ble.ui.characteristic

import android.bluetooth.BluetoothGattCharacteristic
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.h9promax.ble.ble.BleCallback
import com.h9promax.ble.ble.BleManager
import com.h9promax.ble.databinding.ActivityCharacteristicBinding
import com.h9promax.ble.model.BleCharacteristic
import com.h9promax.ble.model.BleLogEntry
import com.h9promax.ble.model.BleService
import com.h9promax.ble.ui.log.LogAdapter
import com.h9promax.ble.util.HexUtils
import com.h9promax.ble.util.TimeUtils

class CharacteristicDetailActivity : AppCompatActivity(), BleCallback {

    private lateinit var binding: ActivityCharacteristicBinding
    private var serviceUuid: String? = null
    private var charUuid: String? = null
    private var model: BleCharacteristic? = null
    private var actual: BluetoothGattCharacteristic? = null
    private val history = mutableListOf<BleLogEntry>()
    private lateinit var historyAdapter: LogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCharacteristicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        serviceUuid = intent.getStringExtra(EXTRA_SERVICE_UUID)
        charUuid = intent.getStringExtra(EXTRA_CHAR_UUID)

        binding.txtServiceUuid.text = serviceUuid ?: "n/a"
        binding.txtCharUuid.text = charUuid ?: "n/a"

        historyAdapter = LogAdapter(history)
        binding.recyclerNotificationHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerNotificationHistory.adapter = historyAdapter

        binding.btnRead.setOnClickListener { doRead() }
        binding.btnEnableNotifications.setOnClickListener { doNotification(true) }
        binding.btnDisableNotifications.setOnClickListener { doNotification(false) }
        binding.btnWrite.setOnClickListener { doWrite(false) }
        binding.btnWriteNoResponse.setOnClickListener { doWrite(true) }

        loadModel()
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        BleManager.register(this)
        // Try reloading the actual characteristic if services were discovered after
        // this activity opened.
        if (actual == null) loadActual()
    }

    override fun onPause() {
        super.onPause()
        BleManager.unregister(this)
    }

    private fun loadModel() {
        val sUuid = serviceUuid ?: return
        val cUuid = charUuid ?: return
        model = BleManager.sessionServices().flatMap { it.characteristics }
            .firstOrNull { it.serviceUuid == sUuid && it.uuid == cUuid }
        loadActual()
    }

    private fun loadActual() {
        val sUuid = serviceUuid ?: return
        val cUuid = charUuid ?: return
        actual = BleManager.findCharacteristic(sUuid, cUuid)
    }

    private fun refreshUi() {
        val m = model
        binding.txtProperties.text = m?.properties?.joinToString(", ") ?: "n/a"
        binding.txtPermissions.text = m?.permissions?.joinToString(", ")?.ifBlank { "-" } ?: "-"
        binding.btnRead.visibility = if (m?.supportsRead() == true) View.VISIBLE else View.GONE
        binding.btnEnableNotifications.visibility =
            if (m?.supportsNotify() == true || m?.supportsIndicate() == true) View.VISIBLE else View.GONE
        binding.btnDisableNotifications.visibility =
            if (m?.supportsNotify() == true || m?.supportsIndicate() == true) View.VISIBLE else View.GONE
        binding.btnWrite.visibility =
            if (m?.supportsWrite() == true) View.VISIBLE else View.GONE
        binding.btnWriteNoResponse.visibility =
            if (m?.supportsWriteNoResponse() == true) View.VISIBLE else View.GONE

        // Render descriptors
        binding.layoutDescriptors.removeAllViews()
        val descs = m?.descriptors ?: emptyList()
        for (d in descs) {
            val row = LayoutInflater.from(this)
                .inflate(com.h9promax.ble.R.layout.item_descriptor, binding.layoutDescriptors, false)
            row.findViewById<android.widget.TextView>(com.h9promax.ble.R.id.txtDescriptorUuid).text = d.uuid
            row.findViewById<android.widget.TextView>(com.h9promax.ble.R.id.txtDescriptorPerms).text =
                "permissions: ${d.permissions.joinToString(", ").ifBlank { "-" }}"
            row.findViewById<Button>(com.h9promax.ble.R.id.btnReadDescriptor).setOnClickListener {
                doReadDescriptor(d.uuid)
            }
            binding.layoutDescriptors.addView(row)
        }
    }

    private fun doRead() {
        val a = actual
        if (a == null) {
            showStatus(binding.txtReadStatus, "Characteristic not found in GATT. Are services loaded?")
            return
        }
        BleManager.readCharacteristic(a)
    }

    private fun doNotification(enable: Boolean) {
        val a = actual
        if (a == null) {
            showStatus(binding.txtReadStatus, "Characteristic not found in GATT.")
            return
        }
        BleManager.setNotifications(a, enable)
    }

    private fun doWrite(noResponse: Boolean) {
        val input = binding.editWriteHex.text.toString().trim()
        if (input.isEmpty()) {
            showStatus(binding.txtWriteStatus, getString(com.h9promax.ble.R.string.write_empty))
            return
        }
        val bytes = try {
            HexUtils.fromHex(input)
        } catch (e: IllegalArgumentException) {
            showStatus(binding.txtWriteStatus, getString(com.h9promax.ble.R.string.write_invalid) + " ${e.message}")
            return
        }
        val a = actual
        if (a == null) {
            showStatus(binding.txtWriteStatus, "Characteristic not found in GATT.")
            return
        }
        BleManager.writeCharacteristic(a, bytes, noResponse)
        showStatus(binding.txtWriteStatus, "Write submitted…")
    }

    private fun doReadDescriptor(descriptorUuid: String) {
        val sUuid = serviceUuid ?: return
        val cUuid = charUuid ?: return
        val service = BleManager.findService(sUuid)
        val desc = service?.characteristics
            ?.firstOrNull { it.uuid.toString() == cUuid }
            ?.getDescriptor(java.util.UUID.fromString(descriptorUuid))
        if (desc != null) {
            BleManager.readDescriptor(desc)
            Toast.makeText(this, "Descriptor read requested: $descriptorUuid", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Descriptor not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showStatus(view: android.widget.TextView, text: String?) {
        if (text.isNullOrBlank()) {
            view.visibility = View.GONE
        } else {
            view.text = text
            view.visibility = View.VISIBLE
        }
    }

    // --- BleCallback ----------------------------------------------------

    override fun onCharacteristicRead(
        serviceUuid: String,
        characteristicUuid: String,
        bytes: ByteArray,
        status: Int,
        success: Boolean
    ) {
        if (characteristicUuid != charUuid) return
        runOnUiThread {
            if (success) {
                updateRawData(bytes)
                showStatus(binding.txtReadStatus, "Read OK at ${TimeUtils.nowMillis()}")
            } else {
                showStatus(binding.txtReadStatus, "Read FAILED status=$status at ${TimeUtils.nowMillis()}")
            }
        }
    }

    override fun onCharacteristicChanged(
        serviceUuid: String,
        characteristicUuid: String,
        bytes: ByteArray
    ) {
        if (characteristicUuid != charUuid) return
        runOnUiThread {
            updateRawData(bytes)
            val entry = BleLogEntry(
                TimeUtils.nowMillis(),
                "RX",
                "service=$serviceUuid char=$characteristicUuid",
                HexUtils.toHex(bytes)
            )
            history.add(entry)
            historyAdapter.submitList(history.toList())
            binding.recyclerNotificationHistory.scrollToPosition(history.lastIndex)
        }
    }

    override fun onNotificationChanged(
        serviceUuid: String,
        characteristicUuid: String,
        enabled: Boolean,
        success: Boolean
    ) {
        if (characteristicUuid != charUuid) return
        runOnUiThread {
            showStatus(
                binding.txtReadStatus,
                if (success) {
                    if (enabled) "Notifications ENABLED" else "Notifications DISABLED"
                } else {
                    "Notification change FAILED"
                }
            )
        }
    }

    private fun updateRawData(bytes: ByteArray) {
        binding.txtRawTimestamp.text = TimeUtils.nowMillis()
        binding.txtHex.text = HexUtils.toHex(bytes)
        binding.txtDecimal.text = HexUtils.toDecimal(bytes)
        binding.txtAscii.text = HexUtils.toAscii(bytes)
    }

    companion object {
        const val EXTRA_SERVICE_UUID = "service_uuid"
        const val EXTRA_CHAR_UUID = "char_uuid"
    }
}