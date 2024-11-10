package com.example.barcodescanner.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.barcodescanner.HistoryManager
import com.example.barcodescanner.dao.ProductScanDao
import com.example.barcodescanner.model.HistoryItem
import com.example.barcodescanner.model.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryViewModel(private val context: Context) : ViewModel() {
    private val historyManager = HistoryManager(context)
    private val productScanDao = ProductScanDao(context)
    private val _historyItems = MutableStateFlow<List<HistoryItem>>(emptyList())
    val historyItems = _historyItems.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _updateSuccess = MutableStateFlow(UpdateState.IDLE)
    val updateSuccess = _updateSuccess.asStateFlow()
    fun loadItems() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val items = historyManager.getAllItems()
                _historyItems.value = items
                Log.d("HistoryViewModel", "Items cargados: ${items.size}")
            } catch (e: Exception) {
                _error.value = "Error al cargar el historial: ${e.message}"
                Log.e("HistoryViewModel", "Error cargando items", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    // Definir estados posibles de actualización
    enum class UpdateState {
        IDLE,
        SUCCESS,
        ERROR
    }
    fun updateItem(item: HistoryItem) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val documentExists = productScanDao.checkScanExists(item.id.toString())
                Log.d("HistoryViewModel", if (documentExists) "Actualizando documento existente" else "Creando nuevo documento")

                val result = if (!documentExists) {
                    productScanDao.createScan(
                        scanId = item.id.toString(),
                        barcode = item.barcode,
                        description = item.description,
                        expirationDate = item.expirationDate,
                        quantity = item.quantity,
                        withdrawalDays = item.withdrawalDays,
                        user = item.user
                    )
                } else {
                    productScanDao.updateScan(
                        scanId = item.id.toString(),
                        expirationDate = item.expirationDate,
                        quantity = item.quantity,
                        withdrawalDays = item.withdrawalDays
                    )
                }

                result.fold(
                    onSuccess = {
                        historyManager.updateItem(item)
                        _error.value = "Actualización exitosa"
                        _updateSuccess.value = UpdateState.SUCCESS
                        loadItems()
                    },
                    onFailure = { exception ->
                        _error.value = when {
                            exception.message?.contains("no autenticado") == true ->
                                "Usuario no autenticado"
                            exception.message?.contains("no asociado") == true ->
                                "Usuario no asociado a una tienda"
                            exception.message?.contains("permisos") == true ->
                                "No tienes permisos para editar este registro"
                            else -> "Error al actualizar: ${exception.message}"
                        }
                        _updateSuccess.value = UpdateState.ERROR
                    }
                )
            } catch (e: Exception) {
                _error.value = "Error general: ${e.message}"
                _updateSuccess.value = UpdateState.ERROR
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetUpdateStatus() {
        _updateSuccess.value = UpdateState.IDLE
        _error.value = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HistoryViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
