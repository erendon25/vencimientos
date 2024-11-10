package com.example.barcodescanner.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.barcodescanner.R
import com.example.barcodescanner.auth.StoreAuthManager
import com.example.barcodescanner.databinding.FragmentSplashBinding
import com.google.android.datatransport.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashFragment : Fragment() {
    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!
    private lateinit var storeAuthManager: StoreAuthManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        storeAuthManager = StoreAuthManager(requireContext())
        binding.versionTextView.text = getString(R.string.version_text, BuildConfig.VERSION_NAME)
        checkAuthAndNavigate()
    }

    private fun checkAuthAndNavigate() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                delay(1500) // Mostrar splash por 1.5 segundos

                if (storeAuthManager.isLoggedIn()) {
                    findNavController().navigate(R.id.mainFragment)
                } else {
                    findNavController().navigate(R.id.loginFragment)
                }
            } catch (e: Exception) {
                Log.e("SplashFragment", "Error during navigation: ${e.message}", e)
                // Si hay un error, navegar al login
                findNavController().navigate(R.id.loginFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}