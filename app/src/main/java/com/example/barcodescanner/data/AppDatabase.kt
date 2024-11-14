package com.example.barcodescanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.barcodescanner.dao.*
import com.example.barcodescanner.entity.Product
import com.example.barcodescanner.model.*

@Database(
    entities = [
        Product::class,
        User::class,
        LastUserId::class,
        HistoryItemEntity::class
    ],
    version = 1, // Comenzar desde versión 1
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun userDao(): UserDao
    abstract fun historyDao(): HistoryDao

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
                    .fallbackToDestructiveMigration() // Permitir migración destructiva durante desarrollo
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}