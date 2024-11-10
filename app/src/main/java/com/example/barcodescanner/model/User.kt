package com.example.barcodescanner.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = -1,
    @ColumnInfo(name = "name") val name: String
) : Serializable {
    override fun toString(): String = name
    fun isValid(): Boolean = id != -1
}

@Entity(tableName = "last_user_id")
data class LastUserId(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "lastId") val lastId: Int
)
