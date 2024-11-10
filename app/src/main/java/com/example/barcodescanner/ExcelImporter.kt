package com.example.barcodescanner

import android.content.Context
import android.net.Uri
import com.example.barcodescanner.model.HistoryItem
import com.example.barcodescanner.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class ExcelImporter(private val historyManager: HistoryManager) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    suspend fun importFromExcel(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val workbook: Workbook = XSSFWorkbook(inputStream)
        val sheet: Sheet = workbook.getSheetAt(0)
        val importedItems = mutableListOf<HistoryItem>()
        val duplicateItems = mutableListOf<HistoryItem>()

        for (rowIndex in 1..sheet.lastRowNum) {
            val row: Row = sheet.getRow(rowIndex) ?: continue
            try {
                val item = createHistoryItemFromRow(row)
                if (!isDuplicate(item)) {
                    historyManager.saveItem(item)
                    importedItems.add(item)
                } else {
                    duplicateItems.add(item)
                }
            } catch (e: Exception) {
                // Log the error or handle it as needed
                println("Error processing row $rowIndex: ${e.message}")
            }
        }

        workbook.close()
        inputStream?.close()

        ImportResult(importedItems, duplicateItems)
    }

    private fun createHistoryItemFromRow(row: Row): HistoryItem {
        return HistoryItem(
            id = 0, // Dejar que Room genere el ID
            barcode = getCellValueAsString(row.getCell(1)),
            sku = getCellValueAsString(row.getCell(2)),
            description = getCellValueAsString(row.getCell(3)),
            expirationDate = getCellValueAsString(row.getCell(4)),
            quantity = getCellValueAsInt(row.getCell(6)),
            user = User(name = getCellValueAsString(row.getCell(7))),
            withdrawalDays = getCellValueAsInt(row.getCell(8)),
            scanDate = dateFormat.format(Date()),
            withdrawalDate = calculateWithdrawalDate(
                getCellValueAsString(row.getCell(4)),
                getCellValueAsInt(row.getCell(8))
            )
        )
    }

    private fun getCellValueAsString(cell: Cell?): String {
        return when (cell?.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    dateFormat.format(cell.dateCellValue)
                } else {
                    cell.numericCellValue.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                when (cell.cachedFormulaResultType) {
                    CellType.STRING -> cell.stringCellValue
                    CellType.NUMERIC -> cell.numericCellValue.toString()
                    else -> ""
                }
            }
            else -> ""
        }
    }

    private fun getCellValueAsInt(cell: Cell?): Int {
        return when (cell?.cellType) {
            CellType.NUMERIC -> cell.numericCellValue.toInt()
            CellType.STRING -> cell.stringCellValue.toIntOrNull() ?: 0
            else -> 0
        }
    }

    private fun calculateWithdrawalDate(expirationDate: String, withdrawalDays: Int): String {
        val expDate = try {
            dateFormat.parse(expirationDate)
        } catch (e: Exception) {
            Date() // Use current date if parsing fails
        }
        val calendar = Calendar.getInstance()
        calendar.time = expDate ?: Date()
        calendar.add(Calendar.DAY_OF_MONTH, -withdrawalDays)
        return dateFormat.format(calendar.time)
    }

    private suspend fun isDuplicate(item: HistoryItem): Boolean {
        return historyManager.isItemAlreadyRegistered(item.barcode, item.expirationDate)
    }
}

data class ImportResult(
    val importedItems: List<HistoryItem>,
    val duplicateItems: List<HistoryItem>
)