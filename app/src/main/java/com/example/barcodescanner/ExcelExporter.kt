package com.example.barcodescanner

import com.example.barcodescanner.model.HistoryItem
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ExcelExporter {

    fun export(data: List<HistoryItem>, filePath: String) {
        val workbook: Workbook = XSSFWorkbook()
        val sheet: Sheet = workbook.createSheet("Datos_vencimientos")

        // Crear estilo para los encabezados
        val headerStyle = workbook.createCellStyle().apply {
            // Establecer el estilo de borde
            setBorder(BorderStyle.THIN)
            // Establecer el color de fondo
            setFillForegroundColor(IndexedColors.GREY_25_PERCENT.index)
            fillPattern = FillPatternType.SOLID_FOREGROUND
            // Establecer la alineación
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            // Establecer la fuente
            setFont(workbook.createFont().apply {
                bold = true
            })
        }

        // Crear estilo para el contenido
        val contentStyle = workbook.createCellStyle().apply {
            setBorder(BorderStyle.THIN)
            alignment = HorizontalAlignment.LEFT
            verticalAlignment = VerticalAlignment.CENTER
            wrapText = true // Permite múltiples líneas si es necesario
        }

        // Crear encabezados
        val headerRow: Row = sheet.createRow(0)
        val headers = listOf(
            "Número de ítem",
            "Código de barras",
            "SKU",
            "Descripción",
            "Fecha de vencimiento",
            "Fecha de retiro",
            "Cantidad",
            "Usuario",
            "Días de retiro"
        )

        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }

        // Establecer altura de la fila de encabezado
        headerRow.heightInPoints = 30f

        // Llenar datos
        data.forEachIndexed { index, item ->
            val row: Row = sheet.createRow(index + 1)

            // Usar operador Elvis (?:) para manejar nullables
            createCell(row, 0, (index + 1).toString(), contentStyle)
            createCell(row, 1, item.barcode, contentStyle)
            createCell(row, 2, item.sku ?: "N/A", contentStyle)
            createCell(row, 3, item.description ?: "N/A", contentStyle)
            createCell(row, 4, item.expirationDate, contentStyle)
            createCell(row, 5, calculateWithdrawalDate(item.expirationDate, item.withdrawalDays), contentStyle)
            createCell(row, 6, item.quantity.toString(), contentStyle)
            createCell(row, 7, item.user?.name ?: "N/A", contentStyle) // Acceso seguro a user
            createCell(row, 8, item.withdrawalDays.toString(), contentStyle)
        }

        // Ajustar el ancho de las columnas automáticamente
        headers.indices.forEach { i ->
            // Establecer un ancho mínimo de 15 caracteres
            var maxWidth = 15 * 256 // 15 caracteres

            // Revisar el contenido de cada celda en la columna
            for (rowNum in 0..sheet.lastRowNum) {
                val cell = sheet.getRow(rowNum)?.getCell(i)
                if (cell != null) {
                    val length = cell.toString().length
                    if (length > maxWidth) {
                        maxWidth = length * 256 // multiplicar por 256 para convertir a unidades de Excel
                    }
                }
            }

            // Establecer un ancho máximo de 50 caracteres para evitar columnas demasiado anchas
            maxWidth = minOf(maxWidth, 50 * 256)

            // Ajustar el ancho de la columna
            sheet.setColumnWidth(i, maxWidth)
        }

        // Crear el archivo
        FileOutputStream(File(filePath)).use { outputStream ->
            workbook.write(outputStream)
        }
        workbook.close()
    }

    private fun createCell(row: Row, column: Int, value: String, style: CellStyle) {
        row.createCell(column).apply {
            setCellValue(value)
            cellStyle = style
        }
    }

    private fun CellStyle.setBorder(borderStyle: BorderStyle) {
        borderTop = borderStyle
        borderBottom = borderStyle
        borderLeft = borderStyle
        borderRight = borderStyle
    }

    private fun calculateWithdrawalDate(expirationDate: String, withdrawalDays: Int): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val expDate = formatter.parse(expirationDate)
        val calendar = Calendar.getInstance()
        calendar.time = expDate ?: Date()
        calendar.add(Calendar.DAY_OF_MONTH, -withdrawalDays)
        return formatter.format(calendar.time)
    }
}