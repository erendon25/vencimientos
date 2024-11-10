package com.example.barcodescanner.model

import java.io.Serializable
import java.util.Objects

data class HistoryItem(
    val id: Long = 0, // ID para Room
    val barcode: String,
    val sku: String,
    val description: String,
    val quantity: Int,
    val expirationDate: String,
    val withdrawalDays: Int,
    val withdrawalDate: String?,
    val user: User,
    val scanDate: String? = null
) : Serializable {
    override fun hashCode(): Int {
        return Objects.hash(
            id,
            barcode.orEmpty(),
            sku.orEmpty(),
            description.orEmpty(),
            quantity,
            expirationDate.orEmpty(),
            withdrawalDays,
            withdrawalDate.orEmpty(),
            user,
            scanDate.orEmpty()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HistoryItem

        return id == other.id &&
                barcode == other.barcode &&
                sku == other.sku &&
                description == other.description &&
                quantity == other.quantity &&
                expirationDate == other.expirationDate &&
                withdrawalDays == other.withdrawalDays &&
                withdrawalDate == other.withdrawalDate &&
                user == other.user &&
                scanDate == other.scanDate
    }
}