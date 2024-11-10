package com.example.barcodescanner.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.barcodescanner.AppUserManager
import com.example.barcodescanner.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.content.Context

class RegisterViewModel(application: Application) : AndroidViewModel(application) {
    private val appUserManager = AppUserManager(application)
    private val sharedPreferences = application.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    private val _selectedUser = MutableStateFlow<User?>(null)
    val selectedUser: StateFlow<User?> = _selectedUser

    init {
        loadUsers()
    }

    private fun loadUsers() {
        viewModelScope.launch {
            appUserManager.getUsers().collect { users ->
                _users.value = users
                if (_selectedUser.value == null) {
                    loadLastSelectedUser(users)
                }
            }
        }
    }
    fun selectUser(user: User) {
        _selectedUser.value = user
        saveLastSelectedUser(user.id)
    }

    fun clearSelectedUser() {
        _selectedUser.value = null
        clearLastSelectedUser()
    }

    private fun loadLastSelectedUser(users: List<User>) {
        val lastSelectedUserId = sharedPreferences.getInt("lastSelectedUserId", -1)
        val user = if (lastSelectedUserId != -1) {
            users.find { it.id == lastSelectedUserId }
        } else {
            users.firstOrNull()
        }
        _selectedUser.value = user
    }


    private fun saveLastSelectedUser(userId: Int) {
        sharedPreferences.edit().putInt("lastSelectedUserId", userId).apply()
    }

    private fun clearLastSelectedUser() {
        sharedPreferences.edit().remove("lastSelectedUserId").apply()
    }
}