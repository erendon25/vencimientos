package com.example.barcodescanner.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodescanner.databinding.ItemHistoryBinding
import com.example.barcodescanner.model.HistoryItem
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val onItemClickListener: OnItemClickListener,
    private val onItemLongClickListener: OnItemLongClickListener
) : ListAdapter<HistoryItem, HistoryAdapter.ViewHolder>(HistoryItemDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val selectedItems = mutableSetOf<HistoryItem>()
    private var isSelectionMode = false
    fun updateItems(newItems: List<HistoryItem>) {
        submitList(null)
        submitList(newItems)
    }

    class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.binding.apply {
            itemCheckbox.isVisible = isSelectionMode
            barcodeImage.isVisible = !isSelectionMode
            itemCheckbox.isChecked = selectedItems.contains(item)
            descriptionView.text = item.description
            quantityText.text = "Cantidad: ${item.quantity}"
            barcodeText.text = item.barcode
            expirationDateText.text = "Expira: ${item.expirationDate}"
            userTextView.text = "Usuario: ${item.user.name}"
            withdrawalDateText.text = "Retiro: ${calculateWithdrawalDate(item.expirationDate, item.withdrawalDays)}"

            root.setOnClickListener {
                if (isSelectionMode) {
                    toggleItemSelection(item)
                } else {
                    onItemClickListener.onItemClick(item)
                }
            }

            root.setOnLongClickListener {
                if (!isSelectionMode) {
                    isSelectionMode = true
                    toggleItemSelection(item)
                    notifyDataSetChanged()
                    onItemLongClickListener.onItemLongClick(item)
                }
                true
            }
        }
    }

    fun toggleItemSelection(item: HistoryItem) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
        } else {
            selectedItems.add(item)
        }
        notifyItemChanged(currentList.indexOf(item))

        if (selectedItems.isEmpty()) {
            isSelectionMode = false
            notifyDataSetChanged()
        }
    }

    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        notifyDataSetChanged()
    }

    fun getSelectedItems(): Set<HistoryItem> = selectedItems

    interface OnItemClickListener {
        fun onItemClick(item: HistoryItem)
    }

    interface OnItemLongClickListener {
        fun onItemLongClick(item: HistoryItem): Boolean
    }

    private fun calculateWithdrawalDate(expirationDate: String?, withdrawalDays: Int): String {
        if (expirationDate.isNullOrBlank()) return "Fecha no disponible"

        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val expDate = sdf.parse(expirationDate) ?: return "Fecha inválida"
            val calendar = Calendar.getInstance()
            calendar.time = expDate
            calendar.add(Calendar.DAY_OF_MONTH, -withdrawalDays)
            sdf.format(calendar.time)
        } catch (e: Exception) {
            "Fecha inválida"
        }
    }

    class HistoryItemDiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem.barcode == newItem.barcode && oldItem.scanDate == newItem.scanDate
        }

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}