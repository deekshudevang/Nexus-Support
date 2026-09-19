package com.meshlink.app.domain.model

import java.util.UUID

enum class Priority(val level: Int) {
    CRITICAL_SOS(1),
    MEDICAL(2),
    EVACUATION(3),
    NORMAL(4)
}

enum class MessageType {
    SOS,
    CHAT,
    LOCATION,
    ROUTE_REQUEST,
    ROUTE_REPLY,
    ACK,
    HEARTBEAT,
    BROADCAST
}

data class MeshMessage(
    val messageId: String = UUID.randomUUID().toString(),
    val schemaVersion: Int = 1,
    val type: MessageType,
    val senderId: String,
    val destinationId: String?,
    val priority: Priority,
    val ttl: Int,
    val maxHops: Int = 10,
    val timestamp: Long = System.currentTimeMillis(),
    val hopCount: Int = 0,
    val path: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val payload: String,
    val signature: String? = null
)
