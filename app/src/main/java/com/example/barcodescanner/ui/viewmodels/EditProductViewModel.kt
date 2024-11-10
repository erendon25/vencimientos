package com.example.barcodescanner.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.barcodescanner.data.ProductDatabase
import com.example.barcodescanner.entity.Product // Asegúrate de importar la clase correcta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class EditProductViewModel : ViewModel() {
    private val _product = MutableStateFlow<Product?>(null)
    val product = _product.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun setProduct(product: Product) {
        _product.value = product
    }

    suspend fun updateProduct(
        originalBarcode: String,
        newBarcode: String,
        sku: String,
        description: String,
        productDatabase: ProductDatabase
    ) {
        _isLoading.value = true
        _error.value = null

        try {
            if (originalBarcode != newBarcode) {
                val existingProduct = productDatabase.getProduct(newBarcode)
                if (existingProduct != null) {
                    _error.value = "Ya existe un producto con este código de barras"
                    return
                }
            }

            val updatedProduct = Product(
                barcode = newBarcode,
                sku = sku,
                description = description
            )

            if (originalBarcode != newBarcode) {
                productDatabase.deleteProduct(originalBarcode)
            }

            productDatabase.insertProduct(updatedProduct)
            _product.value = updatedProduct
        } catch (e: Exception) {
            _error.value = "Error al actualizar el producto: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
}