package com.meshlink.app.domain.model

/**
 * Node role and status for mesh-wide resource coordination.
 *
 * Inspired by RescueMesh-1's community dashboard with device status,
 * resource coordination, and role assignment.
 */
enum class NodeRole {
    SURVIVOR,
    MEDIC,
    SEARCH_RESCUE,
    COORDINATOR,
    RELAY_ONLY
}

/**
 * Status snapshot for a mesh node — broadcast via STATUS_UPDATE packets.
 */
data class NodeStatus(
    val deviceId: String,
    val displayName: String = "",
    val role: NodeRole = NodeRole.SURVIVOR,
    val batteryLevel: Int = 100,
    val hasWater: Boolean = false,
    val hasFood: Boolean = false,
    val hasMedKit: Boolean = false,
    val needsHelp: Boolean = false,
    val personCount: Int = 1,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        val sb = StringBuilder("{")
        sb.append("\"deviceId\":\"$deviceId\",")
        sb.append("\"displayName\":\"${displayName.replace("\"", "\\\"")}\",")
        sb.append("\"role\":\"${role.name}\",")
        sb.append("\"batteryLevel\":$batteryLevel,")
        sb.append("\"hasWater\":$hasWater,")
        sb.append("\"hasFood\":$hasFood,")
        sb.append("\"hasMedKit\":$hasMedKit,")
        sb.append("\"needsHelp\":$needsHelp,")
        sb.append("\"personCount\":$personCount,")
        sb.append("\"latitude\":$latitude,")
        sb.append("\"longitude\":$longitude,")
        sb.append("\"timestamp\":$timestamp")
        sb.append("}")
        return sb.toString()
    }
}
