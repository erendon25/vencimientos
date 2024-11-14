package com.example.barcodescanner.dao

import android.content.Context
import android.util.Log
import com.example.barcodescanner.HistoryManager
import com.example.barcodescanner.model.HistoryItem
import com.example.barcodescanner.model.ProductScan
import com.example.barcodescanner.model.ProductScanData
import com.example.barcodescanner.model.User
import com.example.barcodescanner.model.toMap
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class ProductScanDao(private val context: Context,private val historyManager: HistoryManager) {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val scansCollection = db.collection("scans")


    init {
        checkGooglePlayServices()
    }

    private fun checkGooglePlayServices() {
        try {
            val googleApiAvailability = GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
            if (resultCode != ConnectionResult.SUCCESS) {
                Log.e("ProductScanDao", "Google Play Services no disponible")
            }
        } catch (e: Exception) {
            Log.e("ProductScanDao", "Error checking Google Play Services", e)
        }
    }

    suspend fun checkScanExists(scanId: String): Boolean {
        return try {
            Log.d("ProductScanDao", "Verificando existencia de scanId: $scanId")
            val document = scansCollection
                .document(scanId)
                .get()
                .await()
            val exists = document.exists()
            Log.d("ProductScanDao", "Documento ${if (exists) "encontrado" else "no encontrado"}")
            exists
        } catch (e: Exception) {
            Log.e("ProductScanDao", "Error verificando existencia del documento", e)
            false
        }
    }

    private fun calculateWithdrawalDate(expirationDate: String, withdrawalDays: Int): String {
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val expDate = sdf.parse(expirationDate) ?: return ""
            val calendar = Calendar.getInstance()
            calendar.time = expDate
            calendar.add(Calendar.DAY_OF_MONTH, -withdrawalDays)
            sdf.format(calendar.time)
        } catch (e: Exception) {
            Log.e("ProductScanDao", "Error calculando fecha de retiro", e)
            ""
        }
    }

    suspend fun getCurrentUserStoreId(): String? {
        val userId = auth.currentUser?.uid ?: return null
        val userDoc = db.collection("store_users").document(userId).get().await()
        return userDoc.getString("storeId")
    }

    suspend fun saveProductScan(
        barcode: String,
        productData: ProductScanData
    ): Result<String> {
        return try {
            // Verificar si ya existe un scan con el mismo barcode y fecha de expiración
            val existingScans = scansCollection
                .whereEqualTo("barcode", barcode)
                .whereEqualTo("productData.expirationDate", productData.expirationDate)
                .get()
                .await()

            if (!existingScans.isEmpty) {
                return Result.failure(Exception("Ya existe un registro con este código y fecha de vencimiento"))
            }

            // Resto del código de guardado...
            val userId = auth.currentUser?.uid ?: throw IllegalStateException("Usuario no autenticado")
            val userDoc = db.collection("store_users").document(userId).get().await()
            val storeId = userDoc.getString("storeId") ?:
            throw IllegalStateException("Usuario no asociado a una tienda")
            val storeName = userDoc.getString("storeName") ?: ""
            val scannerName = userDoc.getString("name") ?: ""

            val scan = ProductScan(
                barcode = barcode,
                userId = userId,
                storeId = storeId,
                timestamp = Timestamp(Date()),
                productData = productData.copy(
                    scanDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                    storeName = storeName,
                    scannerName = scannerName,
                    withdrawalDate = calculateWithdrawalDate(
                        productData.expirationDate,
                        productData.withdrawalDays
                    )
                )
            )

            val docRef = scansCollection.document()
            docRef.set(scan.toMap()).await()

            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("ProductScanDao", "Error guardando producto", e)
            Result.failure(e)
        }
    }

    suspend fun updateScan(
        scanId: String,
        expirationDate: String,
        quantity: Int,
        withdrawalDays: Int,
        sku: String,
        description: String
    ): Result<Unit> {
        return try {
            Log.d(TAG, "Iniciando actualización para scanId: $scanId")

            // Verificar si el documento existe
            val docSnapshot = scansCollection.document(scanId).get().await()
            if (!docSnapshot.exists()) {
                return Result.failure(Exception("Documento no encontrado en Firebase"))
            }

            val currentUser = auth.currentUser ?:
            return Result.failure(IllegalStateException("Usuario no autenticado"))

            val userDoc = db.collection("store_users")
                .document(currentUser.uid)
                .get()
                .await()

            val storeId = userDoc.getString("storeId") ?:
            throw IllegalStateException("Usuario no asociado a una tienda")
            val storeName = userDoc.getString("storeName") ?: ""
            val scannerName = userDoc.getString("name") ?: ""

            val withdrawalDate = calculateWithdrawalDate(expirationDate, withdrawalDays)

            val updates = hashMapOf<String, Any>(
                "productData" to mapOf(
                    "expirationDate" to expirationDate,
                    "quantity" to quantity,
                    "withdrawalDays" to withdrawalDays,
                    "withdrawalDate" to withdrawalDate,
                    "storeName" to storeName,
                    "scannerName" to scannerName,
                    "sku" to sku,
                    "description" to description
                ),
                "storeId" to storeId,
                "userId" to currentUser.uid,
                "lastModifiedAt" to FieldValue.serverTimestamp(),
                "lastModifiedBy" to currentUser.uid
            )

            scansCollection.document(scanId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error en updateScan: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createScan(
        scanId: String,
        barcode: String,
        description: String,
        expirationDate: Date,
        quantity: Int,
        withdrawalDays: Int,
        user: User
    ): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("Usuario no autenticado"))

            val scan = hashMapOf(
                "id" to scanId,
                "barcode" to barcode,
                "description" to description,
                "expirationDate" to expirationDate,
                "quantity" to quantity,
                "withdrawalDays" to withdrawalDays,
                "userId" to currentUser.uid,
                "userName" to user.name,
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.collection("scans")
                .document(scanId)
                .set(scan)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getScansForStore(
        storeId: String,
        startDate: Date? = null,
        endDate: Date? = null
    ): Result<List<ProductScan>> {
        return try {
            var query = scansCollection
                .whereEqualTo("storeId", storeId)
                .orderBy("timestamp", Query.Direction.DESCENDING)

            if (startDate != null && endDate != null) {
                query = query
                    .whereGreaterThanOrEqualTo("timestamp", Timestamp(startDate))
                    .whereLessThanOrEqualTo("timestamp", Timestamp(endDate))
            }

            val snapshot = query.get().await()

            val scans = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    val productData = (data["productData"] as? Map<String, Any>)?.let { pdMap ->
                        ProductScanData(
                            sku = pdMap["sku"] as? String ?: "",
                            description = pdMap["description"] as? String ?: "",
                            expirationDate = pdMap["expirationDate"] as? String ?: "",
                            quantity = (pdMap["quantity"] as? Long)?.toInt() ?: 0,
                            withdrawalDays = (pdMap["withdrawalDays"] as? Long)?.toInt() ?: 0,
                            withdrawalDate = pdMap["withdrawalDate"] as? String ?: "",
                            scanDate = pdMap["scanDate"] as? String ?: "",
                            storeName = pdMap["storeName"] as? String ?: "",
                            scannerName = pdMap["scannerName"] as? String ?: ""
                        )
                    } ?: ProductScanData()

                    ProductScan(
                        id = doc.id,
                        barcode = data["barcode"] as? String ?: "",
                        userId = data["userId"] as? String ?: "",
                        storeId = data["storeId"] as? String ?: "",
                        timestamp = data["timestamp"] as? Timestamp ?: Timestamp(Date()),
                        productData = productData
                    )
                } catch (e: Exception) {
                    Log.e("ProductScanDao", "Error parseando documento", e)
                    null
                }
            }
            Result.success(scans)
        } catch (e: Exception) {
            Log.e("ProductScanDao", "Error obteniendo scans", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "ProductScanDao"
    }
}