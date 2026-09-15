package com.h9promax.ble.ui.scanner

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.h9promax.ble.databinding.ItemDeviceBinding
import com.h9promax.ble.model.BleDevice

/**
 * Adapter for the discovered devices list.
 */
class DeviceAdapter(
    private var items: List<BleDevice>,
    private val onClick: (BleDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    fun submitList(list: List<BleDevice>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: BleDevice) {
            binding.txtDeviceName.text = device.displayName()
            binding.txtDeviceAddress.text = device.address
            binding.txtRssi.text = "RSSI: ${device.rssi} dBm"
            binding.txtConnectable.text = when (device.isConnectable) {
                true -> "CONNECTABLE"
                false -> "NOT CONNECTABLE"
                null -> "CONNECTABLE: ?"
            }
            binding.txtServiceUuids.text = if (device.serviceUuids.isNotEmpty()) {
                "UUIDs: ${device.serviceUuids.joinToString(" ")}"
            } else {
                ""
            }
            binding.btnItemConnect.text = when {
                device.isConnected -> "CONNECTED"
                device.isConnecting -> "CONNECTING…"
                else -> "OPEN"
            }
            binding.root.setOnClickListener { onClick(device) }
            binding.btnItemConnect.setOnClickListener { onClick(device) }
        }
    }
}