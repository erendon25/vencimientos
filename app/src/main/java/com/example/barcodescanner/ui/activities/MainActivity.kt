package com.example.barcodescanner.ui.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.barcodescanner.R
import com.example.barcodescanner.auth.StoreAuthManager
import com.example.barcodescanner.databinding.ActivityMainBinding
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var authManager: StoreAuthManager
    private val RC_SIGN_IN = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = StoreAuthManager(this)
        checkGooglePlayServices()

        lifecycleScope.launch {
            setupNavigation()
            observeAuthState()
        }
    }

    private fun checkGooglePlayServices() {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                googleApiAvailability.getErrorDialog(this, resultCode, RC_SIGN_IN)?.show()
            } else {
                Toast.makeText(
                    this,
                    "Este dispositivo no es compatible con Google Play Services",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.scanFragment,
                R.id.historyContainerFragment,
                R.id.configFragment
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.loginFragment,
                R.id.storeSetupFragment -> {
                    supportActionBar?.hide()
                    binding.bottomNavigation.isVisible = false
                }
                R.id.registerFragment,
                R.id.addProductFragment,
                R.id.userManagementFragment -> {
                    supportActionBar?.show()
                    binding.bottomNavigation.isVisible = true
                }
                else -> {
                    supportActionBar?.show()
                    binding.bottomNavigation.isVisible = true
                }
            }
        }
    }

    private suspend fun observeAuthState() {
        authManager.currentUser.collectLatest { user ->
            if (user == null && navController.currentDestination?.id != R.id.loginFragment) {
                navController.navigate(R.id.loginFragment)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}