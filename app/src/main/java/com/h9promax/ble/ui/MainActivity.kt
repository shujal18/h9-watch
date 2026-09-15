package com.h9promax.ble.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.h9promax.ble.R
import com.h9promax.ble.ble.BleCallback
import com.h9promax.ble.ble.BleConnectionState
import com.h9promax.ble.ble.BleManager
import com.h9promax.ble.ble.ScanStopReason
import com.h9promax.ble.databinding.ActivityMainBinding
import com.h9promax.ble.model.BleDevice
import com.h9promax.ble.ui.device.DeviceDetailActivity
import com.h9promax.ble.ui.log.BleLogActivity
import com.h9promax.ble.ui.scanner.DeviceAdapter
import com.h9promax.ble.util.BluetoothUtils
import com.h9promax.ble.util.PermissionUtils
import com.h9promax.ble.util.ScanGate
import com.h9promax.ble.util.ScanGates

class MainActivity : AppCompatActivity(), BleCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DeviceAdapter
    private val handler = Handler(Looper.getMainLooper())

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                val enabled = state == BluetoothAdapter.STATE_ON
                BleManager.notifyBluetoothState(enabled)
                updateBluetoothUi()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        BleManager.init(applicationContext)
        BleManager.register(this)

        adapter = DeviceAdapter(emptyList()) { device ->
            openDeviceDetail(device)
        }
        binding.recyclerDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevices.adapter = adapter

        binding.btnScan.setOnClickListener { onScanClicked() }
        binding.btnStop.setOnClickListener {
            BleManager.stopScan(ScanStopReason.USER)
        }
        binding.btnEnableBluetooth.setOnClickListener {
            PermissionUtils.requestEnableBluetooth(this)
        }
        binding.btnOpenLog.setOnClickListener {
            startActivity(Intent(this, BleLogActivity::class.java))
        }

        updateBluetoothUi()
    }

    override fun onResume() {
        super.onResume()
        BleManager.register(this)
        updateBluetoothUi()
        refreshDeviceList()
        registerBluetoothReceiver()
    }

    override fun onPause() {
        super.onPause()
        BleManager.unregister(this)
        unregisterReceiverSafely()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    // --- Bluetooth / permissions -----------------------------------------

    private fun updateBluetoothUi() {
        val bt = BluetoothUtils.adapter(this)
        binding.txtBluetoothStatus.text = when {
            bt == null -> {
                binding.btnEnableBluetooth.visibility = View.VISIBLE
                binding.btnEnableBluetooth.text = getString(R.string.bluetooth_unavailable)
                binding.btnEnableBluetooth.isEnabled = false
                getString(R.string.bluetooth_status_format, getString(R.string.bluetooth_unavailable))
            }

            !bt.isEnabled -> {
                binding.btnEnableBluetooth.visibility = View.VISIBLE
                binding.btnEnableBluetooth.isEnabled = true
                getString(R.string.bluetooth_status_format, getString(R.string.bluetooth_off))
            }

            else -> {
                binding.btnEnableBluetooth.visibility = View.GONE
                getString(R.string.bluetooth_status_format, getString(R.string.bluetooth_on))
            }
        }
    }

    private fun onScanClicked() {
        val bt = BluetoothUtils.adapter(this)
        val locationEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ||
            PermissionUtils.isLocationEnabled(this)
        when (
            ScanGates.evaluate(
                bluetoothLowEnergySupported = BleManager.hasBluetoothFeature(this),
                hasAdapter = bt != null,
                bluetoothEnabled = bt?.isEnabled == true,
                hasPermissions = PermissionUtils.hasBleScanPermissions(this),
                locationEnabled = locationEnabled
            )
        ) {
            ScanGate.OK -> startScan()

            ScanGate.NO_BLUETOOTH_ADAPTER ->
                showMessage(getString(R.string.bluetooth_unavailable))

            ScanGate.BLUETOOTH_DISABLED -> {
                showMessage(getString(R.string.bluetooth_needed))
                binding.btnEnableBluetooth.visibility = View.VISIBLE
            }

            ScanGate.PERMISSIONS_MISSING -> {
                showMessage(getString(R.string.permission_needed))
                PermissionUtils.requestBlePermissions(this)
            }

            ScanGate.LOCATION_DISABLED -> showDialogAndOpenLocationSettings()
        }
    }

    private fun showDialogAndOpenLocationSettings() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Location required")
            .setMessage(getString(R.string.location_needed))
            .setPositiveButton("OPEN LOCATION SETTINGS") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun startScan() {
        binding.txtScanState.text = getString(R.string.scanning)
        binding.btnScan.isEnabled = false
        binding.btnStop.isEnabled = true
        showMessage(null)
        BleManager.startScan()
    }

    private fun showMessage(text: String?) {
        binding.txtMessage.text = text
        binding.txtMessage.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // --- BleCallback ------------------------------------------------------

    override fun onScanStarted() {
        runOnUiThread {
            binding.txtScanState.text = getString(R.string.scanning)
            binding.btnScan.isEnabled = false
            binding.btnStop.isEnabled = true
        }
    }

    override fun onScanStopped(reason: ScanStopReason) {
        runOnUiThread {
            binding.txtScanState.text = when (reason) {
                ScanStopReason.TIMEOUT -> getString(R.string.scan_timeout_hint)
                else -> getString(R.string.scan_idle)
            }
            binding.btnScan.isEnabled = true
            binding.btnStop.isEnabled = false
        }
    }

    override fun onDeviceDiscovered(device: BleDevice) {
        runOnUiThread { refreshDeviceList() }
    }

    override fun onConnectionStateChanged(address: String, state: BleConnectionState) {
        runOnUiThread { refreshDeviceList() }
    }

    override fun onLogChanged() {
        // noop on main screen
    }

    private fun refreshDeviceList() {
        adapter.submitList(BleManager.devicesList())
        binding.txtNoDevices.visibility =
            if (BleManager.devicesList().isEmpty()) View.VISIBLE else View.GONE
    }

    // --- Activity results ---------------------------------------------------

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PermissionUtils.rcBlePermissions()) return

        val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (granted) {
            showMessage(null)
            binding.btnEnableBluetooth.visibility = View.GONE
            // User may have just granted; try to start the scan automatically.
            onScanClicked()
        } else {
            val anyPermanent = permissions.indices.any { index ->
                grantResults[index] == PackageManager.PERMISSION_DENIED &&
                    PermissionUtils.isPermanentlyDenied(this, permissions[index])
            }
            if (anyPermanent) {
                Toast.makeText(this, R.string.permission_denied_permanent, Toast.LENGTH_LONG).show()
            } else {
                showMessage(getString(R.string.permission_denied))
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PermissionUtils.rcRequestEnableBluetooth()) {
            updateBluetoothUi()
        }
    }

    // --- Helpers -----------------------------------------------------------

    private fun openDeviceDetail(device: BleDevice) {
        val intent = Intent(this, DeviceDetailActivity::class.java)
        DeviceDetailActivity.putDevice(intent, device)
        startActivity(intent)
    }

    private fun registerBluetoothReceiver() {
        if (registerFlagsOk()) {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            ContextCompat.registerReceiver(
                this,
                bluetoothStateReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        }
    }

    private fun registerFlagsOk(): Boolean = true

    private fun unregisterReceiverSafely() {
        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (_: IllegalArgumentException) {
            // not registered
        }
    }
}