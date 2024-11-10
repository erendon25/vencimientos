package com.example.barcodescanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.barcodescanner.dao.ProductDao
import com.example.barcodescanner.dao.UserDao
import com.example.barcodescanner.dao.HistoryDao // Agregar esta importación
import com.example.barcodescanner.dao.HistoryItemEntity // Agregar esta importación
import com.example.barcodescanner.entity.Product
import com.example.barcodescanner.model.LastUserId
import com.example.barcodescanner.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Product::class,
        User::class,
        LastUserId::class,
        HistoryItemEntity::class // Agregar esta entidad
    ],
    version = 2, // Incrementar la versión
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun userDao(): UserDao
    abstract fun historyDao(): HistoryDao // Agregar esta función

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration() // Esto permitirá la migración automática
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}