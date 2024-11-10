package com.example.barcodescanner.repository

import com.example.barcodescanner.dao.ProductDao
import com.example.barcodescanner.entity.Product
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ProductRepository(
    private val productDao: ProductDao,
    private val firestore: FirebaseFirestore
) {
    suspend fun updateProduct(product: Product): Result<Unit> = try {
        // Actualizar en Firebase
        val documentRef = firestore.collection("products").document(product.barcode)
        documentRef.set(product.toMap()).await()

        // Actualizar localmente
        productDao.updateProduct(product)

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getProduct(barcode: String): Result<Product?> = try {
        // Intentar obtener de la base de datos local primero
        val localProduct = productDao.getProduct(barcode)
        if (localProduct != null) {
            Result.success(localProduct)
        } else {
            // Si no está localmente, intentar obtener de Firebase
            val document = firestore.collection("products")
                .document(barcode)
                .get()
                .await()

            if (document.exists()) {
                val product = document.toObject(Product::class.java)
                product?.let { productDao.insertProduct(it) }
                Result.success(product)
            } else {
                Result.success(null)
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}