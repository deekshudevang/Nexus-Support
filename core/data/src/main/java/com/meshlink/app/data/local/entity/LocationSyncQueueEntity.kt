package com.meshlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "location_sync_queue",
    foreignKeys = [
        ForeignKey(
            entity = LocationEventEntity::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("eventId")]
)
data class LocationSyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    val targetPeerId: String,
    val ttl: Int,
    val status: Int = 0 // 0 = PENDING, 1 = SENT
)
