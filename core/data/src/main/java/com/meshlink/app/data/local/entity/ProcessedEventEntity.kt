package com.meshlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processed_events")
data class ProcessedEventEntity(
    @PrimaryKey val eventId: String,
    val timestamp: Long = System.currentTimeMillis()
)
