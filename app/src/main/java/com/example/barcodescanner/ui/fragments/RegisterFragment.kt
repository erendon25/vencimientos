package com.example.barcodescanner.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.barcodescanner.AppUserManager
import com.example.barcodescanner.model.HistoryItem
import com.example.barcodescanner.HistoryManager
import com.example.barcodescanner.data.ProductDatabase
import com.example.barcodescanner.R
import com.example.barcodescanner.dao.ProductScanDao
import com.example.barcodescanner.ui.viewmodels.RegisterViewModel
import com.example.barcodescanner.databinding.FragmentRegisterBinding
import com.example.barcodescanner.entity.Product
import com.example.barcodescanner.model.ProductScanData
import com.example.barcodescanner.model.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RegisterFragment : Fragment() {

    // View binding
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private var isFormTouched = false  // Nueva variable para rastrear si el usuario ha interactuado con el formulario
    // Managers and databases
    private lateinit var historyManager: HistoryManager
    private lateinit var productDatabase: ProductDatabase
    private lateinit var appUserManager: AppUserManager

    // Navigation arguments
    private val args: RegisterFragmentArgs by navArgs()

    // ViewModel
    private val viewModel: RegisterViewModel by viewModels()

    // User adapter for spinner
    private lateinit var userAdapter: ArrayAdapter<User>

    // Fragment lifecycle methods
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize managers and databases
        historyManager = HistoryManager(requireContext())
        productDatabase = ProductDatabase(requireContext())
        appUserManager = AppUserManager(requireContext())

        setupUI()
        observeViewModel()
        observeUsers()
        // Asegurarse de que la botonera permanezca visible

        // Handle barcode argument if present
        args.barcode?.let { barcode ->
            if (barcode.isNotEmpty()) {
                binding.barcodeEditText.setText(barcode)
                updateProductInfo(barcode)
            }
        }
    }

    // Setup UI components
    private fun setupUI() {
        setupUserSpinner()
        setupBarcodeField()
        setupSearchButton()
        setupSaveButton()
        setupDatePicker()
        setupClearButton()
        setupInputListeners()
    }
    private fun disableDefaultSelection(parent: AdapterView<*>, position: Int) {
        if (position == 0) {  // Posición de "Seleccionar usuario"
            parent.setSelection(lastValidPosition)
            Toast.makeText(context, "Por favor seleccione un usuario válido", Toast.LENGTH_SHORT).show()
        } else {
            lastValidPosition = position
        }
    }

    private var lastValidPosition = 0  // Variable para mantener la última selección válida

    private fun setupInputListeners() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormTouched) {
                    validateInput(showErrors = false)  // Solo validamos sin mostrar errores
                }
            }
        }

        binding.apply {
            barcodeEditText.addTextChangedListener(textWatcher)
            expirationDateTextView.addTextChangedListener(textWatcher)
            quantityEditText.addTextChangedListener(textWatcher)
            withdrawalDaysEditText.addTextChangedListener(textWatcher)

            // Marcar el formulario como tocado cuando el usuario interactúa con cualquier campo
            arrayOf(
                barcodeEditText,
                expirationDateTextView,
                quantityEditText,
                withdrawalDaysEditText
            ).forEach { view ->
                view.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) isFormTouched = true
                }
            }
        }
    }

    // Observe ViewModel for user selection changes
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedUser.collectLatest { user ->
                updateSelectedUserUI(user)
                validateInput()
            }
        }
    }

    // Observe users from AppUserManager
    private fun observeUsers() {
        viewLifecycleOwner.lifecycleScope.launch {
            appUserManager.getUsers().collectLatest { users ->
                updateUserAdapter(users)
            }
        }
    }

    private fun setupUserSpinner() {
        userAdapter = object : ArrayAdapter<User>(
            requireContext(),
            android.R.layout.simple_spinner_item,  // Usar layout predefinido de Android
            mutableListOf()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = (convertView ?: LayoutInflater.from(context)
                    .inflate(android.R.layout.simple_spinner_item, parent, false))
                    .apply {
                        findViewById<TextView>(android.R.id.text1)?.apply {
                            val user = getItem(position)
                            text = user?.name
                            setTextColor(
                                if (user?.id == -1)
                                    ContextCompat.getColor(context, android.R.color.darker_gray)
                                else
                                    ContextCompat.getColor(context, android.R.color.black)
                            )
                        }
                    }
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = (convertView ?: LayoutInflater.from(context)
                    .inflate(android.R.layout.simple_spinner_dropdown_item, parent, false))
                    .apply {
                        findViewById<TextView>(android.R.id.text1)?.apply {
                            val user = getItem(position)
                            text = user?.name
                            isEnabled = user?.id != -1
                            setTextColor(
                                if (user?.id == -1)
                                    ContextCompat.getColor(context, android.R.color.darker_gray)
                                else
                                    ContextCompat.getColor(context, android.R.color.black)
                            )
                        }
                    }
                return view
            }
        }
        userAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.userSpinner.adapter = userAdapter

        // Agregar el usuario por defecto y configurar el listener
        userAdapter.add(User(-1, "Seleccionar usuario"))

        binding.userSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedUser = userAdapter.getItem(position)
                if (selectedUser?.id == -1 && isFormTouched) {
                    // Solo mostrar mensaje si el formulario ha sido tocado
                    Toast.makeText(context, "Por favor seleccione un usuario válido", Toast.LENGTH_SHORT).show()
                } else {
                    selectedUser?.let { viewModel.selectUser(it) }
                }
                validateInput(showErrors = false)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                viewModel.clearSelectedUser()
                validateInput(showErrors = false)
            }
        }
    }

    // Update user adapter with new user list
    private fun updateUserAdapter(users: List<User>) {
        userAdapter.clear()
        userAdapter.add(User(name = "Seleccionar usuario")) // Remove id parameter
        userAdapter.addAll(users)
        userAdapter.notifyDataSetChanged()
    }

    // Update UI to reflect selected user
    private fun updateSelectedUserUI(user: User?) {
        user?.let {
            val position = userAdapter.getPosition(it)
            if (position != -1) {
                binding.userSpinner.setSelection(position)
            }
            binding.selectedUserTextView.text = "Usuario seleccionado: ${it.name}"
        } ?: run {
            binding.userSpinner.setSelection(0)
            binding.selectedUserTextView.text = "Usuario seleccionado: Ninguno"
        }
    }

    // Setup barcode input field
    private fun setupBarcodeField() {
        binding.barcodeEditText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
    }

    // Setup search button
    private fun setupSearchButton() {
        binding.searchButton.setOnClickListener {
            val barcode = binding.barcodeEditText.text.toString()
            if (barcode.isNotEmpty()) {
                updateProductInfo(barcode)
            } else {
                Toast.makeText(context, "Por favor, ingrese un código de barras", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Update product info based on barcode
    private fun updateProductInfo(barcode: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val product = productDatabase.getProduct(barcode)
                if (product != null) {
                    updateUI(product)
                } else {
                    val defaultProduct = Product(
                        barcode = barcode,
                        sku = "N/A",
                        description = "N/A"
                    )
                    updateUI(defaultProduct)
                    binding.addProductButton.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener producto: ${e.message}")
                Toast.makeText(requireContext(), "Error al obtener información del producto", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // Update UI with product information
    private fun updateUI(product: Product) {
        binding.skuTextView.text = "SKU: ${product.sku}"
        binding.descriptionTextView.text = "Descripción: ${product.description}"

        if (product.sku != "N/A" && product.description != "N/A") {
            binding.addProductButton.visibility = View.GONE
        } else {
            binding.addProductButton.visibility = View.VISIBLE
            binding.addProductButton.setOnClickListener {
                val action = RegisterFragmentDirections.actionRegisterFragmentToAddProductFragment(product.barcode)
                findNavController().navigate(action)
            }
        }
        validateInput()
    }


    private fun setupSaveButton() {
        binding.saveButton.setOnClickListener {
            isFormTouched = true  // Marcar como tocado cuando intenta guardar
            if (validateInput(showErrors = true)) {  // Mostrar errores solo al intentar guardar
                saveData()
            }
        }
    }

    // Setup date picker
    private fun setupDatePicker() {
        binding.expirationDateTextView.inputType = android.text.InputType.TYPE_NULL
        binding.expirationDateTextView.setOnClickListener { showDatePickerDialog() }
    }

    // Setup clear button
    private fun setupClearButton() {
        binding.clearButton.setOnClickListener {
            clearForm()
            Toast.makeText(requireContext(), "Campos limpiados", Toast.LENGTH_SHORT).show()
        }
    }

    // Show date picker dialog
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = android.app.DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth)
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val formattedDate = dateFormat.format(selectedDate.time)
                binding.expirationDateTextView.setText(formattedDate)
                validateInput()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    // Save data to history manager

    private fun saveData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val barcode = binding.barcodeEditText.text.toString()
                if (historyManager.isItemAlreadyRegistered(barcode, binding.expirationDateTextView.text.toString())) {
                    Toast.makeText(requireContext(), "Producto ya registrado", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val product = productDatabase.getProduct(barcode)
                val selectedUser = viewModel.selectedUser.value ?: return@launch

                // Crear el registro local
                val newItem = HistoryItem(
                    id = 0, // SQLite generará el ID
                    barcode = barcode,
                    quantity = binding.quantityEditText.text.toString().toIntOrNull() ?: 0,
                    expirationDate = binding.expirationDateTextView.text.toString(),
                    withdrawalDays = binding.withdrawalDaysEditText.text.toString().toIntOrNull() ?: 0,
                    withdrawalDate = calculateWithdrawalDate(
                        binding.expirationDateTextView.text.toString(),
                        binding.withdrawalDaysEditText.text.toString().toIntOrNull() ?: 0
                    ),
                    user = selectedUser,
                    sku = product?.sku ?: "N/A",
                    description = product?.description ?: "N/A",
                    scanDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                )

                // Guardar en el historial local
                val localId = historyManager.saveItem(newItem)


                // Crear y guardar el escaneo en Firebase
                val productScanDao = ProductScanDao(requireContext())
                // Primero calculamos la fecha de retiro
                val expirationDate = binding.expirationDateTextView.text.toString()
                val withdrawalDays = binding.withdrawalDaysEditText.text.toString().toIntOrNull() ?: 0
                // Calcular la fecha de retiro usando el formato correcto
                val withdrawalDate = try {
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val expDate = sdf.parse(expirationDate)
                    val calendar = Calendar.getInstance()
                    calendar.time = expDate
                    calendar.add(Calendar.DAY_OF_MONTH, -withdrawalDays)
                    sdf.format(calendar.time)
                } catch (e: Exception) {
                    ""
                }

                val scanData = ProductScanData(
                    sku = product?.sku ?: "N/A",
                    description = product?.description ?: "N/A",
                    expirationDate = binding.expirationDateTextView.text.toString(),
                    quantity = binding.quantityEditText.text.toString().toIntOrNull() ?: 0,
                    withdrawalDays = binding.withdrawalDaysEditText.text.toString().toIntOrNull() ?: 0,
                    withdrawalDate = withdrawalDate,
                    scanDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                    storeName = selectedUser.name, // Asumiendo que el nombre de la tienda está en el usuario seleccionado
                    scannerName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Usuario desconocido"
                )

                productScanDao.saveProductScan(barcode, scanData).onSuccess {
                    Toast.makeText(requireContext(), "Producto Registrado", Toast.LENGTH_SHORT).show()
                    clearForm()
                    findNavController().navigate(R.id.action_registerFragment_to_scanFragment)
                }.onFailure { e ->
                    Toast.makeText(requireContext(), "Error al guardar en la nube: ${e.message}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error al guardar datos", e)
                Toast.makeText(requireContext(), "Error al guardar los datos: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    // Calculate withdrawal date based on expiration date and withdrawal days
    private fun calculateWithdrawalDate(expirationDate: String, withdrawalDays: Int): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val expDate = sdf.parse(expirationDate)
        val calendar = Calendar.getInstance()
        calendar.time = expDate
        calendar.add(Calendar.DAY_OF_MONTH, -withdrawalDays)
        return sdf.format(calendar.time)
    }

    private fun validateInput(showErrors: Boolean = false): Boolean {
        val barcode = binding.barcodeEditText.text.toString()
        val expirationDate = binding.expirationDateTextView.text.toString()
        val quantity = binding.quantityEditText.text.toString()
        val withdrawalDays = binding.withdrawalDaysEditText.text.toString()
        val selectedUser = viewModel.selectedUser.value

        val isValid = barcode.isNotEmpty() &&
                expirationDate.isNotEmpty() &&
                quantity.isNotEmpty() &&
                withdrawalDays.isNotEmpty() &&
                selectedUser?.isValid() == true

        if (!isValid && showErrors) {
            // Solo mostrar errores si showErrors es true
            when {
                barcode.isEmpty() -> showError("Ingrese un código de barras")
                expirationDate.isEmpty() -> showError("Seleccione una fecha de vencimiento")
                quantity.isEmpty() -> showError("Ingrese una cantidad")
                withdrawalDays.isEmpty() -> showError("Ingrese los días de retiro")
                selectedUser?.isValid() != true -> showError("Seleccione un usuario válido")
            }
        }

        binding.saveButton.isEnabled = isValid
        return isValid
    }

    private fun showError(message: String) {
        if (isVisible && isFormTouched) {  // Solo mostrar errores si el fragmento está visible
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }


    // Clear form fields
    private fun clearForm() {
        isFormTouched = false  // Resetear el estado de interacción
        binding.apply {
            barcodeEditText.text.clear()
            expirationDateTextView.setText("")
            quantityEditText.text.clear()
            skuTextView.text = "SKU: N/A"
            descriptionTextView.text = "Descripción: N/A"
            addProductButton.visibility = View.GONE
            withdrawalDaysEditText.text.clear()
            userSpinner.setSelection(0)
        }
        viewModel.clearSelectedUser()
        validateInput(showErrors = false)
        // Add a button to return to the scan screen
        binding.returnToScanButton.visibility = View.VISIBLE
        binding.returnToScanButton.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_scanFragment)
        }
    }
    override fun onPause() {
        super.onPause()
        // Desactivar la validación cuando el fragmento no está visible
        isFormTouched = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "RegisterFragment"
    }
}