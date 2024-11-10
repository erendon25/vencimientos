package com.example.barcodescanner.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.barcodescanner.data.ProductDatabase
import com.example.barcodescanner.databinding.FragmentEditProductBinding
import com.example.barcodescanner.ui.viewmodels.EditProductViewModel
import kotlinx.coroutines.launch

class EditProductFragment : Fragment() {
    private var _binding: FragmentEditProductBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EditProductViewModel by viewModels()
    private lateinit var productDatabase: ProductDatabase
    private val args: EditProductFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProductBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        productDatabase = ProductDatabase(requireContext())

        setupViews()
        loadProduct()
        observeViewModel()
    }

    private fun setupViews() {
        binding.apply {
            barcodeEditText.isEnabled = true

            saveButton.setOnClickListener {
                updateProduct()
            }
        }
    }

    private fun loadProduct() {
        lifecycleScope.launch {
            try {
                val product = productDatabase.getProduct(args.barcode)
                product?.let {
                    binding.apply {
                        barcodeEditText.setText(it.barcode)
                        skuEditText.setText(it.sku)
                        descriptionEditText.setText(it.description)
                    }
                    viewModel.setProduct(it)
                } ?: run {
                    showToast("Producto no encontrado")
                    findNavController().navigateUp()
                }
            } catch (e: Exception) {
                showToast("Error al cargar el producto: ${e.message}")
                findNavController().navigateUp()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let { showToast(it) }
            }
        }

        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                binding.saveButton.isEnabled = !isLoading
            }
        }
    }

    private fun updateProduct() {
        val currentProduct = viewModel.product.value ?: return
        val newSku = binding.skuEditText.text.toString()
        val newDescription = binding.descriptionEditText.text.toString()

        if (newSku.isBlank() || newDescription.isBlank()) {
            showToast("Todos los campos son requeridos")
            return
        }

        lifecycleScope.launch {
            try {
                viewModel.updateProduct(
                    originalBarcode = currentProduct.barcode,
                    newBarcode = currentProduct.barcode,
                    sku = newSku,
                    description = newDescription,
                    productDatabase = productDatabase
                )
                showToast("Producto actualizado correctamente")
                findNavController().navigateUp()
            } catch (e: Exception) {
                showToast("Error al actualizar el producto: ${e.message}")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}