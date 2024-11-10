package com.example.barcodescanner.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.barcodescanner.model.LastUserId
import com.example.barcodescanner.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users") // Cambiado de "User" a "users"
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id = :id") // Cambiado de "User" a "users"
    suspend fun getUserById(id: Int): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)

    @Update
    suspend fun update(user: User)

    @Delete
    suspend fun delete(user: User)

    @Query("SELECT * FROM last_user_id WHERE id = 0")
    suspend fun getLastUserId(): LastUserId?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLastUserId(lastUserId: LastUserId)

    @Query("UPDATE last_user_id SET lastId = :lastId WHERE id = 0")
    suspend fun updateLastUserId(lastId: Int)
}