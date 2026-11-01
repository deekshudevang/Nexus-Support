package com.meshlink.app.mesh.util

import com.meshlink.app.domain.model.MeshPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshPacketSerializerSecurityTest {

    @Test
    fun `roundtrip serialization preserves all packet fields`() {
        val original = MeshPacket(
            messageId = "test-msg-123",
            senderId = "sender-node",
            receiverId = "receiver-node",
            content = "EncryptedPayloadBytes",
            timestamp = 1700000000000L,
            type = MeshPacket.PacketType.ROUTED_CHAT,
            originId = "origin-node",
            finalDestId = "dest-node",
            hopCount = 2,
            maxHops = 7,
            routeHistory = listOf("relay-1", "relay-2"),
            senderName = "Responder Alice"
        )

        val bytes = original.toBytes()
        val restored = bytes.toMeshPacket()

        assertNotNull(restored)
        restored?.let {
            assertEquals(original.messageId, it.messageId)
            assertEquals(original.senderId, it.senderId)
            assertEquals(original.receiverId, it.receiverId)
            assertEquals(original.content, it.content)
            assertEquals(original.timestamp, it.timestamp)
            assertEquals(original.type, it.type)
            assertEquals(original.originId, it.originId)
            assertEquals(original.finalDestId, it.finalDestId)
            assertEquals(original.hopCount, it.hopCount)
            assertEquals(original.maxHops, it.maxHops)
            assertEquals(original.routeHistory, it.routeHistory)
            assertEquals(original.senderName, it.senderName)
        }
    }

    @Test
    fun `oversized packet exceeding 64KB is rejected and returns null`() {
        val giantBytes = ByteArray(MAX_PACKET_BYTES + 1) { 0x41 } // 65,537 bytes of 'A'
        val result = giantBytes.toMeshPacket()
        assertNull("Packets exceeding MAX_PACKET_BYTES must be rejected", result)
    }

    @Test
    fun `malformed or corrupted packet returns null without throwing unhandled exceptions`() {
        val garbage = "NotAValidJsonPayloadAtAll".toByteArray(Charsets.UTF_8)
        val result = garbage.toMeshPacket()
        assertNull(result)
    }

    @Test
    fun `route history is clamped to MAX_ROUTE_HOPS to prevent resource exhaustion`() {
        val massiveHistory = (1..100).map { "relay-$it" }
        val packet = MeshPacket(
            senderId = "s",
            receiverId = "r",
            content = "test",
            timestamp = 1000L,
            routeHistory = massiveHistory
        )

        val bytes = packet.toBytes()
        val restored = bytes.toMeshPacket()

        assertNotNull(restored)
        assertTrue(restored!!.routeHistory.size <= MAX_ROUTE_HOPS)
    }
}
