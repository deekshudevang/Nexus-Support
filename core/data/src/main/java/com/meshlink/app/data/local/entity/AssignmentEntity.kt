package com.meshlink.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "assignments",
    indices = [
        Index("incidentId"),
        Index("status")
    ]
)
data class AssignmentEntity(
    @PrimaryKey
    val id: String,
    val incidentId: String,
    @ColumnInfo(defaultValue = "")
    val responderId: String = "",
    @ColumnInfo(defaultValue = "SAR")
    val role: String = "SAR",
    @ColumnInfo(defaultValue = "DISPATCHED")
    val status: String = "DISPATCHED",
    @ColumnInfo(defaultValue = "")
    val notes: String = "",
    @ColumnInfo(defaultValue = "")
    val description: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ColumnInfo(defaultValue = "3")
    val priority: Int = 3,
    @ColumnInfo(defaultValue = "5")
    val severity: Int = 5,
    @ColumnInfo(defaultValue = "0")
    val offeredAt: Long = 0L,
    val respondedAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val synced: Boolean = false
)
