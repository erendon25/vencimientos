package com.example.barcodescanner.dao
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.barcodescanner.model.HistoryItem
import kotlinx.coroutines.flow.Flow
@Entity(
    tableName = "history_items",
    indices = [Index(value = ["barcode", "expirationDate", "firebaseId"], unique = true)]
)
data class HistoryItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L, // Cambiado a Long
    val barcode: String,
    val sku: String,
    val description: String,
    val quantity: Int,
    val expirationDate: String,
    val withdrawalDays: Int,
    val withdrawalDate: String,
    val userName: String,
    val scanDate: String,
    val firebaseId: String?
)
@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_items ORDER BY scanDate DESC")
    suspend fun getAllItems(): List<HistoryItemEntity>


    @Query("""UPDATE history_items 
        SET quantity = :quantity, 
        expirationDate = :expirationDate, 
        withdrawalDays = :withdrawalDays, 
        withdrawalDate = :withdrawalDate,
        firebaseId = :firebaseId 
        WHERE id = :id
    """)
    suspend fun updateItemById(
        id: Long,
        quantity: Int,
        expirationDate: String,
        withdrawalDays: Int,
        withdrawalDate: String,
        firebaseId: String? = null
    )
    @Query("SELECT * FROM history_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): HistoryItemEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItemEntity)
    @Update
    suspend fun update(item: HistoryItemEntity)
    @Delete
    suspend fun delete(item: HistoryItemEntity)
    @Query("DELETE FROM history_items")
    suspend fun deleteAll()
    @Query("""
        SELECT * FROM history_items 
        WHERE barcode = :barcode 
        AND expirationDate = :expirationDate 
        LIMIT 1
    """)
    suspend fun getItemByBarcodeAndExpiration(
        barcode: String,
        expirationDate: String
    ): HistoryItemEntity?
    @Query("""
        SELECT * FROM history_items 
        WHERE barcode = :barcode 
        AND expirationDate = :expirationDate 
        AND (firebaseId IS NULL OR firebaseId != :firebaseId)
        LIMIT 1
    """)
    suspend fun getItemByBarcodeAndExpirationExcludingFirebaseId(
        barcode: String,
        expirationDate: String,
        firebaseId: String
    ): HistoryItemEntity?
}
@Database(entities = [HistoryItemEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}