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
        assertFalse("0.0, 0.0 Null Island coordinates must not be considered valid location", parsed.hasValidLocation)
    }

    @Test
    fun `hasValidLocation returns false for 0_0 null island placeholder coordinates`() {
        val nullIsland = SosPacketData(
            senderId = "test-null-island",
            latitude = 0.0,
            longitude = 0.0,
            message = "Uninitialized GPS"
        )
        assertFalse(nullIsland.hasValidLocation)
        val json = nullIsland.toJson()
        val reparsed = SosPacketData.fromJson(json)
        assertNotNull(reparsed)
        assertFalse(reparsed!!.hasValidLocation)
        assertNull(reparsed.latitude)
        assertNull(reparsed.longitude)
    }

    @Test
    fun `serialized SOS payload for worst realistic case must not exceed transport limit of 32KB`() {
        val largeMedicalNotes = buildString {
            append("PATIENT MEDICAL DOSSIER:\n")
            append("Condition: Traumatic injury, suspected fracture.\n")
            append("Allergies: Penicillin, NSAIDs, Sulfa drugs, Latex.\n")
            append("Blood Type: AB-negative.\n")
            append("Emergency Contact: Dr. Jane Doe (555-0199).\n")
            append("Notes: ")
            repeat(400) { i ->
                append("Record #$i: Patient vitals stable; severe hemorrhage risk ⚠️; admin IV saline 🆘; path: C:\\Records\\Med$i.dat; ")
            }
        }
        val packet = SosPacketData(
            uuid = "test-uuid-large-1234",
            senderId = "node-alpha-99",
            senderName = "Rescue Medic Alpha",
            emergencyType = SosPacketData.EmergencyType.MEDICAL,
            severity = 5,
            latitude = -34.6037,
            longitude = -58.3816,
            message = largeMedicalNotes
        )

        val jsonString = packet.toJson()
        val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)

        // Google Nearby Connections Payload.MAX_BYTES is 32,768 (32 KB); reserve envelope headroom
        val transportLimit = 32_000
        assertTrue(
            "Serialized SOS byte size (${jsonBytes.size}) must not exceed transport limit ($transportLimit bytes)",
            jsonBytes.size <= transportLimit
        )

        val parsed = SosPacketData.fromJson(jsonString)
        assertNotNull(parsed)
        assertEquals("test-uuid-large-1234", parsed!!.uuid)
        assertEquals("node-alpha-99", parsed.senderId)
        assertEquals(-34.6037, parsed.latitude!!, 0.0001)
        assertEquals(-58.3816, parsed.longitude!!, 0.0001)
        assertTrue(parsed.message.contains("[TRUNCATED: Medical info exceeded transport limit]"))
    }

    @Test
    fun `golden test - old receiver parser accepts new output`() {
        val packet = SosPacketData(
            uuid = "golden-uuid-2026",
            senderId = "node-medic-1",
            senderName = "Medic \"Fast\" Bob",
            emergencyType = SosPacketData.EmergencyType.MEDICAL,
            severity = 4,
            latitude = 45.5017,
            longitude = -73.5673,
            message = "Need oxygen\nBlood: A+\nPath: C:\\Logs\\sos.txt",
            timestamp = 1770000000L,
            hopCount = 2,
            status = SosPacketData.SosStatus.DELIVERED,
            beaconKey = "sos-123456-100-42"
        )

        val newJson = packet.toJson()

        // Old receiver (MeshRouter / NearbyRepository) parsed with standard JSON reader
        val jsonElement = JsonParser.parseString(newJson).asJsonObject
        assertEquals("golden-uuid-2026", jsonElement.get("uuid").asString)
        assertEquals("node-medic-1", jsonElement.get("senderId").asString)
        assertEquals("Medic \"Fast\" Bob", jsonElement.get("senderName").asString)
        assertEquals("MEDICAL", jsonElement.get("emergencyType").asString)
        assertEquals(4, jsonElement.get("severity").asInt)
        assertEquals(45.5017, jsonElement.get("latitude").asDouble, 0.0001)
        assertEquals(-73.5673, jsonElement.get("longitude").asDouble, 0.0001)
        assertEquals("Need oxygen\nBlood: A+\nPath: C:\\Logs\\sos.txt", jsonElement.get("message").asString)
        assertEquals(1770000000L, jsonElement.get("timestamp").asLong)
        assertEquals(2, jsonElement.get("hopCount").asInt)
        assertEquals("DELIVERED", jsonElement.get("status").asString)
        assertEquals("sos-123456-100-42", jsonElement.get("beaconKey").asString)
    }

    @Test
    fun `golden test - new parser accepts old format payloads with missing optional fields and unknown future fields`() {
        // Old payload with only minimal fields (missing senderName, message, status, hopCount, lat/lon)
        val oldMinimalJson = """
            {
                "uuid": "minimal-uuid-77",
                "senderId": "node-legacy-min",
                "emergencyType": "FIRE",
                "severity": 4
            }
        """.trimIndent()

        val parsedMinimal = SosPacketData.fromJson(oldMinimalJson)
        assertNotNull(parsedMinimal)
        assertEquals("minimal-uuid-77", parsedMinimal!!.uuid)
        assertEquals("node-legacy-min", parsedMinimal.senderId)
        assertEquals("", parsedMinimal.senderName)
        assertEquals("", parsedMinimal.message)
        assertEquals(SosPacketData.EmergencyType.FIRE, parsedMinimal.emergencyType)
        assertEquals(4, parsedMinimal.severity)
        assertNull(parsedMinimal.latitude)
        assertNull(parsedMinimal.longitude)
        assertFalse(parsedMinimal.hasValidLocation)
        assertEquals(0, parsedMinimal.hopCount)
        assertEquals(SosPacketData.SosStatus.PENDING, parsedMinimal.status)
        assertNull(parsedMinimal.beaconKey)

        // Old payload with unknown extra fields (forward compatibility)
        val futureJson = """
            {
                "uuid": "future-uuid-88",
                "senderId": "node-future-99",
                "senderName": "Future Explorer",
                "emergencyType": "SECURITY",
                "severity": 3,
                "latitude": 10.0,
                "longitude": 20.0,
                "message": "Future SOS",
                "timestamp": 123456789,
                "hopCount": 1,
                "status": "RELAYED",
                "futureFieldA": "unknown_value",
                "futureFieldB": 999,
                "futureArray": [1, 2, 3],
                "futureObject": {"nested": true}
            }
        """.trimIndent()

        val parsedFuture = SosPacketData.fromJson(futureJson)
        assertNotNull(parsedFuture)
        assertEquals("future-uuid-88", parsedFuture!!.uuid)
        assertEquals("node-future-99", parsedFuture.senderId)
        assertEquals(10.0, parsedFuture.latitude!!, 0.0001)
        assertEquals(20.0, parsedFuture.longitude!!, 0.0001)
        assertTrue(parsedFuture.hasValidLocation)
        assertEquals("Future SOS", parsedFuture.message)
    }
}
