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
    private val productScanDao = ProductScanDao(context, historyManager)
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
                Log.d("HistoryViewModel", "Iniciando actualización del item: ${item.description}")

                // Verificar existencia local primero
                val localItem = historyManager.getItemById(item.id)
                if (localItem == null) {
                    _error.value = "Error: Item no encontrado localmente"
                    _updateSuccess.value = UpdateState.ERROR
                    return@launch
                }

                // Usar el firebaseId en lugar del id local
                val firebaseId = localItem.firebaseId
                if (firebaseId.isNullOrEmpty()) {
                    _error.value = "Error: Item no tiene ID de Firebase asociado"
                    _updateSuccess.value = UpdateState.ERROR
                    return@launch
                }
                // Verificar existencia en Firebase
                if (!productScanDao.checkScanExists(firebaseId)) {
                    _error.value = "Error: Documento no encontrado en Firebase"
                    _updateSuccess.value = UpdateState.ERROR
                    return@launch
                }

                // Actualizar en Firebase primero
                val result = productScanDao.updateScan(
                    scanId = firebaseId,
                    expirationDate = item.expirationDate,
                    quantity = item.quantity,
                    withdrawalDays = item.withdrawalDays,
                    sku = localItem.sku ?: "",
                    description = localItem.description ?: ""
                )

                result.fold(
                    onSuccess = {
                        // Actualizar localmente con el firebaseId
                        val withdrawalDate = calculateWithdrawalDate(item.expirationDate, item.withdrawalDays)
                        historyManager.updateItemById(
                            id = item.id,
                            quantity = item.quantity,
                            expirationDate = item.expirationDate,
                            withdrawalDays = item.withdrawalDays,
                            withdrawalDate = withdrawalDate,
                            firebaseId = firebaseId  // Pasar el firebaseId existente
                        )

                        Log.d("HistoryViewModel", "Actualización en Firebase y local exitosa")
                        _error.value = "Actualización exitosa"
                        _updateSuccess.value = UpdateState.SUCCESS
                        loadItems()
                    },
                    onFailure = { exception ->
                        Log.e("HistoryViewModel", "Error en actualización de Firebase", exception)
                        _error.value = "Error al actualizar en Firebase: ${exception.message}"
                        _updateSuccess.value = UpdateState.ERROR
                    }
                )
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Error general en actualización", e)
                _error.value = "Error general: ${e.message}"
                _updateSuccess.value = UpdateState.ERROR
            } finally {
                _isLoading.value = false
            }
        }
    }
    private fun calculateWithdrawalDate(expirationDate: String, withdrawalDays: Int): String {
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val expDate = sdf.parse(expirationDate) ?: return ""
            val calendar = Calendar.getInstance()
            calendar.time = expDate
            calendar.add(Calendar.DAY_OF_MONTH, -withdrawalDays)
            sdf.format(calendar.time)
        } catch (e: Exception) {
            Log.e("HistoryViewModel", "Error calculando fecha de retiro", e)
            ""
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
