package com.example.barcodescanner.ui.fragments
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.barcodescanner.data.ProductDatabase
import com.example.barcodescanner.databinding.FragmentAddProductBinding
import com.example.barcodescanner.entity.Product
import kotlinx.coroutines.launch

class AddProductFragment : Fragment() {
    private var _binding: FragmentAddProductBinding? = null
    private val binding get() = _binding!!
    private val args: AddProductFragmentArgs by navArgs()
    private lateinit var productDatabase: ProductDatabase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddProductBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        productDatabase = ProductDatabase(requireContext())

        // Mostrar el código de barras escaneado
        binding.barcodeTextView.text = "Código de barras: ${args.barcode}"

        // Configurar el botón de guardar
        binding.saveButton.setOnClickListener {
            val sku = binding.skuEditText.text.toString()
            val description = binding.descriptionEditText.text.toString()
            if (sku.isNotEmpty() && description.isNotEmpty()) {
                lifecycleScope.launch {
                    val newProduct = Product(args.barcode, sku, description)
                    productDatabase.insertProduct(newProduct)

                    // Mostrar mensaje de confirmación
                    Toast.makeText(requireContext(), "Producto agregado correctamente", Toast.LENGTH_SHORT).show()

                    // Navegar de vuelta al RegisterFragment
                    val action = AddProductFragmentDirections.actionAddProductFragmentToRegisterFragment(args.barcode)
                    findNavController().navigate(action)
                }
            } else {
                Toast.makeText(requireContext(), "Por favor, complete todos los campos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
