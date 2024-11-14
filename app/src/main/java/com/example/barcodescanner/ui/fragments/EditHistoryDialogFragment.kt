package com.example.barcodescanner.ui.fragments

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.barcodescanner.R
import com.example.barcodescanner.model.HistoryItem
import com.example.barcodescanner.ui.viewmodels.HistoryViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class EditHistoryDialogFragment : DialogFragment() {
    private lateinit var item: HistoryItem
    private lateinit var viewModel: HistoryViewModel
    private lateinit var dialogContent: View
    private lateinit var loadingView: View
    private lateinit var progressBar: View
    private lateinit var contentView: View

    interface OnItemUpdateListener {
        fun onItemUpdated(updatedItem: HistoryItem)
    }

    private var itemUpdateListener: OnItemUpdateListener? = null

    fun setItemUpdateListener(listener: OnItemUpdateListener) {
        itemUpdateListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireParentFragment())[HistoryViewModel::class.java]

        @Suppress("DEPRECATION")
        item = arguments?.getSerializable(ARG_ITEM) as? HistoryItem
            ?: throw IllegalStateException("Item no encontrado")
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let { activity ->
            val builder = AlertDialog.Builder(activity)
            val view = layoutInflater.inflate(R.layout.dialog_edit_history, null)

            setupProgressViews(view)
            setupViews(view)

            builder.setView(view)
                .setTitle("Editar Registro")
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar") { _, _ -> dismiss() }

            val dialog = builder.create()

            dialog.setOnShowListener { dialogInterface ->
                val positiveButton = (dialogInterface as AlertDialog)
                    .getButton(AlertDialog.BUTTON_POSITIVE)

                positiveButton.setOnClickListener {
                    if (validateInput(view)) {
                        updateItem(view)
                    }
                }
            }

            dialog
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.updateSuccess.collect { state ->
                when (state) {
                    HistoryViewModel.UpdateState.SUCCESS -> {
                        hideProgress()
                        showToast("Actualización exitosa")
                        dismiss()
                        viewModel.resetUpdateStatus()
                    }
                    HistoryViewModel.UpdateState.ERROR -> {
                        hideProgress()
                        viewModel.error.value?.let { showToast(it) }
                        viewModel.resetUpdateStatus()
                    }
                    HistoryViewModel.UpdateState.IDLE -> {
                        // Estado inicial o después de reset
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                if (isLoading) showProgress() else hideProgress()
            }
        }
    }

    private fun setupViews(view: View) {
        val editTextQuantity = view.findViewById<EditText>(R.id.quantityEditText)
        val editTextExpirationDate = view.findViewById<EditText>(R.id.expirationDateEditText)
        val editTextWithdrawalDays = view.findViewById<EditText>(R.id.withdrawalDaysEditText)
        val productInfoText = view.findViewById<TextView>(R.id.productInfoText)
        val withdrawalDateText = view.findViewById<TextView>(R.id.withdrawalDateText)

        editTextQuantity.setText(item.quantity.toString())
        editTextExpirationDate.setText(item.expirationDate)
        editTextWithdrawalDays.setText(item.withdrawalDays.toString())
        productInfoText.text = "Producto: ${item.description}\nCódigo: ${item.barcode}"

        updateWithdrawalDateDisplay(
            editTextExpirationDate.text.toString(),
            editTextWithdrawalDays.text.toString().toIntOrNull() ?: 0,
            withdrawalDateText
        )

        setupDatePicker(editTextExpirationDate)
        setupNumericFilters(editTextQuantity, editTextWithdrawalDays)
        setupWithdrawalDateUpdate(view)
    }

    private fun setupWithdrawalDateUpdate(view: View) {
        val editTextExpirationDate = view.findViewById<EditText>(R.id.expirationDateEditText)
        val editTextWithdrawalDays = view.findViewById<EditText>(R.id.withdrawalDaysEditText)
        val withdrawalDateText = view.findViewById<TextView>(R.id.withdrawalDateText)

        val updateWithdrawalDate = {
            val expirationDate = editTextExpirationDate.text.toString()
            val withdrawalDays = editTextWithdrawalDays.text.toString().toIntOrNull() ?: 0
            updateWithdrawalDateDisplay(expirationDate, withdrawalDays, withdrawalDateText)
        }

        editTextExpirationDate.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateWithdrawalDate()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        editTextWithdrawalDays.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateWithdrawalDate()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun updateItem(view: View) {
        if (!validateInput(view)) return

        val quantity = view.findViewById<EditText>(R.id.quantityEditText)
            .text.toString().toIntOrNull() ?: return
        val expirationDate = view.findViewById<EditText>(R.id.expirationDateEditText)
            .text.toString()
        val withdrawalDays = view.findViewById<EditText>(R.id.withdrawalDaysEditText)
            .text.toString().toIntOrNull() ?: return

        val updatedItem = item.copy(
            id = item.id,
            firebaseId = item.firebaseId,
            barcode = item.barcode,
            sku = item.sku,
            description = item.description,
            quantity = quantity,
            expirationDate = expirationDate,
            withdrawalDays = withdrawalDays,
            withdrawalDate = calculateWithdrawalDate(expirationDate, withdrawalDays),
            user = item.user,
            scanDate = item.scanDate
        )

        Log.d("EditHistoryDialogFragment", "Firebase ID del item a actualizar: ${updatedItem.firebaseId}")

        val result = Bundle().apply {
            putSerializable("updatedItem", updatedItem)
        }
        parentFragmentManager.setFragmentResult("itemUpdate", result)
        dismiss()
    }

    private fun setupProgressViews(view: View) {
        dialogContent = view.findViewById(R.id.dialogContent)
        loadingView = view.findViewById(R.id.loadingView)
        progressBar = view.findViewById(R.id.progressBar)
        contentView = view.findViewById(R.id.contentView)
    }

    private fun showProgress() {
        dialog?.let { dialog ->
            if (dialog is AlertDialog) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = false
            }
        }
        contentView.visibility = View.GONE
        loadingView.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
    }

    private fun hideProgress() {
        dialog?.let { dialog ->
            if (dialog is AlertDialog) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = true
            }
        }
        loadingView.visibility = View.GONE
        progressBar.visibility = View.GONE
        contentView.visibility = View.VISIBLE
    }

    private fun updateWithdrawalDateDisplay(expirationDate: String, withdrawalDays: Int, textView: TextView) {
        if (expirationDate.isNotEmpty() && withdrawalDays > 0) {
            val withdrawalDate = calculateWithdrawalDate(expirationDate, withdrawalDays)
            textView.text = "Fecha de retiro: $withdrawalDate"
            textView.visibility = View.VISIBLE
        } else {
            textView.visibility = View.GONE
        }
    }

    private fun setupDatePicker(editTextDate: EditText) {
        editTextDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            try {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val date = sdf.parse(editTextDate.text.toString())
                if (date != null) {
                    calendar.time = date
                }
            } catch (e: Exception) {
                // Use current date if parsing fails
            }

            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val selectedDate = String.format("%02d/%02d/%04d", day, month + 1, year)
                    editTextDate.setText(selectedDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun setupNumericFilters(vararg editTexts: EditText) {
        val numericFilter = InputFilter { source, _, end, _, _, _ ->
            for (i in 0 until end) {
                if (!Character.isDigit(source[i])) {
                    return@InputFilter ""
                }
            }
            null
        }
        editTexts.forEach { it.filters = arrayOf(numericFilter) }
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
            ""
        }
    }

    private fun validateInput(view: View): Boolean {
        val quantity = view.findViewById<EditText>(R.id.quantityEditText)
            .text.toString().toIntOrNull()
        val expirationDate = view.findViewById<EditText>(R.id.expirationDateEditText)
            .text.toString()
        val withdrawalDays = view.findViewById<EditText>(R.id.withdrawalDaysEditText)
            .text.toString().toIntOrNull()

        when {
            quantity == null || quantity <= 0 -> {
                showToast("La cantidad debe ser mayor a 0")
                return false
            }
            expirationDate.isBlank() -> {
                showToast("La fecha de vencimiento es requerida")
                return false
            }
            withdrawalDays == null || withdrawalDays < 0 -> {
                showToast("Los días de retiro no pueden ser negativos")
                return false
            }
        }
        return true
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }


    companion object {
        private const val ARG_ITEM = "item"

        fun newInstance(item: HistoryItem): EditHistoryDialogFragment {
            return EditHistoryDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_ITEM, item)
                }
            }
        }
    }
}