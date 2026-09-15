package com.h9promax.ble.ui.log

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.h9promax.ble.ble.BleCallback
import com.h9promax.ble.ble.BleManager
import com.h9promax.ble.databinding.ActivityBleLogBinding
import com.h9promax.ble.util.SessionExporter
import com.h9promax.ble.util.TimeUtils
import java.io.BufferedReader
import java.io.InputStreamReader

class BleLogActivity : AppCompatActivity(), BleCallback {

    private lateinit var binding: ActivityBleLogBinding
    private lateinit var adapter: LogAdapter

    private val createFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            exportToUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBleLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = LogAdapter(BleManager.logEntries())
        binding.recyclerLog.layoutManager = LinearLayoutManager(this)
        binding.recyclerLog.adapter = adapter

        binding.btnClearLog.setOnClickListener {
            BleManager.clearLog()
            refreshList()
        }

        binding.btnExportLog.setOnClickListener {
            val name = SessionExporter.defaultFileName(BleManager.sessionDeviceAddress())
            createFileLauncher.launch("$name.json")
        }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        BleManager.register(this)
        refreshList()
    }

    override fun onPause() {
        super.onPause()
        BleManager.unregister(this)
    }

    override fun onLogChanged() {
        runOnUiThread { refreshList() }
    }

    private fun refreshList() {
        val entries = BleManager.logEntries()
        adapter.submitList(entries)
        if (entries.isNotEmpty()) {
            binding.recyclerLog.scrollToPosition(entries.lastIndex)
        }
    }

    private fun exportToUri(uri: android.net.Uri) {
        try {
            val content = buildExportContent()
            contentResolver.openOutputStream(uri)?.use { out ->
                out.bufferedWriter().use { it.write(content) }
            }
            // Also write a .txt sibling alongside the JSON when feasible by
            // prompting for a second file, or simply export JSON only.
            // For simplicity in Phase 1, export JSON as the primary format;
            // TXT can be generated too if the user picks a .txt file by
            // changing the MIME type. Both are available via the same button
            // by exporting JSON only for now. A richer flow can be added
            // later.
            android.widget.Toast.makeText(this, "Log exported to ${uri.lastPathSegment}", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun buildExportContent(): String {
        return SessionExporter.buildJson(
            deviceName = BleManager.sessionDeviceName(),
            deviceAddress = BleManager.sessionDeviceAddress(),
            connectTime = BleManager.sessionConnectTime(),
            disconnectTime = BleManager.sessionDisconnectTime(),
            services = BleManager.sessionServices(),
            entries = BleManager.logEntries()
        )
    }
}