package com.example.barcodescanner.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodescanner.ExcelExporter
import com.example.barcodescanner.ui.adapters.HistoryAdapter
import com.example.barcodescanner.model.HistoryItem
import com.example.barcodescanner.HistoryManager
import com.example.barcodescanner.R
import com.example.barcodescanner.databinding.FragmentHistoryBinding
import kotlinx.coroutines.launch
import java.io.File
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DefaultItemAnimator
import com.example.barcodescanner.ui.viewmodels.HistoryViewModel
import com.google.android.material.snackbar.Snackbar

class HistoryFragment : Fragment(), HistoryAdapter.OnItemClickListener, HistoryAdapter.OnItemLongClickListener {
    private val viewModel: HistoryViewModel by viewModels {
        HistoryViewModel.Factory(requireContext())
    }
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var historyManager: HistoryManager
    private var lastDeletedItem: HistoryItem? = null
    private var lastDeletedPosition: Int = -1
    private var actionMode: ActionMode? = null
    private var currentSortOrder = SortOrder.DATE_DESC
    private var isShowingTodayWithdrawals = false
    private var undoSnackbar: Snackbar? = null

    enum class SortOrder {
        DATE_ASC, DATE_DESC, DESCRIPTION_ASC, DESCRIPTION_DESC, USER_ASC, USER_DESC
    }
    override fun onResume() {
        super.onResume()
        viewModel.loadItems() // Recargar items cuando el fragmento se resume
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        historyManager = HistoryManager(requireContext())
        historyAdapter = HistoryAdapter(this, this)
        setupRecyclerView()
        observeViewModel() // Añadir esta línea
        setupSwipeToDelete()
        setupButtons()
        setupDeleteAllButton()
        setupUndoButton()
        setupMenu()
        viewModel.loadItems() // Cargar items inicialmente
    }
    private fun observeViewModel() {
        // Observar cambios en los items
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.historyItems.collect { items ->
                Log.d("HistoryFragment", "Nuevos items recibidos: ${items.size}")
                val sortedItems = sortItems(items)
                historyAdapter.updateItems(sortedItems)
            }
        }
        // Observar errores
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collect { errorMessage ->
                errorMessage?.let {
                    showError(it)
                }
            }
        }
        // Observar estado de carga
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showError(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG).show()
        }
    }


    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.history_menu, menu)
                menu.findItem(R.id.action_sort)?.isVisible = !isShowingTodayWithdrawals
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_sort -> {
                        showSortDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner)
    }

    private fun showSortDialog() {
        val options = arrayOf("Fecha (Reciente primero)", "Fecha (Antiguo primero)",
            "Descripción (A-Z)", "Descripción (Z-A)",
            "Usuario (A-Z)", "Usuario (Z-A)")

        AlertDialog.Builder(requireContext())
            .setTitle("Ordenar por")
            .setItems(options) { _, which ->
                currentSortOrder = when (which) {
                    0 -> SortOrder.DATE_DESC
                    1 -> SortOrder.DATE_ASC
                    2 -> SortOrder.DESCRIPTION_ASC
                    3 -> SortOrder.DESCRIPTION_DESC
                    4 -> SortOrder.USER_ASC
                    5 -> SortOrder.USER_DESC
                    else -> SortOrder.DATE_DESC
                }
                loadHistoryItems()
            }
            .show()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(this, this)
        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
            // Opcional: Añadir animaciones
            itemAnimator = DefaultItemAnimator()
        }
    }

    private fun loadHistoryItems() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.loadItems()
            } catch (e: Exception) {
                Log.e("HistoryFragment", "Error al cargar items", e)
                view?.let { fragmentView ->
                    Snackbar.make(
                        fragmentView,
                        "Error al cargar los elementos",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun sortItems(items: List<HistoryItem>): List<HistoryItem> {
        return when (currentSortOrder) {
            SortOrder.DATE_DESC -> items.sortedByDescending { it.scanDate }
            SortOrder.DATE_ASC -> items.sortedBy { it.scanDate }
            SortOrder.DESCRIPTION_ASC -> items.sortedBy { it.description }
            SortOrder.DESCRIPTION_DESC -> items.sortedByDescending { it.description }
            SortOrder.USER_ASC -> items.sortedBy { it.user.name }
            SortOrder.USER_DESC -> items.sortedByDescending { it.user.name }
        }
    }

    override fun onItemClick(item: HistoryItem) {
        if (actionMode != null) {
            toggleSelection(item)
        } else {
            showEditDialog(item) // Asegúrate de que esto se esté llamando
        }
    }

    override fun onItemLongClick(item: HistoryItem): Boolean {
        if (actionMode == null) {
            actionMode = (activity as AppCompatActivity).startSupportActionMode(actionModeCallback)
        }
        toggleSelection(item)
        return true
    }

    private fun toggleSelection(item: HistoryItem) {
        historyAdapter.toggleItemSelection(item)
        if (historyAdapter.getSelectedItems().isEmpty()) {
            actionMode?.finish()
        } else {
            actionMode?.title = "${historyAdapter.getSelectedItems().size} seleccionados"
            actionMode?.invalidate()
        }
    }
    private fun showEditDialog(item: HistoryItem) {
        Log.d("HistoryFragment", "Mostrando diálogo de edición para: ${item.description}")
        val dialog = EditHistoryDialogFragment.newInstance(item)

        // Set up result listener
        childFragmentManager.setFragmentResultListener("itemUpdate", viewLifecycleOwner) { _, bundle ->
            @Suppress("DEPRECATION")
            val updatedItem = bundle.getSerializable("updatedItem") as? HistoryItem
            updatedItem?.let {
                Log.d("HistoryFragment", "Item actualizado: ${it.description}")
                viewModel.updateItem(it)
            }
        }

        dialog.show(childFragmentManager, "edit_dialog")
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.contextual_action_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_delete -> {
                    deleteSelectedItems()
                    true
                }
                R.id.action_share -> {
                    shareSelectedItems()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            historyAdapter.clearSelection()
            actionMode = null
        }
    }

    private fun deleteSelectedItems() {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar items seleccionados")
            .setMessage("¿Estás seguro de que quieres eliminar los items seleccionados?")
            .setPositiveButton("Sí") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    historyAdapter.getSelectedItems().forEach { historyManager.deleteItem(it) }
                    loadHistoryItems()
                    actionMode?.finish()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun shareSelectedItems() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val exporter = ExcelExporter()
                val fileName = "Datos_seleccionados.xlsx"
                val filePath = "${requireContext().getExternalFilesDir(null)}/$fileName"
                exporter.export(historyAdapter.getSelectedItems().toList(), filePath)

                val file = File(filePath)
                val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
                startShareIntent(uri)

                actionMode?.finish()
            } catch (e: Exception) {
                Log.e("ExportToExcel", "Error exporting selected items to Excel", e)
            }
        }
    }

    private fun setupSwipeToDelete() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.RIGHT
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = historyAdapter.currentList[position]
                showDeleteConfirmation(item)
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.historyRecyclerView)
    }

    private fun showDeleteConfirmation(item: HistoryItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar item")
            .setMessage("¿Estás seguro de que quieres eliminar este item?")
            .setPositiveButton("Sí") { _, _ -> onDeleteItem(item) }
            .setNegativeButton("No") { _, _ -> historyAdapter.notifyDataSetChanged() }
            .show()
    }


    private fun onDeleteItem(item: HistoryItem) {
        lastDeletedItem = item
        lastDeletedPosition = historyAdapter.currentList.indexOf(item)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                historyManager.deleteItem(item)
                viewModel.loadItems() // Recargar la lista
                showUndoSnackbar()
            } catch (e: Exception) {
                showError("Error al eliminar el elemento: ${e.message}")
            }
        }
    }
    private fun showUndoSnackbar() {
        view?.let { view ->
            Snackbar.make(view, "Elemento eliminado", Snackbar.LENGTH_LONG)
                .setAction("DESHACER") {
                    undoDelete()
                }
                .show()
        }
    }
    private fun undoDelete() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                lastDeletedItem?.let { item ->
                    if (!historyManager.isItemAlreadyRegistered(item.barcode, item.expirationDate)) {
                        historyManager.saveItem(item)
                        viewModel.loadItems() // Recargar la lista
                        showError("Elemento restaurado")
                    }
                }
            } catch (e: Exception) {
                showError("Error al restaurar el elemento: ${e.message}")
            } finally {
                lastDeletedItem = null
                lastDeletedPosition = -1
            }
        }
    }

    private fun setupButtons() {
        binding.addButton.setOnClickListener {
            findNavController().navigate(R.id.action_historyContainerFragment_to_scanFragment)
        }

        binding.exportButton.setOnClickListener {
            exportAllToExcel()
        }
    }

    private fun setupUndoButton() {
        binding.undoButton.setOnClickListener {
            undoLastDeletion()
        }
        binding.undoButton.visibility = View.GONE
    }


    private fun undoLastDeletion() {
        viewLifecycleOwner.lifecycleScope.launch {
            lastDeletedItem?.let { item ->
                try {
                    if (!historyManager.isItemAlreadyRegistered(item.barcode, item.expirationDate)) {
                        historyManager.saveItem(item)
                        // Forzar recarga inmediata
                        viewModel.loadItems()
                        // Notificar al adaptador del cambio específico
                        historyAdapter.notifyItemInserted(lastDeletedPosition)

                        view?.let { fragmentView ->
                            Snackbar.make(
                                fragmentView,
                                "Elemento restaurado",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                    lastDeletedItem = null
                    lastDeletedPosition = -1
                } catch (e: Exception) {
                    Log.e("HistoryFragment", "Error al deshacer eliminación", e)
                    view?.let { fragmentView ->
                        Snackbar.make(
                            fragmentView,
                            "Error al deshacer la eliminación",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun setupDeleteAllButton() {
        binding.deleteAllButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Eliminar todos los registros")
                .setMessage("¿Estás seguro de que quieres eliminar todos los registros?")
                .setPositiveButton("Sí") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        historyManager.deleteAllItems()
                        loadHistoryItems()
                    }
                }
                .setNegativeButton("No", null)
                .show()
        }
    }

    private fun exportAllToExcel() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val items = historyManager.getAllItems()
                val exporter = ExcelExporter()
                val fileName = "Datos_vencimientos.xlsx"
                val filePath = "${requireContext().getExternalFilesDir(null)}/$fileName"
                exporter.export(items, filePath)

                val file = File(filePath)
                val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
                startShareIntent(uri)
            } catch (e: Exception) {
                Log.e("ExportToExcel", "Error exporting to Excel", e)
            }
        }
    }

    private fun startShareIntent(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        startActivity(Intent.createChooser(intent, "Compartir Excel"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        actionMode?.finish()
        _binding = null
    }

    companion object {
        private const val TAG = "HistoryFragment"
    }

}