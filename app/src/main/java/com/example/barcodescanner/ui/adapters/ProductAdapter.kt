package com.example.barcodescanner.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodescanner.databinding.ItemProductBinding
import com.example.barcodescanner.entity.Product

class ProductAdapter(
    private val products: MutableList<Product> = mutableListOf(),
    private val isAdmin: Boolean = false,
    private val onEditClick: (Product) -> Unit = {}
) : RecyclerView.Adapter<ProductAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            binding.apply {
                barcodeTextView.text = "Código: ${product.barcode}"
                skuTextView.text = "SKU: ${product.sku}"
                descriptionTextView.text = "Descripción: ${product.description}"

                // Configurar botón de edición
                editButton.visibility = if (isAdmin) View.VISIBLE else View.GONE
                editButton.setOnClickListener { onEditClick(product) }

                // Opcional: Configurar el clic en toda la tarjeta
                root.setOnClickListener {
                    if (isAdmin) {
                        onEditClick(product)
                    }
                }
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProductBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount() = products.size

    fun getProductAt(position: Int): Product = products[position]

    fun removeProduct(position: Int) {
        if (position in products.indices) {
            products.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, products.size)
        }
    }

    fun updateList(newProducts: List<Product>) {
        val diffCallback = ProductDiffCallback(products, newProducts)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        products.clear()
        products.addAll(newProducts)

        diffResult.dispatchUpdatesTo(this)
    }

    private class ProductDiffCallback(
        private val oldList: List<Product>,
        private val newList: List<Product>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].barcode == newList[newItemPosition].barcode
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]

            return oldItem.barcode == newItem.barcode &&
                    oldItem.sku == newItem.sku &&
                    oldItem.description == newItem.description
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            // Opcional: Implementar si necesitas animaciones más específicas
            return super.getChangePayload(oldItemPosition, newItemPosition)
        }
    }

    // Métodos auxiliares para gestionar la lista
    fun addProduct(product: Product) {
        products.add(product)
        notifyItemInserted(products.size - 1)
    }

    fun updateProduct(updatedProduct: Product) {
        val position = products.indexOfFirst { it.barcode == updatedProduct.barcode }
        if (position != -1) {
            products[position] = updatedProduct
            notifyItemChanged(position)
        }
    }

    fun clearList() {
        val size = products.size
        products.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun getProducts(): List<Product> = products.toList()
}