package com.example.barcodescanner.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodescanner.R
import com.example.barcodescanner.model.HistoryItem

class WithdrawalScheduleAdapter : ListAdapter<HistoryItem, WithdrawalScheduleAdapter.ViewHolder>(DiffCallback()) {

    private var onItemClickListener: ((HistoryItem) -> Unit)? = null

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val barcodeText: TextView = itemView.findViewById(R.id.barcodeText)
        private val expirationDateText: TextView = itemView.findViewById(R.id.expirationDateText)
        private val quantityText: TextView = itemView.findViewById(R.id.quantityText)
        private val withdrawalDaysText: TextView = itemView.findViewById(R.id.withdrawalDaysText)
        private val userText: TextView = itemView.findViewById(R.id.userText)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener?.invoke(getItem(position))
                }
            }
        }

        fun bind(item: HistoryItem) {
            descriptionText.text = item.description
            barcodeText.text = "Código: ${item.barcode}"
            expirationDateText.text = "Vence: ${item.expirationDate}"
            quantityText.text = "Cantidad: ${item.quantity}"
            withdrawalDaysText.text = "Días retiro: ${item.withdrawalDays}"
            userText.text = "Usuario: ${item.user.name}"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_withdrawal_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun setOnItemClickListener(listener: (HistoryItem) -> Unit) {
        onItemClickListener = listener
    }

    private class DiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}