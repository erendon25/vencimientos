package com.example.barcodescanner.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodescanner.AppUserManager
import com.example.barcodescanner.R
import com.example.barcodescanner.ui.adapters.UserAdapter
import com.example.barcodescanner.databinding.FragmentUserManagementBinding
import com.example.barcodescanner.model.User
import kotlinx.coroutines.launch

class UserManagementFragment : Fragment() {
    private var _binding: FragmentUserManagementBinding? = null
    private val binding get() = _binding!!
    private lateinit var appUserManager: AppUserManager
    private lateinit var adapter: UserAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        appUserManager = AppUserManager(requireContext())

        lifecycleScope.launch {
            appUserManager.getUsers().collect { users ->
                adapter.submitList(users)
                setupSwipeToDelete(users)
            }
        }

        setupAddUserButton()
    }
    private fun setupRecyclerView() {
        adapter = UserAdapter()
        binding.recyclerViewUsers.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@UserManagementFragment.adapter  // Corregido aquí
        }
    }


    private fun setupAddUserButton() {
        binding.fabAddUser.setOnClickListener {
            showAddUserDialog()
        }
    }

    private fun showAddUserDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_user, null)
        val editTextUsername = dialogView.findViewById<EditText>(R.id.editTextUsername)

        AlertDialog.Builder(requireContext())
            .setTitle("Agregar Usuario")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val username = editTextUsername.text.toString()
                if (username.isNotBlank()) {
                    addUser(username)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun addUser(username: String) {
        val user = User(name = username)
        lifecycleScope.launch {
            appUserManager.addUser(user)
        }
    }

    private fun setupSwipeToDelete(users: List<User>) {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position >= 0 && position < users.size) {
                    val deletedUser = users[position]

                    AlertDialog.Builder(requireContext())
                        .setTitle("Confirmar eliminación")
                        .setMessage("¿Estás seguro de que deseas eliminar a ${deletedUser.name}?")
                        .setPositiveButton("Sí") { _, _ ->
                            lifecycleScope.launch {
                                appUserManager.removeUser(deletedUser)
                            }
                        }
                        .setNegativeButton("No") { _, _ ->
                            adapter.notifyItemChanged(position)
                        }
                        .show()
                }
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewUsers)
    }

    // El resto de los métodos (setupRecyclerView, setupAddUserButton, showAddUserDialog, addUser, setupSwipeToDelete)
    // permanecen iguales que en el ConfigFragment original

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}