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
import com.example.barcodescanner.R
import com.example.barcodescanner.auth.StoreAuthManager
import com.example.barcodescanner.databinding.FragmentStoreSetupBinding
import kotlinx.coroutines.launch

class StoreSetupFragment : Fragment() {
    private var _binding: FragmentStoreSetupBinding? = null
    private val binding get() = _binding!!
    private lateinit var storeAuthManager: StoreAuthManager
    private val args: StoreSetupFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoreSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        storeAuthManager = StoreAuthManager(requireContext())

        // Pre-llenar el email si está disponible
        binding.userEmailText.text = "Email: ${args.userEmail}"

        setupSaveButton()
    }
    private fun setupSaveButton() {
        binding.saveButton.setOnClickListener {
            if (validateInputs()) {
                saveStoreData()
            }
        }
    }


    private fun validateInputs(): Boolean {
        val storeName = binding.storeNameInput.text.toString()
        val storeCode = binding.storeCodeInput.text.toString()

        if (storeName.isBlank()) {
            binding.storeNameInput.error = "Ingrese el nombre de la tienda"
            return false
        }

        if (storeCode.isBlank()) {
            binding.storeCodeInput.error = "Ingrese el código de la tienda"
            return false
        }

        return true
    }

    private fun saveStoreData() {
        val storeName = binding.storeNameInput.text.toString()
        val storeCode = binding.storeCodeInput.text.toString()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val isAdmin = args.userEmail == "erickrendon18@gmail.com"
                val result = storeAuthManager.createNewUser(
                    email = args.userEmail,
                    name = args.userName,
                    storeId = storeCode,
                    storeName = storeName,
                    isAdmin = isAdmin
                )

                if (result.isSuccess) {
                    findNavController().navigate(R.id.action_storeSetupFragment_to_mainFragment)
                } else {
                    showError("Error al guardar los datos: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                showError("Error: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}