package com.meshlink.app.mesh.repository

import com.meshlink.app.mesh.transport.SosBeaconCodec
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

class SosMessageIdTest {
    @Test
    fun testSosMessageIdUniqueAfter1812() {
        // Simulate a time after 18:12 UTC (which is > 65535 seconds since midnight)
        // 19 hours after midnight = 19 * 3600 * 1000 = 68,400,000 ms
        val midnight = System.currentTimeMillis() - (System.currentTimeMillis() % 86_400_000L)
        val time1 = midnight + 68_400_000L // 19:00:00
        val time2 = midnight + 68_402_000L // 19:00:02

        val deviceId = UUID.randomUUID().toString()

        val bytes1 = SosBeaconCodec.encode(
            deviceId,
            0.0,
            0.0,
            SosBeaconCodec.EmergencyType.MEDICAL,
            5,
            time1
        )

        val bytes2 = SosBeaconCodec.encode(
            deviceId,
            0.0,
            0.0,
            SosBeaconCodec.EmergencyType.MEDICAL,
            5,
            time2
        )

        val beacon1 = SosBeaconCodec.decode(bytes1)!!
        val beacon2 = SosBeaconCodec.decode(bytes2)!!

        val msgId1 = "sos-${beacon1.deviceIdHash}-${beacon1.timestampDelta}"
        val msgId2 = "sos-${beacon2.deviceIdHash}-${beacon2.timestampDelta}"

        assertNotEquals("Message IDs should be unique even after 18:12 UTC", msgId1, msgId2)
    }
}
