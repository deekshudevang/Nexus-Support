package com.meshlink.app.mesh.repository

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meshlink.app.crypto.cipher.EncryptionService
import com.meshlink.app.crypto.cipher.EciesService
import com.meshlink.app.crypto.session.SessionKeyStore
import com.meshlink.app.data.local.AppDatabase
import com.meshlink.app.data.local.entity.KnownDeviceEntity
import com.meshlink.app.data.repository.DeviceRepositoryImpl
import com.meshlink.app.data.repository.MessageRepositoryImpl
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.repository.PendingMessageRepository
import com.meshlink.app.domain.repository.UserProfileManager
import com.meshlink.app.mesh.routing.MeshRouter
import com.meshlink.app.mesh.routing.RoutingTable
import com.meshlink.app.mesh.routing.SeenMessageCache
import com.meshlink.app.mesh.transport.SosBeaconCodec
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
class SosLocationPipelineTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var deviceRepository: DeviceRepositoryImpl
    private lateinit var messageRepository: MessageRepositoryImpl
    private lateinit var meshRouter: MeshRouter

    private val myDeviceId = "DEVICE_RECEIVER"
    private val senderDeviceId = "DEVICE_SENDER"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        deviceRepository = DeviceRepositoryImpl(database.deviceDao())
        messageRepository = MessageRepositoryImpl(database.messageDao())

        val routingTable = RoutingTable(myDeviceId)
        val seenCache = SeenMessageCache()
        val encryptionService: EncryptionService = mockk(relaxed = true)
        val eciesService: EciesService = mockk(relaxed = true)
        val sessionKeyStore: SessionKeyStore = mockk(relaxed = true)
        val pendingRepo: PendingMessageRepository = mockk(relaxed = true)
        val userProfileManager: UserProfileManager = mockk(relaxed = true)
        val notificationManager: NotificationManager = mockk(relaxed = true)

        meshRouter = MeshRouter(
            context = context,
            myDeviceId = myDeviceId,
            routingTable = routingTable,
            seenMessageCache = seenCache,
            encryptionService = encryptionService,
            eciesService = eciesService,
            sessionKeyStore = sessionKeyStore,
            messageRepository = messageRepository,
            deviceRepository = deviceRepository,
            pendingMessageRepository = pendingRepo,
            userProfileManager = userProfileManager
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `SosBeaconCodec encodes sentinel when location is null, out of range, or legacy 0_0`() {
        // Valid location
        val validBytes = SosBeaconCodec.encode(
            deviceId = "dev-1",
            latitude = 37.7749,
            longitude = -122.4194,
            emergencyType = SosBeaconCodec.EmergencyType.MEDICAL,
            severity = 5
        )
        val validDecoded = SosBeaconCodec.decode(validBytes)
        assertNotNull(validDecoded)
        assertEquals(37.7749f, validDecoded!!.latitude!!, 0.001f)
        assertEquals(-122.4194f, validDecoded.longitude!!, 0.001f)
        assertTrue(validDecoded.hasValidLocation)

        // Null location
        val nullBytes = SosBeaconCodec.encode(
            deviceId = "dev-2",
            latitude = null,
            longitude = null,
            emergencyType = SosBeaconCodec.EmergencyType.FIRE,
            severity = 4
        )
        val nullDecoded = SosBeaconCodec.decode(nullBytes)
        assertNotNull(nullDecoded)
        assertNull("Latitude must be null when location was null", nullDecoded!!.latitude)
        assertNull("Longitude must be null when location was null", nullDecoded.longitude)
        assertFalse(nullDecoded.hasValidLocation)

        // Legacy (0.0, 0.0) policy: treated as unavailable
        val legacyBytes = SosBeaconCodec.encode(
            deviceId = "dev-3",
            latitude = 0.0,
            longitude = 0.0,
            emergencyType = SosBeaconCodec.EmergencyType.OTHER,
            severity = 3
        )
        val legacyDecoded = SosBeaconCodec.decode(legacyBytes)
        assertNotNull(legacyDecoded)
        assertNull("Legacy 0.0 latitude must be treated as unavailable", legacyDecoded!!.latitude)
        assertNull("Legacy 0.0 longitude must be treated as unavailable", legacyDecoded.longitude)
        assertFalse(legacyDecoded.hasValidLocation)

        // Out of range latitude (> 90.0)
        val oobBytes = SosBeaconCodec.encode(
            deviceId = "dev-4",
            latitude = 95.0,
            longitude = 50.0,
            emergencyType = SosBeaconCodec.EmergencyType.SECURITY,
            severity = 5
        )
        val oobDecoded = SosBeaconCodec.decode(oobBytes)
        assertNotNull(oobDecoded)
        assertNull("Out of range latitude must be encoded as unavailable", oobDecoded!!.latitude)
        assertNull("Out of range longitude must be encoded as unavailable", oobDecoded.longitude)
        assertFalse(oobDecoded.hasValidLocation)
    }

    @Test
    fun `golden test - raw IEEE 754 float reader on unavailable beacon bytes yields NaN and never plots`() = runTest {
        val unavailableBytes = SosBeaconCodec.encode(
            deviceId = "dev-legacy-receiver",
            latitude = null,
            longitude = null,
            emergencyType = SosBeaconCodec.EmergencyType.MEDICAL,
            severity = 4
        )

        // Simulate an OLD raw byte reader reading bytes [4-7] and [8-11] as Little-Endian IEEE 754 float
        val buf = ByteBuffer.wrap(unavailableBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.int // skip hash [0-3]
        val rawLat = buf.float
        val rawLon = buf.float

        assertTrue("Raw latitude bytes must represent Float.NaN sentinel", rawLat.isNaN())
        assertTrue("Raw longitude bytes must represent Float.NaN sentinel", rawLon.isNaN())

        // Feed into MeshRouter via incoming beacon packet
        val packet = MeshPacket(
            senderId = senderDeviceId,
            receiverId = MeshPacket.BROADCAST_DEST,
            content = """{"emergencyType":"MEDICAL","severity":4,"lat":$rawLat,"lon":$rawLon}""",
            timestamp = 1000L,
            type = MeshPacket.PacketType.SOS,
            messageId = "sos-beacon-nan-test",
            originId = senderDeviceId,
            finalDestId = MeshPacket.BROADCAST_DEST,
            priority = 100
        )

        meshRouter.route("sos-beacon", packet, mapOf("ep_1" to senderDeviceId))

        // Real Room DB check: device must not have a fake position
        val deviceInDb = database.deviceDao().getById(senderDeviceId)
        if (deviceInDb != null) {
            deviceInDb.lastLatitude?.let {
                assertNotEquals("Must never plot at Null Island (0.0, 0.0)", 0.0, it, 0.0001)
            }
            deviceInDb.lastLongitude?.let {
                assertNotEquals("Must never plot at Null Island (0.0, 0.0)", 0.0, it, 0.0001)
            }
        }
    }

    @Test
    fun `real DB integration - SOS routes and persists in Room without plotting missing or invalid GPS`() = runTest {
        // Pre-insert known device
        database.deviceDao().upsert(
            KnownDeviceEntity(
                deviceId = senderDeviceId,
                displayName = "Survivor Bob",
                publicKey = ByteArray(32),
                lastSeen = 500L,
                lastLatitude = null,
                lastLongitude = null
            )
        )

        // Case A: Incoming SOS with explicit null / missing GPS
        val noGpsPacket = MeshPacket(
            senderId = senderDeviceId,
            receiverId = MeshPacket.BROADCAST_DEST,
            content = """{"uuid":"sos-no-gps-1","senderId":"$senderDeviceId","emergencyType":"MEDICAL","severity":5,"message":"Trapped in rubble, GPS failed"}""",
            timestamp = 1000L,
            type = MeshPacket.PacketType.SOS,
            messageId = "sos-no-gps-1",
            originId = senderDeviceId,
            finalDestId = MeshPacket.BROADCAST_DEST,
            priority = 10
        )

        meshRouter.route("ep_bob", noGpsPacket, mapOf("ep_bob" to senderDeviceId))

        // Message IS persisted in real Room DB
        val msgInDb = database.messageDao().getByConversation(senderDeviceId).first().find { it.id == "sos-no-gps-1" }
        assertNotNull("SOS message must be persisted in database even without GPS", msgInDb)
        assertTrue(msgInDb!!.isSos)
        assertTrue(msgInDb.delivered)

        // Device location in DB is STILL null, NOT 0.0, 0.0
        val devAfterNoGps = database.deviceDao().getById(senderDeviceId)
        assertNotNull(devAfterNoGps)
        assertNull("Device latitude must remain null when GPS was absent", devAfterNoGps!!.lastLatitude)
        assertNull("Device longitude must remain null when GPS was absent", devAfterNoGps.lastLongitude)

        // Case B: Incoming SOS with legacy 0_0 coordinates (Null Island)
        val legacy00Packet = MeshPacket(
            senderId = senderDeviceId,
            receiverId = MeshPacket.BROADCAST_DEST,
            content = """{"uuid":"sos-legacy-00","senderId":"$senderDeviceId","emergencyType":"FIRE","severity":4,"latitude":0.0,"longitude":0.0,"message":"Old device SOS"}""",
            timestamp = 2000L,
            type = MeshPacket.PacketType.SOS,
            messageId = "sos-legacy-00",
            originId = senderDeviceId,
            finalDestId = MeshPacket.BROADCAST_DEST,
            priority = 10
        )

        meshRouter.route("ep_bob", legacy00Packet, mapOf("ep_bob" to senderDeviceId))

        val devAfterLegacy = database.deviceDao().getById(senderDeviceId)
        assertNull("Device latitude must remain null when legacy (0,0) was received", devAfterLegacy!!.lastLatitude)
        assertNull("Device longitude must remain null when legacy (0,0) was received", devAfterLegacy.lastLongitude)

        // Case C: Incoming SOS with valid coordinates
        val validGpsPacket = MeshPacket(
            senderId = senderDeviceId,
            receiverId = MeshPacket.BROADCAST_DEST,
            content = """{"uuid":"sos-valid-gps","senderId":"$senderDeviceId","emergencyType":"MEDICAL","severity":5,"latitude":12.9716,"longitude":77.5946,"message":"GPS acquired"}""",
            timestamp = 3000L,
            type = MeshPacket.PacketType.SOS,
            messageId = "sos-valid-gps",
            originId = senderDeviceId,
            finalDestId = MeshPacket.BROADCAST_DEST,
            priority = 10
        )

        meshRouter.route("ep_bob", validGpsPacket, mapOf("ep_bob" to senderDeviceId))

        val devAfterValid = database.deviceDao().getById(senderDeviceId)
        assertNotNull(devAfterValid!!.lastLatitude)
        assertNotNull(devAfterValid.lastLongitude)
        assertEquals(12.9716, devAfterValid.lastLatitude!!, 0.0001)
        assertEquals(77.5946, devAfterValid.lastLongitude!!, 0.0001)
    }
}
