package com.example.barcodescanner.entity
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "products")
data class Product(
    @PrimaryKey
    val barcode: String,
    val sku: String = "",
    val description: String = ""
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "barcode" to barcode,
            "sku" to sku,
            "description" to description
        )
    }
}