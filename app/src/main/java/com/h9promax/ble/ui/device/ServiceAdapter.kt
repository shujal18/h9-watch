package com.h9promax.ble.ui.device

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.h9promax.ble.databinding.ItemServiceBinding
import com.h9promax.ble.model.BleService

/**
 * Adapter for the discovered services list on the device screen.
 */
class ServiceAdapter(
    private var items: List<BleService>,
    private val onClick: (BleService) -> Unit
) : RecyclerView.Adapter<ServiceAdapter.ViewHolder>() {

    fun submitList(list: List<BleService>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemServiceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(private val binding: ItemServiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(service: BleService) {
            binding.txtServiceUuid.text = service.uuid
            binding.txtServiceType.text = "TYPE: ${service.type}"
            binding.txtCharacteristics.text = buildString {
                if (service.characteristics.isEmpty()) {
                    append("  (no characteristics)")
                } else {
                    for (c in service.characteristics) {
                        append("  ${c.uuid}\n")
                        append("     properties: ${c.properties.joinToString(", ")}\n")
                    }
                }
            }
            binding.root.setOnClickListener { onClick(service) }
        }
    }
}