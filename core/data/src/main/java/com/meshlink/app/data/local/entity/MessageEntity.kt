package com.meshlink.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val receiverId: String,
    val ciphertext: ByteArray,
    val timestamp: Long,
    val delivered: Boolean,
    @ColumnInfo(defaultValue = "")
    val senderName: String = "",
    @ColumnInfo(defaultValue = "")
    val routeHistory: String = "",
    @ColumnInfo(defaultValue = "0")
    val status: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val isSos: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
