package com.example.barcodescanner.ui.activities

import com.example.barcodescanner.model.User
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodescanner.AppUserManager
import com.example.barcodescanner.R
import com.example.barcodescanner.ui.adapters.UserAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class UserManagementActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserAdapter
    private lateinit var appUserManager: AppUserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_management)

        appUserManager = AppUserManager(this) // Inicializa AppUserManager
        lifecycleScope.launch {
            appUserManager.getUsers().collect { users ->
                adapter.submitList(users)
                setupSwipeToDelete(users)
            }
        }


        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = UserAdapter()
        recyclerView.adapter = adapter

        val fab: FloatingActionButton = findViewById(R.id.fabAddUser)
        fab.setOnClickListener { showAddUserDialog() }


        updateUserList() // Carga la lista de usuarios al iniciar
    }

    private fun showAddUserDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_user, null)
        val editTextUsername = dialogView.findViewById<EditText>(R.id.editTextUsername)

        AlertDialog.Builder(this)
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
        val user = User(name = username) // Remove id parameter
        lifecycleScope.launch {
            appUserManager.addUser(user)
        }
    }

    private fun updateUserList() {
        lifecycleScope.launch {  // Lanzar una corrutina
            appUserManager.getUsers().collect { users ->  // Recolectar valores del Flow
                adapter.submitList(users)
            }
        }
    }

    private fun setupSwipeToDelete(users: List<User>) {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedUser = users[position]

                AlertDialog.Builder(this@UserManagementActivity)
                    .setTitle("Confirmar eliminación")
                    .setMessage("¿Estás seguro de que deseas eliminar a ${deletedUser.name}?")
                    .setPositiveButton("Sí") { _, _ ->
                        appUserManager.removeUser(deletedUser)
                        updateUserList()
                    }
                    .setNegativeButton("No") { _, _ ->
                        adapter.notifyItemChanged(position)
                    }
                    .show()
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}