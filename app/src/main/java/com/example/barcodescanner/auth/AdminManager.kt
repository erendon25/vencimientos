package com.example.barcodescanner.auth

import com.example.barcodescanner.auth.StoreUser
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdminManager {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "AdminManager"

    suspend fun createStoreUser(
        email: String,
        password: String,
        storeId: String,
        storeName: String
    ): Result<StoreUser> = withContext(Dispatchers.IO) {
        try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid ?: throw Exception("Failed to create user")

            val storeUser = StoreUser(
                uid = userId,
                email = email,
                name = email.substringBefore("@"),
                storeId = storeId,
                storeName = storeName,
                role = "store_user",
                active = true,  // Cambiado de isActive a active
                admin = false   // Agregado admin
            )

            firestore.collection("store_users")
                .document(userId)
                .set(storeUser.toMap())
                .await()

            Log.d(TAG, "Successfully created user: $email for store: $storeName")
            Result.success(storeUser)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getAllUsers(): Result<List<StoreUser>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("store_users").get().await()
            val users = snapshot.documents.mapNotNull { doc ->
                doc.toObject(StoreUser::class.java)
            }
            Result.success(users)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting users: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateUserStatus(userId: String, isActive: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Verificar si el usuario actual es admin
            auth.currentUser?.let { admin ->
                if (!isAdmin(admin.uid)) {
                    return@withContext Result.failure(Exception("No autorizado"))
                }
            } ?: return@withContext Result.failure(Exception("No hay usuario autenticado"))

            firestore.collection("store_users")
                .document(userId)
                .update("isActive", isActive)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user status: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateUserPassword(userId: String, newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.currentUser?.let { admin ->
                if (!isAdmin(admin.uid)) {
                    return@withContext Result.failure(Exception("No autorizado"))
                }
            } ?: return@withContext Result.failure(Exception("No hay usuario autenticado"))

            val userEmail = getUserEmail(userId) ?: throw Exception("Email no encontrado")
            auth.sendPasswordResetEmail(userEmail).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating password: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun getUserEmail(userId: String): String? {
        return try {
            val doc = firestore.collection("store_users")
                .document(userId)
                .get()
                .await()

            doc.getString("email")
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun isAdmin(userId: String): Boolean {
        return try {
            val doc = firestore.collection("store_users")
                .document(userId)
                .get()
                .await()

            doc.getString("role") == "admin"
        } catch (e: Exception) {
            false
        }
    }
}