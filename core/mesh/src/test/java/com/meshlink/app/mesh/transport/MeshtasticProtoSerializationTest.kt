package com.meshlink.app.mesh.transport

import com.meshlink.app.domain.model.MeshPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshtasticProtoSerializationTest {

    @Test
    fun `deterministicNodeId produces identical output for same input across calls`() {
        val id = "node-alpha-rescue-123"
        val hash1 = deterministicNodeId(id)
        val hash2 = deterministicNodeId(id)
        assertEquals(hash1, hash2)
        assertNotEquals(0, hash1)
    }

    @Test
    fun `deterministicNodeId handles broadcast destination correctly`() {
        val broadcastHash = deterministicNodeId(MeshPacket.BROADCAST_DEST)
        assertEquals(0xFFFFFFFF.toInt(), broadcastHash)
    }

    @Test
    fun `deterministicNodeId handles meshtastic hex node format`() {
        val hexId = "!28a12bc4"
        val expected = 0x28a12bc4.toInt()
        val actual = deterministicNodeId(hexId)
        assertEquals(expected, actual)
    }

    @Test
    fun `deterministicNodeId produces distinct outputs for distinct nodes`() {
        val id1 = "node-alpha-1"
        val id2 = "node-alpha-2"
        assertNotEquals(deterministicNodeId(id1), deterministicNodeId(id2))
    }

    @Test
    fun `toMeshtasticProto converts domain packet faithfully with deterministic IDs`() {
        val domainPacket = MeshPacket(
            senderId = "node-1",
            receiverId = MeshPacket.BROADCAST_DEST,
            content = "EMERGENCY: FLOOD EVACUATION",
            timestamp = 1726815600000L,
            type = MeshPacket.PacketType.ROUTED_CHAT,
            messageId = "msg-uuid-9999",
            originId = "node-origin-42",
            finalDestId = MeshPacket.BROADCAST_DEST,
            hopCount = 1,
            maxHops = 5,
            priority = 2
        )

        val proto = domainPacket.toMeshtasticProto()

        assertEquals(deterministicMessageId(domainPacket.messageId), proto.id)
        assertEquals(deterministicNodeId(domainPacket.originId), proto.from)
        assertEquals(0xFFFFFFFF.toInt(), proto.to)
        assertEquals((domainPacket.timestamp / 1000L).toInt(), proto.rxTime)
        assertEquals(5, proto.hopLimit)
        assertEquals(2, proto.priority)
        assertTrue(proto.hasDecoded())
        assertEquals("EMERGENCY: FLOOD EVACUATION", proto.decoded.payload.toStringUtf8())
    }
}

