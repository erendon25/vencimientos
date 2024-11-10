package com.example.barcodescanner.model

import com.google.firebase.Timestamp
import java.util.*

data class ProductScan(
    val id: String = "",
    val barcode: String = "",
    val userId: String = "",
    val storeId: String = "",
    val timestamp: Timestamp = Timestamp(Date()),
    val productData: ProductScanData = ProductScanData(),
    val lastModifiedAt: Timestamp? = null,
    val lastModifiedBy: String? = null
)

data class ProductScanData(
    val sku: String = "",
    val description: String = "",
    val expirationDate: String = "",
    val quantity: Int = 0,
    val withdrawalDays: Int = 0,
    val withdrawalDate: String = "", // Nuevo campo
    val scanDate: String = "",
    val storeName: String = "",
    val scannerName: String = ""
){
    fun toMap(): Map<String, Any> = mapOf(
        "sku" to sku,
        "description" to description,
        "expirationDate" to expirationDate,
        "quantity" to quantity,
        "withdrawalDays" to withdrawalDays,
        "scanDate" to scanDate,
        "storeName" to storeName,
        "scannerName" to scannerName
    )
}

fun ProductScan.toMap(): Map<String, Any> {
    return mapOf(
        "barcode" to barcode,
        "userId" to userId,
        "storeId" to storeId,
        "timestamp" to timestamp,
        "productData" to mapOf(
            "sku" to productData.sku,
            "description" to productData.description,
            "expirationDate" to productData.expirationDate,
            "quantity" to productData.quantity,
            "withdrawalDays" to productData.withdrawalDays,
            "withdrawalDate" to productData.withdrawalDate, // Agregamos el nuevo campo
            "scanDate" to productData.scanDate,
            "storeName" to productData.storeName,
            "scannerName" to productData.scannerName
        )
    )
}