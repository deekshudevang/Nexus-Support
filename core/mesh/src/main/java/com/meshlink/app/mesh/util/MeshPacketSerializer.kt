package com.meshlink.app.mesh.util

import com.meshlink.app.domain.model.MeshPacket
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID

const val MAX_PACKET_BYTES = 65_536 // 64 KB maximum packet frame size
const val MAX_ROUTE_HOPS = 32

fun MeshPacket.toBytes(): ByteArray {
    val histArray = JSONArray().also { arr -> routeHistory.take(MAX_ROUTE_HOPS).forEach { arr.put(it) } }
    val json = JSONObject().apply {
        put("messageId",    messageId)
        put("senderId",     senderId)
        put("receiverId",   receiverId)
        put("content",      content)
        put("timestamp",    timestamp)
        put("type",         type.name)
        // Phase 4 routing fields
        put("originId",     originId)
        put("finalDestId",  finalDestId)
        put("hopCount",     hopCount)
        put("maxHops",      maxHops.coerceAtMost(MAX_ROUTE_HOPS))
        put("routeHistory", histArray)
        // User identity
        if (senderName.isNotEmpty()) put("senderName", senderName)
    }
    return json.toString().toByteArray(Charsets.UTF_8)
}

fun ByteArray.toMeshPacket(): MeshPacket? {
    if (size > MAX_PACKET_BYTES) {
        Timber.w("MeshPacket exceeds maximum frame size (%d > %d bytes); dropped for security", size, MAX_PACKET_BYTES)
        return null
    }
    return try {
        val json      = JSONObject(toString(Charsets.UTF_8))
        val histArray = json.optJSONArray("routeHistory")
        val history   = buildList {
            if (histArray != null) {
                val limit = histArray.length().coerceAtMost(MAX_ROUTE_HOPS)
                for (i in 0 until limit) add(histArray.getString(i))
            }
        }
        val senderId  = json.getString("senderId")
        val receiverId = json.getString("receiverId")
        MeshPacket(
            messageId    = json.optString("messageId", UUID.randomUUID().toString()),
            senderId     = senderId,
            receiverId   = receiverId,
            content      = json.getString("content"),
            timestamp    = json.getLong("timestamp"),
            type         = MeshPacket.PacketType.valueOf(json.optString("type", "CHAT")),
            // Phase 4 — fall back to Phase 3 values for backward compat with older peers
            originId     = json.optString("originId",    senderId),
            finalDestId  = json.optString("finalDestId", receiverId),
            hopCount     = json.optInt   ("hopCount",    0),
            maxHops      = json.optInt   ("maxHops",     7),
            routeHistory = history,
            // User identity — backward compat: older peers won't send this field
            senderName   = json.optString("senderName", "")
        )
    } catch (e: Exception) {
        Timber.e(e, "Failed to deserialize MeshPacket")
        null
    }
}
