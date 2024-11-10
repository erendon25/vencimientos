package com.example.barcodescanner.auth

data class StoreUser(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val storeId: String = "",
    val storeName: String = "",
    val role: String = "",
    val active: Boolean = false,
    val admin: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
) {
    constructor() : this(
        uid = "",
        email = "",
        name = "",
        storeId = "",
        storeName = "",
        role = "",
        active = false,
        admin = false
    )

    fun toMap(): Map<String, Any> {
        return mapOf(
            "uid" to uid,
            "email" to email,
            "name" to name,
            "storeId" to storeId,
            "storeName" to storeName,
            "role" to role,
            "active" to active,
            "admin" to admin,
            "createdAt" to createdAt,
            "lastLoginAt" to lastLoginAt
        )
    }

    companion object {
        const val ROLE_ADMIN = "ADMIN"
        const val ROLE_USER = "USER"
    }
}