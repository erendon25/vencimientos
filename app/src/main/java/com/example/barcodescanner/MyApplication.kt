package com.example.barcodescanner

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.multidex.MultiDex
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class MyApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        initializeFirebase()
    }

    private fun initializeFirebase() {
        try {
            // Inicializar Firebase si aún no está inicializado
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }

            // Configurar Firestore con persistencia
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)  // Habilitar persistencia offline
                .build()

            // Aplicar configuración a Firestore
            FirebaseFirestore.getInstance().apply {
                firestoreSettings = settings
                enableNetwork().addOnSuccessListener {
                    Log.d(TAG, "Red de Firestore habilitada")
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Error al habilitar la red: ${e.message}")
                }
            }

            Log.d(TAG, "Firebase inicializado correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar Firebase: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MyApplication"
    }
}