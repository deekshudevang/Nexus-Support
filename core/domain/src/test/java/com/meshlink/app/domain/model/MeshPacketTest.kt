package com.meshlink.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshPacketTest {

    @Test
    fun `isBroadcast should return true when finalDestId is BROADCAST_DEST`() {
        val packet = MeshPacket(
            senderId = "A",
            receiverId = MeshPacket.BROADCAST_DEST,
            content = "SOS",
            timestamp = 1000L,
            finalDestId = MeshPacket.BROADCAST_DEST
        )
        
        assertTrue("Packet should be recognized as a broadcast", packet.isBroadcast)
    }

    @Test
    fun `packet initialization sets correct defaults`() {
        val packet = MeshPacket(
            senderId = "A",
            receiverId = "B",
            content = "Hello",
            timestamp = 1000L
        )
        
        assertEquals("originId should default to senderId", "A", packet.originId)
        assertEquals("finalDestId should default to receiverId", "B", packet.finalDestId)
        assertEquals("hopCount should default to 0", 0, packet.hopCount)
        assertEquals("type should default to CHAT", MeshPacket.PacketType.CHAT, packet.type)
        assertEquals("priority should default to 0", 0, packet.priority)
    }
}
