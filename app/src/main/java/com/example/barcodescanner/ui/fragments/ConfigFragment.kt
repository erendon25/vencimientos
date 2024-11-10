package com.example.barcodescanner.ui.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.barcodescanner.ExcelImporter
import com.example.barcodescanner.HistoryManager
import com.example.barcodescanner.ImportResult
import com.example.barcodescanner.data.ProductDatabase
import com.example.barcodescanner.R
import com.example.barcodescanner.auth.StoreAuthManager
import com.example.barcodescanner.databinding.FragmentConfigBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ConfigFragment : Fragment() {
    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding!!
    private lateinit var historyManager: HistoryManager
    private lateinit var excelImporter: ExcelImporter
    private lateinit var authManager: StoreAuthManager
    private lateinit var productDatabase: ProductDatabase

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                lifecycleScope.launch {
                    try {
                        val importResult = excelImporter.importFromExcel(requireContext(), uri)
                        showImportResult(importResult)
                    } catch (e: Exception) {
                        showToastSafely("Error al importar: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeManagers()
        setupClickListeners()
    }

    private fun initializeManagers() {
        historyManager = HistoryManager(requireContext())
        excelImporter = ExcelImporter(historyManager)
        authManager = StoreAuthManager(requireContext())
        productDatabase = ProductDatabase(requireContext())
    }
    private fun setupDatabaseManagement() {
        binding.buttonDatabaseManagement.setOnClickListener {
            showDatabaseOptionsDialog()
        }
    }



    private fun setupClickListeners() {
        with(binding) {
            buttonUserManagement.setOnClickListener {
                findNavController().navigate(R.id.action_configFragment_to_userManagementFragment)
            }

            buttonDatabaseManagement.setOnClickListener {
                showDatabaseOptionsDialog()
            }

            buttonImportRecords.setOnClickListener {
                openFilePicker()
            }

            buttonLogout.setOnClickListener {
                showLogoutConfirmation()
            }
        }
    }

    private fun showDatabaseOptionsDialog() {
        val options = arrayOf(
            "Ver Base de Datos",
            "Sincronizar con Firebase",
            "Actualizar Base de Datos Local"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Gestión de Base de Datos")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> findNavController().navigate(R.id.action_configFragment_to_databaseFragment)
                    1 -> syncWithFirebase()
                    2 -> updateLocalDatabase()
                }
            }
            .show()
    }



    private fun syncWithFirebase() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showLoading(true, "Sincronizando con Firebase...")
                val result = productDatabase.uploadProductsToFirebase()
                showToastSafely("Sincronización completada exitosamente")
            } catch (e: Exception) {
                showToastSafely("Error en la sincronización: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    private fun updateLocalDatabase() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showLoading(true, "Actualizando base de datos local...")
                val count = productDatabase.getAllProducts().size
                showToastSafely("Base de datos actualizada. Total de productos: $count")
            } catch (e: Exception) {
                showToastSafely("Error en la actualización: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean, message: String = "") {
        binding.apply {
            loadingContainer.isVisible = show
            loadingProgress.isVisible = show
            loadingMessage.apply {
                isVisible = show
                text = message
            }

            // Deshabilitar interacción con los botones durante la carga
            userManagementCard.isEnabled = !show
            databaseManagementCard.isEnabled = !show
            importDataCard.isEnabled = !show
            buttonLogout.isEnabled = !show
        }
    }
    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro que deseas cerrar sesión?")
            .setPositiveButton("Sí") { _, _ ->
                logout()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun logout() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                showLoading(true, "Cerrando sesión...")
                authManager.signOut()
                    .onSuccess {
                        findNavController().navigate(R.id.action_configFragment_to_loginFragment)
                    }
                    .onFailure { e ->
                        showToastSafely("Error al cerrar sesión: ${e.message}")
                    }
            } finally {
                showLoading(false)
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }
        try {
            pickFileLauncher.launch(intent)
        } catch (e: Exception) {
            showToastSafely("Error al abrir el selector de archivos: ${e.message}")
        }
    }

    private fun showImportResult(result: ImportResult) {
        val message = buildString {
            append("Registros importados: ${result.importedItems.size}\n")
            append("Registros duplicados (no importados): ${result.duplicateItems.size}")
        }
        showToastSafely(message)
    }

    private fun showToastSafely(message: String) {
        if (isAdded && context != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}