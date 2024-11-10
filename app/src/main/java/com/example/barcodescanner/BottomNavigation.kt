package com.example.barcodescanner

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController

class BottomNavigation @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BottomNavigationView(context, attrs, defStyleAttr) {

    fun setup(navController: androidx.navigation.NavController) {
        setupWithNavController(navController)
    }
}

sealed class BottomNavItem(var title: String, var icon: Int, var route: String) {
    object Scan : BottomNavItem("Scan", R.drawable.ic_scan, "scan")
    object Register : BottomNavItem("Register", R.drawable.ic_register, "register")
    object History : BottomNavItem("History", R.drawable.ic_history, "history")
}