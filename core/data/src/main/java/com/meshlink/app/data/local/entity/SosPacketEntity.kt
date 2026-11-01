package com.meshlink.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for persisting structured SOS packets received via mesh.
 *
 * Inspired by RESCUE-MESH's SosPacketDao + SosPacket model.
 */
@Entity(tableName = "sos_packets")
data class SosPacketEntity(
    @PrimaryKey val uuid: String,
    val senderId: String,
    @ColumnInfo(defaultValue = "")
    val senderName: String = "",
    val emergencyType: String, // EmergencyType.name
    @ColumnInfo(defaultValue = "3")
    val severity: Int = 3,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @ColumnInfo(defaultValue = "")
    val message: String = "",
    val timestamp: Long,
    @ColumnInfo(defaultValue = "0")
    val hopCount: Int = 0,
    @ColumnInfo(defaultValue = "PENDING")
    val status: String = "PENDING" // SosStatus.name
)
