package com.example.barcodescanner.dao

import androidx.room.*
import com.example.barcodescanner.entity.Product

@Dao
interface ProductDao {
    @Query("SELECT * FROM products")
    suspend fun getAllProducts(): List<Product>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProduct(barcode: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Query("DELETE FROM products WHERE barcode = :barcode")
    suspend fun deleteProduct(barcode: String)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    @Query("SELECT * FROM products WHERE barcode LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    suspend fun searchProducts(query: String): List<Product>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

    @Query("SELECT * FROM products LIMIT :limit OFFSET :offset")
    suspend fun getProductsPaginated(limit: Int, offset: Int): List<Product>
    @Update
    suspend fun updateProduct(product: Product)

    @Query("UPDATE products SET sku = :sku, description = :description WHERE barcode = :barcode")
    suspend fun updateProductFields(barcode: String, sku: String, description: String)

    @Transaction
    suspend fun upsertProduct(product: Product) {
        val existingProduct = getProduct(product.barcode)
        if (existingProduct != null) {
            updateProduct(product)
        } else {
            insertProduct(product)
        }
    }
}