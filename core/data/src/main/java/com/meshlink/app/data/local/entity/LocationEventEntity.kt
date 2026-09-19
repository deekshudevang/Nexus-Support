package com.meshlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_events")
data class LocationEventEntity(
    @PrimaryKey val eventId: String,
    val peerId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val sequenceNumber: Int,
    val signature: String,
    val publicKey: String,
    val syncStatus: Int = 0 // 0 = PENDING, 1 = UPLOADED
)
