package com.meshlink.app.mesh.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SosBeaconCodecTest {

    @Test
    fun `encode and decode symmetric test`() {
        val encoded = SosBeaconCodec.encode(
            deviceId = "test-device-123",
            latitude = 37.7749,
            longitude = -122.4194,
            emergencyType = SosBeaconCodec.EmergencyType.MEDICAL,
            severity = 4
        )

        assertEquals(18, encoded.size)

        val decoded = SosBeaconCodec.decode(encoded)
        assertNotNull(decoded)
        
        // Use a small delta for float comparison
        assertEquals(37.7749f, decoded!!.latitude, 0.001f)
        assertEquals(-122.4194f, decoded.longitude, 0.001f)
        assertEquals(SosBeaconCodec.EmergencyType.MEDICAL, decoded.emergencyType)
        assertEquals(4, decoded.severity)
    }

    @Test
    fun `decode returns null for bad size`() {
        val badData = ByteArray(10)
        assertNull(SosBeaconCodec.decode(badData))
    }

    @Test
    fun `decode returns null for bad CRC`() {
        val encoded = SosBeaconCodec.encode(
            deviceId = "test-device",
            latitude = 0.0,
            longitude = 0.0,
            emergencyType = SosBeaconCodec.EmergencyType.OTHER,
            severity = 1
        )
        
        // Corrupt a byte in the payload
        encoded[5] = (encoded[5].toInt() xor 0xFF).toByte()
        
        assertNull(SosBeaconCodec.decode(encoded))
    }
}
