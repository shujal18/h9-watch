package com.h9promax.ble.ui.device

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.h9promax.ble.ble.BleCallback
import com.h9promax.ble.ble.BleConnectionState
import com.h9promax.ble.ble.BleManager
import com.h9promax.ble.databinding.ActivityDeviceDetailBinding
import com.h9promax.ble.model.BleService
import com.h9promax.ble.ui.characteristic.CharacteristicDetailActivity
import com.h9promax.ble.util.HexUtils

class DeviceDetailActivity : AppCompatActivity(), BleCallback {

    private lateinit var binding: ActivityDeviceDetailBinding
    private lateinit var servicesAdapter: DeviceServicesAdapter
    private var deviceName: String? = null
    private var deviceAddress: String? = null
    private var deviceRssi: Int = 0
    private var currentServices: List<BleService> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceName = intent.getStringExtra(EXTRA_NAME)
        deviceAddress = intent.getStringExtra(EXTRA_ADDRESS)
        deviceRssi = intent.getIntExtra(EXTRA_RSSI, 0)

        binding.txtDeviceName.text = deviceName ?: "Unknown Device"
        binding.txtDeviceAddress.text = deviceAddress ?: "n/a"
        binding.txtRssi.text = "RSSI: $deviceRssi dBm"

        servicesAdapter = DeviceServicesAdapter(emptyList()) { serviceUuid, charUuid ->
            openCharacteristicDetail(serviceUuid, charUuid)
        }
        binding.recyclerServices.layoutManager = LinearLayoutManager(this)
        binding.recyclerServices.adapter = servicesAdapter

        binding.btnConnect.setOnClickListener {
            deviceAddress?.let { addr -> BleManager.connect(addr) }
        }
        binding.btnDisconnect.setOnClickListener {
            BleManager.disconnect()
        }

        updateConnectionButtons(BleManager.currentConnectionState())
    }

    override fun onResume() {
        super.onResume()
        BleManager.register(this)
        refreshUi()
    }

    override fun onPause() {
        super.onPause()
        BleManager.unregister(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do NOT close the GATT connection here so the user can keep it alive
        // across screens.
    }

    // --- BleCallback ----------------------------------------------------

    override fun onConnectionStateChanged(address: String, state: BleConnectionState) {
        if (address != deviceAddress) return
        runOnUiThread { updateConnectionButtons(state) }
    }

    override fun onServicesDiscovered(services: List<BleService>) {
        currentServices = services
        runOnUiThread {
            servicesAdapter.submit(services)
            binding.txtDetailMessage.visibility = View.GONE
        }
    }

    override fun onLogChanged() {}

    private fun updateConnectionButtons(state: BleConnectionState) {
        binding.txtConnectionState.text = when (state) {
            BleConnectionState.CONNECTED -> getString(com.h9promax.ble.R.string.connection_connected)
            BleConnectionState.CONNECTING -> getString(com.h9promax.ble.R.string.connection_connecting)
            BleConnectionState.DISCONNECTING -> getString(com.h9promax.ble.R.string.connection_disconnecting)
            BleConnectionState.CONNECTION_FAILED -> getString(com.h9promax.ble.R.string.connection_failed)
            BleConnectionState.DISCONNECTED -> getString(com.h9promax.ble.R.string.connection_disconnected)
        }
        binding.txtConnectionState.setTextColor(
            ContextCompat.getColor(
                this,
                when (state) {
                    BleConnectionState.CONNECTED -> com.h9promax.ble.R.color.ok_green
                    BleConnectionState.CONNECTING,
                    BleConnectionState.DISCONNECTING -> com.h9promax.ble.R.color.warn_yellow
                    BleConnectionState.CONNECTION_FAILED -> com.h9promax.ble.R.color.error_red
                    BleConnectionState.DISCONNECTED -> com.h9promax.ble.R.color.text_muted
                }
            )
        )
        binding.btnConnect.isEnabled = state == BleConnectionState.DISCONNECTED ||
            state == BleConnectionState.CONNECTION_FAILED
        binding.btnDisconnect.isEnabled = state == BleConnectionState.CONNECTED ||
            state == BleConnectionState.CONNECTING
    }

    private fun refreshUi() {
        updateConnectionButtons(BleManager.currentConnectionState())
        if (currentServices.isNotEmpty()) {
            servicesAdapter.submit(currentServices)
        }
    }

    private fun openCharacteristicDetail(serviceUuid: String, characteristicUuid: String) {
        val intent = Intent(this, CharacteristicDetailActivity::class.java)
        intent.putExtra(CharacteristicDetailActivity.EXTRA_SERVICE_UUID, serviceUuid)
        intent.putExtra(CharacteristicDetailActivity.EXTRA_CHAR_UUID, characteristicUuid)
        startActivity(intent)
    }

    companion object {
        const val EXTRA_NAME = "device_name"
        const val EXTRA_ADDRESS = "device_address"
        const val EXTRA_RSSI = "device_rssi"

        fun putDevice(intent: Intent, device: com.h9promax.ble.model.BleDevice) {
            intent.putExtra(EXTRA_NAME, device.name)
            intent.putExtra(EXTRA_ADDRESS, device.address)
            intent.putExtra(EXTRA_RSSI, device.rssi)
        }
    }
}