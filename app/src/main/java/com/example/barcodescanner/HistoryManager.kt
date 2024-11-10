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

    suspend fun saveItem(item: HistoryItem) {
        withContext(Dispatchers.IO) {
            // Verificar si el item ya existe antes de guardarlo
            if (!isItemAlreadyRegistered(item.barcode, item.expirationDate)) {
                val entity = convertToEntity(item)
                historyDao.insert(entity)
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

    suspend fun updateItem(updatedItem: HistoryItem) {
        withContext(Dispatchers.IO) {
            val entity = convertToEntity(updatedItem)
            historyDao.update(entity)
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

    suspend fun isItemAlreadyRegistered(barcode: String, expirationDate: String): Boolean {
        return withContext(Dispatchers.IO) {
            historyDao.getItemByBarcodeAndExpiration(barcode, expirationDate) != null
        }
    }
    private fun convertToEntity(item: HistoryItem): HistoryItemEntity {
        return HistoryItemEntity(
            id = 0, // Dejar que Room genere el ID
            barcode = item.barcode,
            sku = item.sku ?: "",
            description = item.description ?: "",
            quantity = item.quantity,
            expirationDate = item.expirationDate,
            withdrawalDays = item.withdrawalDays,
            withdrawalDate = item.withdrawalDate ?: "",
            userName = item.user?.name ?: "",
            scanDate = item.scanDate ?: ""
        )
    }

    private fun convertToHistoryItem(entity: HistoryItemEntity): HistoryItem {
        return HistoryItem(
            id = entity.id,
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
            historyDao.deleteAll() // Primero borramos todo
            itemsToKeep.forEach { item ->
                saveItem(item) // Guardamos los items filtrados
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