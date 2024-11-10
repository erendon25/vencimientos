package com.example.barcodescanner.ui.fragments

import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodescanner.data.ProductDatabase
import com.example.barcodescanner.R
import com.example.barcodescanner.auth.StoreAuthManager
import com.example.barcodescanner.databinding.FragmentDatabaseBinding
import com.example.barcodescanner.entity.Product
import com.example.barcodescanner.ui.adapters.ProductAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DatabaseFragment : Fragment() {
    private var _binding: FragmentDatabaseBinding? = null
    private val binding get() = _binding!!
    private lateinit var productDatabase: ProductDatabase
    private lateinit var adapter: ProductAdapter
    private var isDataLoaded = false
    private val searchQuery = MutableStateFlow("")
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        showErrorMessage("Error: ${throwable.message}")
    }
    private val authManager: StoreAuthManager by lazy { StoreAuthManager(requireContext()) }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDatabaseBinding.inflate(inflater, container, false)
        return binding.root
    }

    @OptIn(FlowPreview::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("DatabaseFragment", "Fragment created")
        productDatabase = ProductDatabase(requireContext())
        setupSearch()

        // Observar cambios en el usuario actual
        viewLifecycleOwner.lifecycleScope.launch {
            authManager.currentUser.collect { user ->
                Log.d("DatabaseFragment", "User updated: ${user?.email}, Admin: ${user?.admin}")
                setupRecyclerView()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch(coroutineExceptionHandler) {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                setupSearchFlow()
                if (!isDataLoaded) {
                    loadDataFromExcel()
                    isDataLoaded = true
                } else {
                    loadInitialData()
                }
            }
        }

        binding.uploadToFirebaseButton.setOnClickListener {
            uploadToFirebase()
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun setupSearchFlow() {
        searchQuery
            .debounce(300)
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .collect { query ->
                try {
                    if (query.isNotEmpty()) {
                        val searchResults = withContext(Dispatchers.IO) {
                            productDatabase.searchProducts(query)
                        }
                        _binding?.let { adapter.updateList(searchResults) }
                    } else {
                        loadInitialData()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showErrorMessage("Error en la búsqueda: ${e.message}")
                }
            }
    }

    private fun updateStatusText(message: String) {
        _binding?.statusText?.apply {
            text = message
            visibility = if (message.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun uploadToFirebase() {
        viewLifecycleOwner.lifecycleScope.launch(coroutineExceptionHandler) {
            try {
                showLoading(true)
                val localCount = productDatabase.getProductCount()
                val firebaseCount = productDatabase.getFirebaseProductCount()

                updateStatusText("Iniciando sincronización...\n" +
                        "Productos locales: $localCount\n" +
                        "Productos en Firebase: $firebaseCount")

                productDatabase.uploadProductsToFirebase()

                val finalFirebaseCount = productDatabase.getFirebaseProductCount()
                updateStatusText("Sincronización completada\n" +
                        "Productos locales: $localCount\n" +
                        "Productos en Firebase: $finalFirebaseCount")

                showSuccessMessage("Datos subidos exitosamente")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateStatusText("Error: ${e.message}")
                showErrorMessage("Error al subir datos: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        _binding?.apply {
            progressBar.isVisible = show
            uploadToFirebaseButton.isEnabled = !show
        }
    }

    private fun showSuccessMessage(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showErrorMessage(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun loadInitialData() {
        try {
            val initialProducts = withContext(Dispatchers.IO) {
                productDatabase.getProductsPaginated(1, 50)
            }
            _binding?.let { adapter.updateList(initialProducts) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showErrorMessage("Error al cargar datos iniciales: ${e.message}")
        }
    }

    private fun setupRecyclerView() {
        // Agrega logs para debug
        val currentUser = authManager.currentUser.value
        Log.d("DatabaseFragment", "Current User: ${currentUser?.email}, Admin: ${currentUser?.admin}")

        val isAdmin = currentUser?.admin == true
        Log.d("DatabaseFragment", "Is Admin: $isAdmin")

        adapter = ProductAdapter(
            products = mutableListOf(),
            isAdmin = isAdmin
        ) { product ->
            if (isAdmin) {
                findNavController().navigate(
                    DatabaseFragmentDirections.actionDatabaseFragmentToEditProductFragment(product.barcode)
                )
            }
        }

        binding.recyclerView.apply {
            this.adapter = this@DatabaseFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())

            // Si es admin, habilitar swipe to delete
            if (isAdmin) {
                ItemTouchHelper(setupSwipeToDelete()).attachToRecyclerView(this)
            }
        }
    }


    private suspend fun loadDataFromExcel() {
        try {
            showLoading(true)
            withContext(Dispatchers.IO) {
                val productCount = productDatabase.getProductCount()
                if (productCount == 0) {
                    productDatabase.loadFromExcelAsync("product_database.xlsx")
                }
            }
            loadInitialData()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showErrorMessage("Error al cargar datos: ${e.message}")
        } finally {
            showLoading(false)
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                if (view != null) {
                    searchQuery.value = newText.orEmpty()
                }
                return true
            }
        })
    }

    private fun setupSwipeToDelete(): ItemTouchHelper.SimpleCallback {
        return object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val product = adapter.getProductAt(position)

                val isUserAdmin = authManager.currentUser.value?.admin == true
                if (isUserAdmin) {
                    showDeleteConfirmation(product, position)
                } else {
                    adapter.notifyItemChanged(position)
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete)
                val background = ColorDrawable(android.graphics.Color.RED)

                val iconMargin = (itemView.height - icon?.intrinsicHeight!!) / 2
                val iconTop = itemView.top + (itemView.height - icon.intrinsicHeight) / 2
                val iconBottom = iconTop + icon.intrinsicHeight

                if (dX < 0) {
                    val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
                    val iconRight = itemView.right - iconMargin
                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

                    background.setBounds(
                        itemView.right + dX.toInt(),
                        itemView.top,
                        itemView.right,
                        itemView.bottom
                    )
                }

                background.draw(c)
                icon.draw(c)

                super.onChildDraw(
                    c, recyclerView, viewHolder, dX, dY,
                    actionState, isCurrentlyActive
                )
            }
        }
    }

    private fun showDeleteConfirmation(product: Product, position: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar producto")
            .setMessage("¿Estás seguro de que quieres eliminar este producto?\n\nCódigo: ${product.barcode}\nSKU: ${product.sku}")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteProduct(product, position)
            }
            .setNegativeButton("Cancelar") { _, _ ->
                adapter.notifyItemChanged(position)
            }
            .setOnCancelListener {
                adapter.notifyItemChanged(position)
            }
            .show()
    }

    private fun deleteProduct(product: Product, position: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                productDatabase.deleteProduct(product.barcode)
                adapter.removeProduct(position)
                showSuccessMessage("Producto eliminado exitosamente")
            } catch (e: Exception) {
                showErrorMessage("Error al eliminar producto: ${e.message}")
                adapter.notifyItemChanged(position)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}