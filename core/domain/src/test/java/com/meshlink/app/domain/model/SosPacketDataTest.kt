package com.meshlink.app.domain.model

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SosPacketDataTest {

    @Test
    fun `toJson should produce valid JSON when message contains multiline text and backslashes`() {
        val multilineMessage = """
            SOS! [MED_EVAC] I need help!
            Location: https://maps.google.com/?q=12.9716,77.5946
            Medical Info:
            Blood: O+
            Allergies: Penicillin, "Peanuts"
            Meds: C:\Meds\Insulin.txt / 500mg
            Unicode: ⚠️ 🆘
        """.trimIndent()

        val packet = SosPacketData(
            senderId = "test-device-123",
            senderName = "Alice \"Medic\" Smith",
            emergencyType = SosPacketData.EmergencyType.MEDICAL,
            severity = 5,
            latitude = 12.9716,
            longitude = 77.5946,
            message = multilineMessage
        )

        val jsonString = packet.toJson()
        
        // Proper JSON parser must parse this cleanly
        val jsonElement = JsonParser.parseString(jsonString)
        assertNotNull(jsonElement)
        assertTrue(jsonElement.isJsonObject)
        val jsonObj = jsonElement.asJsonObject
        
        assertEquals(packet.uuid, jsonObj.get("uuid").asString)
        assertEquals(packet.senderId, jsonObj.get("senderId").asString)
        assertEquals(packet.senderName, jsonObj.get("senderName").asString)
        assertEquals(packet.message, jsonObj.get("message").asString)
        assertEquals(packet.latitude!!, jsonObj.get("latitude").asDouble, 0.0001)
        assertEquals(packet.longitude!!, jsonObj.get("longitude").asDouble, 0.0001)

        // Round-trip verification
        val roundTrip = SosPacketData.fromJson(jsonString)
        assertNotNull(roundTrip)
        assertEquals(packet.uuid, roundTrip!!.uuid)
        assertEquals(packet.senderId, roundTrip.senderId)
        assertEquals(packet.senderName, roundTrip.senderName)
        assertEquals(packet.message, roundTrip.message)
        assertEquals(packet.latitude!!, roundTrip.latitude!!, 0.0001)
        assertEquals(packet.longitude!!, roundTrip.longitude!!, 0.0001)
        assertTrue(roundTrip.hasValidLocation)
    }

    @Test
    fun `location validation explicitly represents missing and invalid GPS`() {
        val missingLocation = SosPacketData(
            senderId = "test-1",
            latitude = null,
            longitude = null,
            message = "Help no GPS"
        )
        assertFalse(missingLocation.hasValidLocation)
        val missingJson = missingLocation.toJson()
        val parsedMissing = SosPacketData.fromJson(missingJson)
        assertNotNull(parsedMissing)
        assertNull(parsedMissing!!.latitude)
        assertNull(parsedMissing.longitude)
        assertFalse(parsedMissing.hasValidLocation)

        // Invalid bounds: Latitude > 90
        val outOfBounds = SosPacketData(
            senderId = "test-2",
            latitude = 95.0,
            longitude = 50.0,
            message = "Corrupted GPS"
        )
        assertFalse(outOfBounds.hasValidLocation)
        val outOfBoundsJson = outOfBounds.toJson()
        val parsedOob = SosPacketData.fromJson(outOfBoundsJson)
        assertNotNull(parsedOob)
        assertNull(parsedOob!!.latitude)
        assertNull(parsedOob.longitude)
        assertFalse(parsedOob.hasValidLocation)
    }

    @Test
    fun `backward compatibility - tolerant parser accepts old format payload with 0_0 coordinates`() {
        val legacyJson = """
            {"uuid":"legacy-uuid-1","senderId":"dev-old","senderName":"Old Device","emergencyType":"FIRE","severity":4,"latitude":0.0,"longitude":0.0,"message":"Old SOS","timestamp":1000,"hopCount":0,"status":"PENDING"}
        """.trimIndent()

        val parsed = SosPacketData.fromJson(legacyJson)
        assertNotNull(parsed)
        assertEquals("legacy-uuid-1", parsed!!.uuid)
        assertEquals(SosPacketData.EmergencyType.FIRE, parsed.emergencyType)
        // 0.0 is technically within [-90,90] and [-180,180] so valid lat/lon, but safely parsed
        assertEquals(0.0, parsed.latitude!!, 0.0001)
        assertEquals(0.0, parsed.longitude!!, 0.0001)
    }
}
