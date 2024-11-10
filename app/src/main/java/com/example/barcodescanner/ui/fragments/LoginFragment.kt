package com.example.barcodescanner.ui.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.barcodescanner.R
import com.example.barcodescanner.auth.StoreAuthManager
import com.example.barcodescanner.databinding.FragmentLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var storeAuthManager: StoreAuthManager

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account.idToken?.let { token ->
                    handleGoogleSignIn(token)
                }
            } catch (e: ApiException) {
                Toast.makeText(context, "Error de Google Sign In: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        storeAuthManager = StoreAuthManager(requireContext())

        if (storeAuthManager.isLoggedIn()) {
            navigateToMain()
            return
        }

        setupGoogleSignIn()
    }
    private fun setupGoogleSignIn() {
        binding.googleSignInButton.setOnClickListener {
            try {
                val signInIntent = storeAuthManager.signInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            } catch (e: Exception) {
                Log.e("LoginFragment", "Error en Google Sign In: ${e.message}", e)
                Toast.makeText(
                    context,
                    "Error al iniciar sesión con Google: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun handleGoogleSignIn(idToken: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = FirebaseAuth.getInstance().signInWithCredential(credential).await()
                val firebaseUser = authResult.user

                if (firebaseUser != null) {
                    val userExists = storeAuthManager.checkUserExists(firebaseUser.uid)

                    if (userExists) {
                        findNavController().navigate(R.id.action_loginFragment_to_mainFragment)
                    } else {
                        val directions = LoginFragmentDirections.actionLoginFragmentToStoreSetupFragment(
                            userEmail = firebaseUser.email ?: "",
                            userName = firebaseUser.displayName ?: ""
                        )
                        findNavController().navigate(directions)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun navigateToMain() {
        findNavController().navigate(R.id.action_loginFragment_to_mainFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}