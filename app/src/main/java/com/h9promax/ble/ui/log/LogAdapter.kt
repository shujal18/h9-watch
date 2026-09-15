package com.h9promax.ble.ui.log

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.h9promax.ble.databinding.ItemLogBinding
import com.h9promax.ble.model.BleLogEntry

/**
 * Adapter for rendering log entries in both the BLE Log screen and the
 * characteristic notification history.
 */
class LogAdapter(
    private var items: List<BleLogEntry>
) : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    fun submitList(list: List<BleLogEntry>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(private val binding: ItemLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: BleLogEntry) {
            binding.txtLogTimestamp.text = entry.timestamp
            binding.txtLogEvent.text = entry.event
            val detail = StringBuilder()
            if (entry.detail.isNotBlank()) detail.append(entry.detail)
            if (entry.rawHex != null) {
                if (detail.isNotEmpty()) detail.append('\n')
                detail.append("HEX: ").append(entry.rawHex)
            }
            binding.txtLogDetail.text = detail.toString()
        }
    }
}