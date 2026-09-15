package com.h9promax.ble.ui.device

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.h9promax.ble.R
import com.h9promax.ble.databinding.ItemCharacteristicHeaderBinding
import com.h9promax.ble.databinding.ItemCharacteristicListItemBinding
import com.h9promax.ble.model.BleCharacteristic
import com.h9promax.ble.model.BleService

/**
 * Mixed list of service headers and characteristic rows so the user can
 * tap any characteristic to open its detail screen.
 */
class DeviceServicesAdapter(
    private var items: List<UiItem>,
    private val onCharacteristicClick: (serviceUuid: String, characteristicUuid: String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun submit(services: List<BleService>) {
        items = buildServiceItems(services)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is UiItem.ServiceHeader -> VIEW_TYPE_HEADER
        is UiItem.CharacteristicRow -> VIEW_TYPE_CHAR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_CHAR -> CharViewHolder(
                ItemCharacteristicListItemBinding.inflate(inflater, parent, false)
            )
            else -> HeaderViewHolder(
                ItemCharacteristicHeaderBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is UiItem.ServiceHeader -> (holder as HeaderViewHolder).bind(item)
            is UiItem.CharacteristicRow -> (holder as CharViewHolder).bind(item)
        }
    }

    inner class HeaderViewHolder(private val binding: ItemCharacteristicHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: UiItem.ServiceHeader) {
            binding.txtHeaderUuid.text = "Service: ${item.uuid}"
            binding.txtHeaderType.text = "TYPE: ${item.type}"
        }
    }

    inner class CharViewHolder(private val binding: ItemCharacteristicListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: UiItem.CharacteristicRow) {
            binding.txtCharUuid.text = "Characteristic: ${item.characteristic.uuid}"
            binding.txtProperties.text = "Properties: ${item.characteristic.properties.joinToString(", ")}"
            if (item.characteristic.permissions.isNotEmpty()) {
                binding.txtPermissions.text =
                    "Permissions: ${item.characteristic.permissions.joinToString(", ")}"
                binding.txtPermissions.visibility = View.VISIBLE
            } else {
                binding.txtPermissions.visibility = View.GONE
            }
            val descCount = item.characteristic.descriptors.size
            if (descCount > 0) {
                binding.txtDescriptorCount.text = "Descriptors: $descCount"
                binding.txtDescriptorCount.visibility = View.VISIBLE
            } else {
                binding.txtDescriptorCount.visibility = View.GONE
            }
            binding.root.setOnClickListener {
                onCharacteristicClick(item.characteristic.serviceUuid, item.characteristic.uuid)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CHAR = 1
    }
}

sealed class UiItem {
    data class ServiceHeader(val uuid: String, val type: String) : UiItem()
    data class CharacteristicRow(val characteristic: BleCharacteristic, val serviceUuid: String) : UiItem()
}

private fun buildServiceItems(services: List<BleService>): List<UiItem> {
    val result = mutableListOf<UiItem>()
    for (s in services) {
        result.add(UiItem.ServiceHeader(s.uuid, s.type))
        for (c in s.characteristics) {
            result.add(UiItem.CharacteristicRow(c, s.uuid))
        }
    }
    return result
}