package com.example.barcodescanner.data

import android.content.Context
import android.util.Log
import com.example.barcodescanner.entity.Product
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.delay
import org.apache.poi.ss.usermodel.WorkbookFactory
class ProductDatabase(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private var isLocalCacheValid = false
    private val database = AppDatabase.getInstance(context)
    private val productDao = database.productDao()
    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .build()
        }
    }

    init {
        FirebaseFirestore.setLoggingEnabled(true)  // Para ayudar con la depuración
    }

    private fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null
    }
    private suspend fun waitForAuthentication(): Boolean = withContext(Dispatchers.IO) {
        var attempts = 0
        while (attempts < 3) {
            if (auth.currentUser != null) {
                return@withContext true
            }
            delay(1000)
            attempts++
        }
        false
    }


    suspend fun getProductCount(): Int = withContext(Dispatchers.IO) {
        try {
            if (!isUserAuthenticated()) {
                Log.e("ProductDatabase", "User not authenticated")
                return@withContext 0
            }

            val snapshot = firestore.collection("products")
                .get()
                .await()
            snapshot.size()
        } catch (e: Exception) {
            Log.e("ProductDatabase", "Error getting product count: ${e.message}")
            0
        }
    }
    // Función para obtener productos preferentemente desde caché local
    suspend fun getProductsPreferLocal(): List<Product> = withContext(Dispatchers.IO) {
        try {
            if (!isLocalCacheValid) {
                val products = getAllProducts()
                try {
                    database.productDao().deleteAllProducts()
                    database.productDao().insertProducts(products)
                    isLocalCacheValid = true
                } catch (e: Exception) {
                    Log.e("ProductDatabase", "Error updating local cache: ${e.message}")
                }
                return@withContext products
            }
            return@withContext database.productDao().getAllProducts()
        } catch (e: Exception) {
            Log.e("ProductDatabase", "Error getting local products: ${e.message}")
            return@withContext emptyList()
        }
    }

    suspend fun invalidateCache() {
        isLocalCacheValid = false
    }
    // Función para sincronizar con Firebase solo cuando sea necesario
    suspend fun syncWithFirebaseIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        if (!isLocalCacheValid) {
            try {
                val products = getAllProducts()
                try {
                    database.productDao().deleteAllProducts()
                    database.productDao().insertProducts(products)
                    isLocalCacheValid = true
                    return@withContext true
                } catch (e: Exception) {
                    Log.e("ProductDatabase", "Error updating local database: ${e.message}")
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e("ProductDatabase", "Error syncing with Firebase: ${e.message}")
                return@withContext false
            }
        }
        return@withContext true
    }
    suspend fun getProductsPaginated(page: Int, pageSize: Int): List<Product> = withContext(Dispatchers.IO) {
        try {
            if (!isUserAuthenticated()) {
                Log.e("ProductDatabase", "User not authenticated")
                return@withContext emptyList()
            }

            // Calcular el último documento de la página anterior
            val startAfterDoc = if (page > 1) {
                firestore.collection("products")
                    .orderBy("barcode")
                    .limit((page - 1) * pageSize.toLong())
                    .get()
                    .await()
                    .documents
                    .lastOrNull()
            } else null

            // Obtener la página actual
            val query = firestore.collection("products")
                .orderBy("barcode")  // Solo ordenar por barcode
                .limit(pageSize.toLong())
                .apply {
                    if (startAfterDoc != null) {
                        startAfter(startAfterDoc)
                    }
                }

            val snapshot = query.get().await()

            snapshot.documents.mapNotNull { doc ->
                doc.toProduct()
            }
        } catch (e: Exception) {
            Log.e("ProductDatabase", "Error getting paginated products: ${e.message}")
            emptyList()
        }
    }
    suspend fun getFirebaseProductCount(): Int = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("products")
                .get()
                .await()
            snapshot.size()
        } catch (e: Exception) {
            Log.e("ProductDatabase", "Error getting Firebase product count: ${e.message}")
            0
        }
    }


    suspend fun uploadProductsToFirebase() = withContext(Dispatchers.IO) {
        try {
            val products = getAllProducts()
            var batch = firestore.batch()
            var operationCount = 0

            products.forEach { product ->
                val docRef = firestore.collection("products").document(product.barcode)
                batch.set(docRef, product.toMap())
                operationCount++

                if (operationCount >= 450) { // Firebase tiene un límite de 500 operaciones por batch
                    batch.commit().await()
                    batch = firestore.batch()
                    operationCount = 0
                }
            }

            // Commit final para las operaciones restantes
            if (operationCount > 0) {
                batch.commit().await()
            }
        } catch (e: Exception) {
            Log.e("ProductDatabase", "Error uploading to Firebase: ${e.message}")
            throw e
        }
    }

    suspend fun loadFromExcelAsync(fileName: String) = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.assets.open(fileName)
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)

            var batch = firestore.batch()
            var operationCount = 0

            for (row in sheet) {
                if (row.rowNum == 0) continue // Saltar la fila de encabezado

                try {
                    val barcode = row.getCell(0)?.stringCellValue?.trim()
                    val sku = row.getCell(1)?.stringCellValue?.trim()
                    val description = row.getCell(2)?.stringCellValue?.trim()

                    if (!barcode.isNullOrEmpty()) {
                        val product = Product(
                            barcode = barcode,
                            sku = sku ?: "",
                            description = description ?: ""
                        )

                        val docRef = firestore.collection("products").document(barcode)
                        batch.set(docRef, product.toMap())
                        operationCount++

                        if (operationCount >= 450) {
                            batch.commit().await()
                            batch = firestore.batch()
                            operationCount = 0
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ProductDatabase", "Error processing Excel row: ${e.message}")
                }
            }

            // Commit final para las operaciones restantes
            if (operationCount > 0) {
                batch.commit().await()
            }

            workbook.close()
            inputStream.close()

        } catch (e: Exception) {
            Log.e("ProductDatabase", "Error loading Excel file: ${e.message}")
            throw e
        }
    }

    suspend fun getAllProducts(): List<Product> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("products")
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.toProduct()
            }
        } catch (e: Exception) {
            Log.e("ProductDatabase", "Error getting products: ${e.message}")
            emptyList()
        }
    }

    suspend fun getProduct(barcode: String): Product? = withContext(Dispatchers.IO) {
        try {
            if (!waitForAuthentication()) {
                Log.e("ProductDatabase", "User not authenticated")
                return@withContext null
            }

            val doc = firestore.collection("products")
                .document(barcode)
                .get()
                .await()

            if (doc.exists()) {
                doc.toProduct()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ProductDatabase", "Error getting product: ${e.message}")
            null
        }
    }

    suspend fun insertProduct(product: Product) = withContext(Dispatchers.IO) {
        try {
            firestore.collection("products")
                .document(product.barcode)
                .set(productToMap(product))  // Usar la nueva función
                .await()
            true
        } catch (e: Exception) {
            Log.e("ProductDatabase", "Error inserting product: ${e.message}")
            false
        }
    }

    suspend fun deleteProduct(barcode: String) = withContext(Dispatchers.IO) {
        try {
            firestore.collection("products")
                .document(barcode)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            Log.e("ProductDatabase", "Error deleting product: ${e.message}")
            false
        }
    }

    suspend fun searchProducts(query: String): List<Product> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("products")
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.toProduct()
            }.filter { product ->
                product.barcode.contains(query, ignoreCase = true) ||
                        product.sku.contains(query, ignoreCase = true) ||
                        product.description.contains(query, ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e("ProductDatabase", "Error searching products: ${e.message}")
            emptyList()
        }
    }


    private fun DocumentSnapshot.toProduct(): Product? {
        return try {
            val data = data
            if (data != null) {
                Product(
                    barcode = data["barcode"] as? String ?: return null,
                    sku = data["sku"] as? String ?: "",
                    description = data["description"] as? String ?: ""
                )
            } else null
        } catch (e: Exception) {
            Log.e("ProductDatabase", "Error converting document to product: ${e.message}")
            null
        }
    }
    private fun productToMap(product: Product): Map<String, Any> {
        return mapOf(
            "barcode" to product.barcode,
            "sku" to product.sku,
            "description" to product.description,
            "timestamp" to FieldValue.serverTimestamp()
        )
    }

    // Método para configurar índices compuestos si son necesarios
    private suspend fun setupIndexes() {
        try {
            // Esto es solo un ejemplo, los índices reales se configuran en la consola de Firebase
            firestore.collection("products")
                .orderBy("barcode")
                .limit(1)
                .get()
                .await()
        } catch (e: Exception) {
            Log.e("ProductDatabase", "Error setting up indexes: ${e.message}")
        }
    }

    // Método para manejar la paginación con cursor
    suspend fun getNextPage(lastProduct: Product?, pageSize: Int): List<Product> = withContext(Dispatchers.IO) {
        try {
            val query = firestore.collection("products")
                .orderBy("barcode")
                .limit(pageSize.toLong())
                .apply {
                    if (lastProduct != null) {
                        startAfter(lastProduct.barcode)
                    }
                }

            val snapshot = query.get().await()
            snapshot.documents.mapNotNull { it.toProduct() }
        } catch (e: Exception) {
            Log.e("ProductDatabase", "Error getting next page: ${e.message}")
            emptyList()
        }
    }
}

