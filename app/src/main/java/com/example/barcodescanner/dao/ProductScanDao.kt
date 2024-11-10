package com.example.barcodescanner.dao

import android.content.Context
import android.util.Log
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
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class ProductScanDao (private val context: Context){
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val scansCollection = db.collection("scans")
    init {
        // Verificar servicios de Google Play
        checkGooglePlayServices()
    }
    private fun checkGooglePlayServices() {
        try {
            val googleApiAvailability = GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
            if (resultCode != ConnectionResult.SUCCESS) {
                Log.e("ProductScanDao", "Google Play Services no disponible")
                // Manejar el error según sea necesario
            }
        } catch (e: Exception) {
            Log.e("ProductScanDao", "Error checking Google Play Services", e)
        }
    }
    suspend fun checkScanExists(scanId: String): Boolean {
        return try {
            val document = db.collection("scans")
                .document(scanId)
                .get()
                .await()
            document.exists()
        } catch (e: Exception) {
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
            // 1. Verificar autenticación y permisos
            val userId = auth.currentUser?.uid ?: throw IllegalStateException("Usuario no autenticado")
            val userDoc = db.collection("store_users").document(userId).get().await()
            val storeId = userDoc.getString("storeId") ?: throw IllegalStateException("Usuario no asociado a una tienda")
            val storeName = userDoc.getString("storeName") ?: ""
            val scannerName = userDoc.getString("name") ?: ""

            // 2. Calcular la fecha de retiro
            val withdrawalDate = calculateWithdrawalDate(
                productData.expirationDate,
                productData.withdrawalDays
            )

            // 3. Crear el objeto de escaneo
            val currentTime = Timestamp(Date())
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val scanDate = sdf.format(Date())

            val scan = ProductScan(
                barcode = barcode,
                userId = userId,
                storeId = storeId,
                timestamp = currentTime,
                productData = productData.copy(
                    scanDate = scanDate,
                    storeName = storeName,
                    scannerName = scannerName,
                    withdrawalDate = calculateWithdrawalDate(
                        productData.expirationDate,
                        productData.withdrawalDays
                    )
                )
            )

            // 4. Guardar en Firestore
            val docRef = scansCollection.document()
            docRef.set(scan.toMap()).await()
            Log.d("ProductScanDao", "ID del documento: ${userId}")

            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateScan(
        scanId: String,
        expirationDate: String,
        quantity: Int,
        withdrawalDays: Int
    ): Result<Unit> {
        return try {
            Log.d("ProductScanDao", "Iniciando actualización para scanId: $scanId")

            // 1. Verificar autenticación
            val currentUser = auth.currentUser ?: run {
                Log.w("ProductScanDao", "Usuario no autenticado")
                return Result.failure(IllegalStateException("Usuario no autenticado"))
            }

            // 2. Obtener información del usuario y store
            val userDoc = db.collection("store_users")
                .document(currentUser.uid)
                .get()
                .await()

            val storeId = userDoc.getString("storeId") ?: throw IllegalStateException("Usuario no asociado a una tienda")
            val storeName = userDoc.getString("storeName") ?: ""
            val scannerName = userDoc.getString("name") ?: ""

            // 3. Calcular fecha de retiro
            val withdrawalDate = calculateWithdrawalDate(expirationDate, withdrawalDays)

            // 4. Preparar datos de actualización con la estructura correcta
            val updates = hashMapOf<String, Any>(
                "productData" to mapOf(
                    "expirationDate" to expirationDate,
                    "quantity" to quantity,
                    "withdrawalDays" to withdrawalDays,
                    "withdrawalDate" to withdrawalDate,
                    "storeName" to storeName,
                    "scannerName" to scannerName
                ),
                "storeId" to storeId,
                "userId" to currentUser.uid,
                "lastModifiedAt" to FieldValue.serverTimestamp(),
                "lastModifiedBy" to currentUser.uid
            )

            // 5. Realizar la actualización
            scansCollection.document(scanId)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ProductScanDao", "Error en updateScan: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createScan(
        scanId: String,
        barcode: String,
        description: String,
        expirationDate: String,
        quantity: Int,
        withdrawalDays: Int,
        user: User
    ): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?:
            return Result.failure(Exception("Usuario no autenticado"))

            val userDoc = db.collection("store_users")
                .document(currentUser.uid)
                .get()
                .await()

            val storeId = userDoc.getString("storeId") ?:
            throw IllegalStateException("Usuario no asociado a una tienda")
            val storeName = userDoc.getString("storeName") ?: ""

            val withdrawalDate = calculateWithdrawalDate(expirationDate, withdrawalDays)
            val scanDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

            val scan = hashMapOf(
                "barcode" to barcode,
                "userId" to currentUser.uid,
                "storeId" to storeId,
                "timestamp" to FieldValue.serverTimestamp(),
                "productData" to mapOf(
                    "sku" to "",
                    "description" to description,
                    "expirationDate" to expirationDate,
                    "quantity" to quantity,
                    "withdrawalDays" to withdrawalDays,
                    "withdrawalDate" to withdrawalDate,
                    "scanDate" to scanDate,
                    "storeName" to storeName,
                    "scannerName" to user.name
                )
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
                            withdrawalDate = pdMap["withdrawalDate"] as? String ?: "", // Nuevo campo
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
                    null
                }
            }
            Result.success(scans)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}