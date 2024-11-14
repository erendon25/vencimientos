package com.example.barcodescanner

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.barcodescanner.dao.HistoryDao
import com.example.barcodescanner.dao.HistoryItemEntity
import com.example.barcodescanner.data.AppDatabase
import com.example.barcodescanner.model.HistoryItem
import com.example.barcodescanner.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class HistoryManager(context: Context) {
    private val historyDao: HistoryDao
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    init {
        val database = AppDatabase.getInstance(context)
        historyDao = database.historyDao()
    }

    suspend fun saveItemWithFirebaseId(item: HistoryItem, firebaseId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Verificar si ya existe un item con el mismo barcode y fecha
                if (isItemAlreadyRegistered(item.barcode, item.expirationDate)) {
                    return@withContext Result.failure(Exception("Item ya registrado localmente"))
                }

                val entity = convertToEntity(item).copy(firebaseId = firebaseId)
                historyDao.insert(entity)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    suspend fun saveItemWithTransaction(item: HistoryItem, firebaseId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (isItemAlreadyRegistered(item.barcode, item.expirationDate, firebaseId)) {
                    return@withContext Result.failure(Exception("Item ya registrado"))
                }

                val entity = convertToEntity(item).copy(firebaseId = firebaseId)
                historyDao.insert(entity)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    suspend fun getItemById(id: Long): HistoryItem? {
        return withContext(Dispatchers.IO) {
            try {
                val entity = historyDao.getItemById(id)
                entity?.let { convertToHistoryItem(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error obteniendo item por ID", e)
                null
            }
        }
    }

    suspend fun getAllItems(): List<HistoryItem> {
        return withContext(Dispatchers.IO) {
            historyDao.getAllItems().map { convertToHistoryItem(it) }
        }
    }

    suspend fun deleteItem(item: HistoryItem) {
        withContext(Dispatchers.IO) {
            val entity = convertToEntity(item)
            historyDao.delete(entity)
        }
    }

    suspend fun deleteAllItems() {
        withContext(Dispatchers.IO) {
            historyDao.deleteAll()
        }
    }

    suspend fun updateItemById(
        id: Long,
        quantity: Int,
        expirationDate: String,
        withdrawalDays: Int,
        withdrawalDate: String,
        firebaseId: String? = null
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Actualizando item con ID: $id")
                historyDao.updateItemById(
                    id = id,
                    quantity = quantity,
                    expirationDate = expirationDate,
                    withdrawalDays = withdrawalDays,
                    withdrawalDate = withdrawalDate,
                    firebaseId = firebaseId
                )
                Log.d(TAG, "Item actualizado exitosamente en base de datos local")
            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando item en base de datos local", e)
                throw e
            }
        }
    }


    suspend fun getItemsForWithdrawalDate(date: Date): List<HistoryItem> {
        return withContext(Dispatchers.IO) {
            getAllItems().filter { item ->
                try {
                    if (item.expirationDate.isBlank()) return@filter false
                    val expirationDate = dateFormat.parse(item.expirationDate)
                    val withdrawalDate = calculateWithdrawalDate(expirationDate, item.withdrawalDays)
                    isSameDay(withdrawalDate, date)
                } catch (e: Exception) {
                    Log.e(TAG, "Error calculating withdrawal date for item: $item", e)
                    false
                }
            }
        }
    }

    suspend fun isItemAlreadyRegistered(
        barcode: String,
        expirationDate: String,
        firebaseId: String? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            if (firebaseId != null) {
                // Si tenemos firebaseId, verificar excluyendo ese ID
                historyDao.getItemByBarcodeAndExpirationExcludingFirebaseId(
                    barcode,
                    expirationDate,
                    firebaseId
                ) != null
            } else {
                // Si no hay firebaseId, usar la validación simple
                historyDao.getItemByBarcodeAndExpiration(barcode, expirationDate) != null
            }
        }
    }

    private fun convertToEntity(item: HistoryItem): HistoryItemEntity {
        return HistoryItemEntity(
            id = item.id, // Ahora ambos son Long
            barcode = item.barcode,
            sku = item.sku ?: "",
            description = item.description ?: "",
            quantity = item.quantity,
            expirationDate = item.expirationDate,
            withdrawalDays = item.withdrawalDays,
            withdrawalDate = item.withdrawalDate ?: "",
            userName = item.user?.name ?: "",
            scanDate = item.scanDate ?: "",
            firebaseId = item.firebaseId
        )
    }
    private fun convertToHistoryItem(entity: HistoryItemEntity): HistoryItem {
        return HistoryItem(
            id = entity.id, // Ahora ambos son Long
            firebaseId = entity.firebaseId,
            barcode = entity.barcode,
            sku = entity.sku,
            description = entity.description,
            quantity = entity.quantity,
            expirationDate = entity.expirationDate,
            withdrawalDays = entity.withdrawalDays,
            withdrawalDate = entity.withdrawalDate,
            user = User(name = entity.userName),
            scanDate = entity.scanDate
        )
    }

    private fun calculateWithdrawalDate(expirationDate: Date?, withdrawalDays: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.time = expirationDate ?: Date()
        calendar.add(Calendar.DAY_OF_MONTH, -withdrawalDays)
        return calendar.time
    }
    suspend fun deleteItemsOlderThan(date: Date) {
        withContext(Dispatchers.IO) {
            val itemsToKeep = getAllItems().filter { item ->
                try {
                    if (item.expirationDate.isBlank()) return@filter true
                    val expirationDate = dateFormat.parse(item.expirationDate)
                    expirationDate?.after(date) ?: true
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing date for item: $item", e)
                    true // Conservamos el item si hay un error en el parsing
                }
            }
            // Guardar los items que queremos mantener
            historyDao.deleteAll() // Primero borramos
            itemsToKeep.forEach { item ->
                // Usar el firebaseId existente del item
                item.firebaseId?.let { firebaseId ->
                    saveItemWithFirebaseId(item, firebaseId)
                }
            }
        }
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }

    companion object {
        private const val TAG = "HistoryManager"
    }
}