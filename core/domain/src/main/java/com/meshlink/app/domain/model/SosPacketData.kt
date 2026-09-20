package com.meshlink.app.domain.model

import java.util.UUID

/**
 * Structured SOS packet data — replaces raw plaintext SOS strings.
 *
 * Inspired by RESCUE-MESH's SosPacket model with emergency categorization,
 * severity levels, GPS, and status tracking.
 */
data class SosPacketData(
    val uuid: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String = "",
    val emergencyType: EmergencyType = EmergencyType.OTHER,
    val severity: Int = 3, // 1-5 scale
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val hopCount: Int = 0,
    val status: SosStatus = SosStatus.PENDING
) {
    enum class EmergencyType {
        MEDICAL, FIRE, FLOOD, EARTHQUAKE, SECURITY, TRAPPED, LOST, OTHER
    }

    enum class SosStatus {
        PENDING, RELAYED, DELIVERED, RESPONDED
    }

    fun toJson(): String {
        val sb = StringBuilder("{")
        sb.append("\"uuid\":\"$uuid\",")
        sb.append("\"senderId\":\"$senderId\",")
        sb.append("\"senderName\":\"${senderName.replace("\"", "\\\"")}\",")
        sb.append("\"emergencyType\":\"${emergencyType.name}\",")
        sb.append("\"severity\":$severity,")
        sb.append("\"latitude\":$latitude,")
        sb.append("\"longitude\":$longitude,")
        sb.append("\"message\":\"${message.replace("\"", "\\\"")}\",")
        sb.append("\"timestamp\":$timestamp,")
        sb.append("\"hopCount\":$hopCount,")
        sb.append("\"status\":\"${status.name}\"")
        sb.append("}")
        return sb.toString()
    }
}
