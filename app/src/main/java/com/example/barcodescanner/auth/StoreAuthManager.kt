package com.example.barcodescanner.auth

import com.example.barcodescanner.auth.StoreUser
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.barcodescanner.R
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.auth.FirebaseUser

class StoreAuthManager(private val context: Context) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val TAG = "StoreAuthManager"

    private val _currentUser = MutableStateFlow<StoreUser?>(null)
    val currentUser: StateFlow<StoreUser?> = _currentUser

    private val googleSignInClient: GoogleSignInClient by lazy {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

            Log.d(TAG, "Initializing GoogleSignInClient with GSO")
            GoogleSignIn.getClient(context.applicationContext, gso)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing GoogleSignInClient: ${e.message}", e)
            throw e
        }
    }

    init {
        Log.d(TAG, "StoreAuthManager initialized")
        checkGooglePlayServices()
        initializeUser()
    }
    private fun checkGooglePlayServices(): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)

        if (resultCode != ConnectionResult.SUCCESS) {
            Log.e(TAG, "Google Play Services no está disponible: $resultCode")
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                // El error puede ser resuelto por el usuario
                Log.w(TAG, "Google Play Services requiere actualización")
            }
            return false
        }
        return true
    }


    private fun initializeUser() {
        auth.currentUser?.let { firebaseUser ->
            Log.d(TAG, "Initializing user: ${firebaseUser.email}")
            setupUserListener(firebaseUser.uid)
        }
    }

    val signInClient: GoogleSignInClient
        get() = googleSignInClient

    suspend fun createNewUser(
        email: String,
        name: String,
        storeId: String,
        storeName: String,
        isAdmin: Boolean
    ): Result<StoreUser> = withContext(Dispatchers.IO) {
        try {
            val userId = auth.currentUser?.uid ?: return@withContext Result.failure(
                Exception("Usuario no autenticado")
            )

            val newUser = StoreUser(
                uid = userId,
                email = email,
                name = name,
                storeId = storeId,
                storeName = storeName,
                role = if (isAdmin) "ADMIN" else "USER",
                active = true,
                admin = isAdmin,
                createdAt = System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis()
            )

            firestore.collection("store_users")
                .document(userId)
                .set(newUser.toMap())
                .await()

            _currentUser.value = newUser
            Result.success(newUser)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating new user: ${e.message}")
            Result.failure(e)
        }
    }

    private fun setupUserListener(userId: String) {
        Log.d(TAG, "Setting up user listener for ID: $userId")

        firestore.collection("store_users")
            .document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error listening to user data: ${e.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val data = snapshot.data
                    Log.d(TAG, "Raw Firestore data: $data")

                    val user = snapshot.toObject(StoreUser::class.java)
                    Log.d(TAG, "Converted user object: $user")
                    Log.d(TAG, "User admin status: ${user?.admin}")

                    if (user != null && user.active) {
                        _currentUser.value = user
                        Log.d(TAG, "Updated current user - Email: ${user.email}, Admin: ${user.admin}")
                    } else {
                        Log.w(TAG, "Invalid user data received")
                        handleInvalidUser("Usuario no válido")
                    }
                } else {
                    Log.w(TAG, "No user document found")
                }
            }
    }

    private fun handleInvalidUser(message: String) {
        Log.d(TAG, "Handling invalid user: $message")
        _currentUser.value = null
        auth.signOut()
        googleSignInClient.signOut()
    }

    fun isLoggedIn(): Boolean {
        val currentFirebaseUser = auth.currentUser
        val currentStoreUser = _currentUser.value
        val isLoggedIn = currentFirebaseUser != null && currentStoreUser?.active == true
        Log.d(TAG, "Checking login status: $isLoggedIn")
        return isLoggedIn
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            auth.signOut()
            googleSignInClient.signOut().await()
            _currentUser.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkUserExists(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val doc = firestore.collection("store_users")
                .document(userId)
                .get()
                .await()
            doc.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking user existence: ${e.message}")
            false
        }
    }
}