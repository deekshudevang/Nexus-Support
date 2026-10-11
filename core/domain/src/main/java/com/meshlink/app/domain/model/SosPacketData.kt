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
    val latitude: Double? = null,
    val longitude: Double? = null,
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

    val hasValidLocation: Boolean
        get() = latitude != null && longitude != null &&
                !latitude.isNaN() && !latitude.isInfinite() &&
                !longitude.isNaN() && !longitude.isInfinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun toJson(): String {
        val jsonObj = com.google.gson.JsonObject().apply {
            addProperty("uuid", uuid)
            addProperty("senderId", senderId)
            addProperty("senderName", senderName)
            addProperty("emergencyType", emergencyType.name)
            addProperty("severity", severity)
            if (hasValidLocation) {
                addProperty("latitude", latitude)
                addProperty("longitude", longitude)
            } else {
                add("latitude", com.google.gson.JsonNull.INSTANCE)
                add("longitude", com.google.gson.JsonNull.INSTANCE)
            }
            addProperty("message", message)
            addProperty("timestamp", timestamp)
            addProperty("hopCount", hopCount)
            addProperty("status", status.name)
        }
        return GSON.toJson(jsonObj)
    }

    companion object {
        private val GSON = com.google.gson.Gson()

        fun fromJson(jsonStr: String): SosPacketData? {
            return try {
                val element = com.google.gson.JsonParser.parseString(jsonStr.trim())
                if (!element.isJsonObject) return null
                val obj = element.asJsonObject

                val rawLat = when {
                    obj.has("latitude") && !obj.get("latitude").isJsonNull -> obj.get("latitude").asDouble
                    obj.has("lat") && !obj.get("lat").isJsonNull -> obj.get("lat").asDouble
                    else -> null
                }
                val rawLon = when {
                    obj.has("longitude") && !obj.get("longitude").isJsonNull -> obj.get("longitude").asDouble
                    obj.has("lon") && !obj.get("lon").isJsonNull -> obj.get("lon").asDouble
                    else -> null
                }

                // Explicit coordinate range validation
                val validLat = if (rawLat != null && !rawLat.isNaN() && !rawLat.isInfinite() && rawLat in -90.0..90.0) rawLat else null
                val validLon = if (rawLon != null && !rawLon.isNaN() && !rawLon.isInfinite() && rawLon in -180.0..180.0) rawLon else null

                val eType = try {
                    EmergencyType.valueOf(obj.get("emergencyType")?.asString?.uppercase() ?: "OTHER")
                } catch (_: Exception) {
                    EmergencyType.OTHER
                }

                val status = try {
                    SosStatus.valueOf(obj.get("status")?.asString?.uppercase() ?: "PENDING")
                } catch (_: Exception) {
                    SosStatus.PENDING
                }

                SosPacketData(
                    uuid = obj.get("uuid")?.asString ?: UUID.randomUUID().toString(),
                    senderId = obj.get("senderId")?.asString ?: "",
                    senderName = obj.get("senderName")?.asString ?: "",
                    emergencyType = eType,
                    severity = obj.get("severity")?.asInt ?: 3,
                    latitude = validLat,
                    longitude = validLon,
                    message = obj.get("message")?.asString ?: "",
                    timestamp = obj.get("timestamp")?.asLong ?: System.currentTimeMillis(),
                    hopCount = obj.get("hopCount")?.asInt ?: 0,
                    status = status
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
