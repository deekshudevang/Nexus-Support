package com.meshlink.app.mesh.repository

import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.meshlink.app.crypto.cipher.EncryptionService
import com.meshlink.app.crypto.session.HandshakeManager
import com.meshlink.app.crypto.session.SessionKeyStore
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.LocationSyncManager
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.UserProfileManager
import com.meshlink.app.mesh.battery.AdaptiveScanController
import com.meshlink.app.mesh.battery.BatteryMonitor
import com.meshlink.app.mesh.routing.MeshRouter
import com.meshlink.app.mesh.routing.RoutingTable
import com.meshlink.app.mesh.transport.MeshtasticTransport
import com.meshlink.app.mesh.transport.SosBeaconAdvertiser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests for onEndpointLost state-machine behaviour.
 *
 * REGRESSION RATIONALE
 * --------------------
 * Before the fix, onEndpointLost() only removed the peer from _discoveredDevices and
 * nameToEndpointId — it never updated _connectionStates.  A peer that disappeared while
 * in DISCOVERED, CONNECTING, or HANDSHAKING would therefore remain in that state forever,
 * inflating peerCount in HomeViewModel and blocking future reconnection paths.
 *
 * For CONNECTED/AUTHENTICATED we deliberately leave the state unchanged: Nearby can emit
 * onEndpointLost for an advertising-plane disappearance even when the data-plane channel
 * (onDisconnected) is still open.  onDisconnected (+ optional heartbeat) is the correct
 * gate for those states.
 *
 * FIX CONTRACT
 * ------------
 * onEndpointLost MUST set DISCONNECTED if and only if the entry is in
 * {DISCOVERED, CONNECTING, HANDSHAKING}.
 * onEndpointLost MUST NOT alter an entry in {CONNECTED, AUTHENTICATED, DISCONNECTED, null}.
 */
class NearbyRepositoryImplEndpointLostTest {

    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var adaptiveScanController: AdaptiveScanController
    private lateinit var repo: NearbyRepositoryImpl
    private lateinit var discoveryCallback: EndpointDiscoveryCallback

    /** Helper: obtain the private endpointDiscoveryCallback via reflection. */
    private fun extractDiscoveryCallback(): EndpointDiscoveryCallback {
        val field = NearbyRepositoryImpl::class.java.getDeclaredField("endpointDiscoveryCallback")
        field.isAccessible = true
        return field.get(repo) as EndpointDiscoveryCallback
    }

    /** Helper: set _connectionStates directly so we can test a specific starting state
     *  without going through the full Nearby flow for each case. */
    private fun forceState(endpointId: String, state: ConnectionState) {
        val field = NearbyRepositoryImpl::class.java.getDeclaredField("_connectionStates")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(repo) as kotlinx.coroutines.flow.MutableStateFlow<Map<String, ConnectionState>>
        flow.value = flow.value + (endpointId to state)
    }

    /** Helper: read the current connection state for one endpoint. */
    private fun stateOf(endpointId: String): ConnectionState? =
        repo.connectionStates.value[endpointId]

    @Before
    fun setUp() {
        // Nearby Task<Void> stub — returned by requestConnection / acceptConnection / etc.
        val voidTask = mockk<com.google.android.gms.tasks.Task<Void>>(relaxed = true)
        every { voidTask.addOnSuccessListener(any()) } returns voidTask
        every { voidTask.addOnFailureListener(any()) } returns voidTask

        connectionsClient = mockk(relaxed = true)
        every { connectionsClient.requestConnection(any<String>(), any(), any()) } returns voidTask
        every { connectionsClient.acceptConnection(any(), any()) } returns voidTask

        adaptiveScanController = mockk(relaxed = true)
        every { adaptiveScanController.scanStrategy } returns MutableStateFlow(
            com.meshlink.app.domain.model.ScanStrategy.CONTINUOUS
        )

        val routingTable = mockk<RoutingTable>(relaxed = true)
        every { routingTable.graphFlow } returns MutableStateFlow(emptyMap())

        val userProfileManager = mockk<UserProfileManager>(relaxed = true)
        // Use a high local name so tie-breaker never calls requestConnection on endpoint "ep1"
        every { userProfileManager.getDisplayName() } returns "zzz-local"

        repo = NearbyRepositoryImpl(
            connectionsClient       = connectionsClient,
            localDeviceId           = "local-device",
            messageRepository       = mockk(relaxed = true),
            deviceRepository        = mockk(relaxed = true),
            handshakeManager        = mockk(relaxed = true),
            encryptionService       = mockk(relaxed = true),
            sessionKeyStore         = mockk(relaxed = true),
            meshRouter              = mockk(relaxed = true),
            routingTable            = routingTable,
            adaptiveScanController  = adaptiveScanController,
            batteryMonitor          = mockk(relaxed = true),
            userProfileManager      = userProfileManager,
            meshtasticTransport     = mockk(relaxed = true),
            locationSyncManager     = mockk(relaxed = true),
            sosBeaconAdvertiser     = mockk(relaxed = true),
        )

        discoveryCallback = extractDiscoveryCallback()
    }

    // -------------------------------------------------------------------------
    // F4-ITEM2-A: DISCOVERED → DISCONNECTED when onEndpointLost fires
    // -------------------------------------------------------------------------

    /**
     * FAILING before fix: onEndpointLost does not update _connectionStates, so the
     * entry stays as DISCOVERED instead of transitioning to DISCONNECTED.
     */
    @Test
    fun `DISCOVERED peer is set to DISCONNECTED when onEndpointLost fires`() {
        val epId = "ep-discovered"
        forceState(epId, ConnectionState.DISCOVERED)

        discoveryCallback.onEndpointLost(epId)

        assertEquals(
            "onEndpointLost should transition DISCOVERED → DISCONNECTED",
            ConnectionState.DISCONNECTED,
            stateOf(epId)
        )
    }

    // -------------------------------------------------------------------------
    // F4-ITEM2-B: CONNECTING → DISCONNECTED when onEndpointLost fires
    // -------------------------------------------------------------------------

    @Test
    fun `CONNECTING peer is set to DISCONNECTED when onEndpointLost fires`() {
        val epId = "ep-connecting"
        forceState(epId, ConnectionState.CONNECTING)

        discoveryCallback.onEndpointLost(epId)

        assertEquals(
            "onEndpointLost should transition CONNECTING → DISCONNECTED",
            ConnectionState.DISCONNECTED,
            stateOf(epId)
        )
    }

    // -------------------------------------------------------------------------
    // F4-ITEM2-C: HANDSHAKING → DISCONNECTED when onEndpointLost fires
    // -------------------------------------------------------------------------

    @Test
    fun `HANDSHAKING peer is set to DISCONNECTED when onEndpointLost fires`() {
        val epId = "ep-handshaking"
        forceState(epId, ConnectionState.HANDSHAKING)

        discoveryCallback.onEndpointLost(epId)

        assertEquals(
            "onEndpointLost should transition HANDSHAKING → DISCONNECTED",
            ConnectionState.DISCONNECTED,
            stateOf(epId)
        )
    }

    // -------------------------------------------------------------------------
    // F4-ITEM2-D: CONNECTED must NOT be touched by onEndpointLost
    // -------------------------------------------------------------------------

    @Test
    fun `CONNECTED peer is NOT altered by onEndpointLost`() {
        val epId = "ep-connected"
        forceState(epId, ConnectionState.CONNECTED)

        discoveryCallback.onEndpointLost(epId)

        assertEquals(
            "onEndpointLost must NOT change a CONNECTED entry (leave to onDisconnected)",
            ConnectionState.CONNECTED,
            stateOf(epId)
        )
    }

    // -------------------------------------------------------------------------
    // F4-ITEM2-E: AUTHENTICATED must NOT be touched by onEndpointLost
    // -------------------------------------------------------------------------

    @Test
    fun `AUTHENTICATED peer is NOT altered by onEndpointLost`() {
        val epId = "ep-authenticated"
        forceState(epId, ConnectionState.AUTHENTICATED)

        discoveryCallback.onEndpointLost(epId)

        assertEquals(
            "onEndpointLost must NOT change an AUTHENTICATED entry (leave to onDisconnected)",
            ConnectionState.AUTHENTICATED,
            stateOf(epId)
        )
    }

    // -------------------------------------------------------------------------
    // F4-ITEM2-F: absent entry stays absent after onEndpointLost
    // -------------------------------------------------------------------------

    @Test
    fun `absent entry is not inserted by onEndpointLost`() {
        discoveryCallback.onEndpointLost("ep-unknown")

        assertNull(
            "onEndpointLost must not insert a new entry for a peer not in connectionStates",
            stateOf("ep-unknown")
        )
    }


    // -------------------------------------------------------------------------
    // CONNECTED → DISCONNECTED when sendPayload fails (Original Behavior)
    // -------------------------------------------------------------------------

    @Test
    fun `CONNECTED peer is set to DISCONNECTED when sendPayload fails`() = kotlinx.coroutines.runBlocking {
        val epId = "ep-liveness"
        forceState(epId, ConnectionState.CONNECTED)

        // Mock peer device ID to pass the guard in sendPacket
        val mapField = NearbyRepositoryImpl::class.java.getDeclaredField("endpointIdToDeviceId")
        mapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val endpointMap = mapField.get(repo) as MutableMap<String, String>
        endpointMap[epId] = "liveness-device"

        // Mock session key and encryption to pass the coroutine guards
        val sessionKeyStoreField = NearbyRepositoryImpl::class.java.getDeclaredField("sessionKeyStore")
        sessionKeyStoreField.isAccessible = true
        val sessionKeyStore = sessionKeyStoreField.get(repo) as SessionKeyStore
        every { sessionKeyStore.getSessionKey(epId) } returns mockk<javax.crypto.SecretKey>()

        val encryptionServiceField = NearbyRepositoryImpl::class.java.getDeclaredField("encryptionService")
        encryptionServiceField.isAccessible = true
        val encryptionService = encryptionServiceField.get(repo) as EncryptionService
        every { encryptionService.encrypt(any(), any()) } returns ByteArray(16)

        // Mock sendPayload to capture the failure listener
        val failedTask = mockk<com.google.android.gms.tasks.Task<Void>>(relaxed = true)
        val listenerSlot = io.mockk.slot<com.google.android.gms.tasks.OnFailureListener>()
        every { failedTask.addOnFailureListener(capture(listenerSlot)) } returns failedTask
        every { connectionsClient.sendPayload(eq(epId), any()) } returns failedTask

        // Mock Base64 because this runs on JVM
        io.mockk.mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } returns "mock-base64"

        // Call sendPacket which internally calls sendPayload in an IO coroutine
        val packet = com.meshlink.app.domain.model.MeshPacket(
            senderId = "local-device",
            receiverId = "liveness-device",
            content = "hello",
            timestamp = 123456789L
        )
        repo.sendPacket(epId, packet)

        // Wait a short bit for the coroutine to reach sendPayload
        kotlinx.coroutines.delay(100)

        // Verify sendPayload was called
        io.mockk.verify(timeout = 1000) { connectionsClient.sendPayload(eq(epId), any()) }

        // Invoke the failure listener
        listenerSlot.captured.onFailure(Exception("Simulated sendPayload failure"))

        assertEquals(
            "sendPayload failure should transition CONNECTED → DISCONNECTED",
            ConnectionState.DISCONNECTED,
            stateOf(epId)
        )
    }
}
