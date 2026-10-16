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
    val status: SosStatus = SosStatus.PENDING,
    val beaconKey: String? = null
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
                latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
                (latitude != 0.0 || longitude != 0.0)

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
            if (!beaconKey.isNullOrBlank()) {
                addProperty("beaconKey", beaconKey)
            }
        }

        var jsonStr = GSON.toJson(jsonObj)
        if (jsonStr.toByteArray(Charsets.UTF_8).size > MAX_TRANSPORT_PAYLOAD_BYTES) {
            // Deterministically truncate medical info / message while strictly preserving all vital fields (uuid, senderId, location, etc.)
            jsonObj.addProperty("message", "")
            val baseSize = GSON.toJson(jsonObj).toByteArray(Charsets.UTF_8).size
            val markerBytes = TRUNCATION_MARKER.toByteArray(Charsets.UTF_8).size
            val maxMessageBytes = (MAX_TRANSPORT_PAYLOAD_BYTES - baseSize - markerBytes - 32).coerceAtLeast(0)

            var best = truncateUtf8String(message, maxMessageBytes)
            jsonObj.addProperty("message", best + TRUNCATION_MARKER)
            jsonStr = GSON.toJson(jsonObj)

            // Final safety adjustment in case JSON escaping added extra bytes
            while (jsonStr.toByteArray(Charsets.UTF_8).size > MAX_TRANSPORT_PAYLOAD_BYTES && best.isNotEmpty()) {
                val excess = jsonStr.toByteArray(Charsets.UTF_8).size - MAX_TRANSPORT_PAYLOAD_BYTES
                val dropCount = maxOf(1, excess / 2)
                best = best.dropLast(dropCount)
                if (best.isNotEmpty() && Character.isHighSurrogate(best.last())) {
                    best = best.dropLast(1)
                }
                jsonObj.addProperty("message", best + TRUNCATION_MARKER)
                jsonStr = GSON.toJson(jsonObj)
            }
        }
        return jsonStr
    }

    private fun truncateUtf8String(str: String, maxBytes: Int): String {
        if (maxBytes <= 0) return ""
        if (str.toByteArray(Charsets.UTF_8).size <= maxBytes) return str

        var low = 0
        var high = minOf(str.length, maxBytes)
        var best = ""
        while (low <= high) {
            val mid = (low + high) ushr 1
            val candidate = str.substring(0, mid)
            if (candidate.toByteArray(Charsets.UTF_8).size <= maxBytes) {
                best = candidate
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (best.isNotEmpty() && Character.isHighSurrogate(best.last())) {
            best = best.dropLast(1)
        }
        return best
    }

    companion object {
        const val MAX_TRANSPORT_PAYLOAD_BYTES = 32_000 // Google Nearby Connections Payload.MAX_BYTES = 32,768 (32 KB); reserve envelope headroom
        const val TRUNCATION_MARKER = "\n[TRUNCATED: Medical info exceeded transport limit]"
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

                val beaconKey = if (obj.has("beaconKey") && !obj.get("beaconKey").isJsonNull) {
                    obj.get("beaconKey").asString
                } else null

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
                    status = status,
                    beaconKey = beaconKey
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
