package com.example.barcodescanner.ui.fragments

import android.app.DatePickerDialog
import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barcodescanner.databinding.FragmentWithdrawalScheduleBinding
import com.example.barcodescanner.HistoryManager
import com.example.barcodescanner.model.HistoryItem
import com.example.barcodescanner.ui.adapters.WithdrawalScheduleAdapter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class WithdrawalScheduleFragment : Fragment() {
    private var _binding: FragmentWithdrawalScheduleBinding? = null
    private val binding get() = _binding!!
    private lateinit var historyManager: HistoryManager
    private lateinit var adapter: WithdrawalScheduleAdapter
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var currentDate = Calendar.getInstance()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWithdrawalScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        historyManager = HistoryManager(requireContext())
        setupRecyclerView()
        setupDateNavigation()
        loadWithdrawalItems(Calendar.getInstance().time)
        deleteOldItems()
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "setupRecyclerView: Configurando RecyclerView")
        adapter = WithdrawalScheduleAdapter()
        binding.withdrawalRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WithdrawalScheduleFragment.adapter
        }
    }

    private fun setupDateNavigation() {
        updateDateDisplay()

        binding.previousDateButton.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_MONTH, -1)
            updateDateDisplay()
            loadWithdrawalItems(currentDate.time)
        }

        binding.nextDateButton.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_MONTH, 1)
            updateDateDisplay()
            loadWithdrawalItems(currentDate.time)
        }

        binding.dateDisplay.setOnClickListener {
            showDatePickerDialog()
        }
    }

    private fun updateDateDisplay() {
        binding.dateDisplay.text = dateFormat.format(currentDate.time)
    }

    private fun showDatePickerDialog() {
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                currentDate.set(year, month, dayOfMonth)
                updateDateDisplay()
                loadWithdrawalItems(currentDate.time)
            },
            currentDate.get(Calendar.YEAR),
            currentDate.get(Calendar.MONTH),
            currentDate.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun loadWithdrawalItems(date: Date) {
        Log.d(TAG, "loadWithdrawalItems: Cargando items para la fecha ${dateFormat.format(date)}")
        lifecycleScope.launch {
            try {
                val items = historyManager.getItemsForWithdrawalDate(date)
                Log.d(TAG, "loadWithdrawalItems: Se encontraron ${items.size} items para retirar")
                adapter.submitList(items)
                updateWithdrawalInfo(items)
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar los items de retiro", e)
            }
        }
    }

    private fun updateWithdrawalInfo(items: List<HistoryItem>) {
        val itemCount = items.size
        val infoText = when {
            itemCount == 0 -> "No hay productos para retirar hoy"
            itemCount == 1 -> "1 producto para retirar hoy"
            else -> "$itemCount productos para retirar hoy"
        }
        binding.withdrawalInfoText.text = infoText
    }

    private fun deleteOldItems() {
        lifecycleScope.launch {
            val thirtyDaysAgo = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -30)
            }.time
            historyManager.deleteItemsOlderThan(thirtyDaysAgo)
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}