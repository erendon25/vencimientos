package com.example.barcodescanner

import android.content.Context
import androidx.room.Room
import com.example.barcodescanner.dao.UserDao
import com.example.barcodescanner.data.AppDatabase
import com.example.barcodescanner.model.LastUserId
import com.example.barcodescanner.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppUserManager(context: Context) {

    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "user-database"
    ).build()
    private val userDao: UserDao = db.userDao()

    fun addUser(user: User) {
        CoroutineScope(Dispatchers.IO).launch {
            val lastUserId = userDao.getLastUserId()?.lastId ?: 0
            val newId = lastUserId + 1
            userDao.insert(user.copy(id = newId))
            userDao.insertLastUserId(LastUserId(lastId = newId))
        }
    }

    fun removeUser(user: User) {
        CoroutineScope(Dispatchers.IO).launch {
            userDao.delete(user)
        }
    }

    fun getUsers(): Flow<List<User>> {
        return userDao.getAllUsers()
    }

    suspend fun getUserById(id: Int): User? {
        return withContext(Dispatchers.IO) {
            userDao.getUserById(id)
        }
    }

    suspend fun updateUser(user: User) {
        withContext(Dispatchers.IO) {
            userDao.update(user)
        }
    }

    suspend fun getLastUserId(): Int {
        return withContext(Dispatchers.IO) {
            userDao.getLastUserId()?.lastId ?: 0
        }
    }

    suspend fun updateLastUserId(id: Int) {
        withContext(Dispatchers.IO) {
            userDao.updateLastUserId(id)  // Changed this line
        }
    }
}