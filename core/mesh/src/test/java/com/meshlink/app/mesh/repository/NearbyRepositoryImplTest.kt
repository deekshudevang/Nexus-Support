package com.meshlink.app.mesh.repository

import com.google.android.gms.common.api.Status
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.meshlink.app.crypto.session.HandshakeManager
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.repository.UserProfileManager
import com.meshlink.app.mesh.battery.AdaptiveScanController
import com.meshlink.app.mesh.routing.RoutingTable
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class NearbyRepositoryImplTest {

    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var handshakeManager: HandshakeManager
    private lateinit var routingTable: RoutingTable
    private lateinit var adaptiveScanController: AdaptiveScanController
    private lateinit var repo: NearbyRepositoryImpl
    private lateinit var connectionCallback: ConnectionLifecycleCallback
    private lateinit var discoveryCallback: EndpointDiscoveryCallback

    private fun extractConnectionCallback(): ConnectionLifecycleCallback {
        val field = NearbyRepositoryImpl::class.java.getDeclaredField("connectionLifecycleCallback")
        field.isAccessible = true
        return field.get(repo) as ConnectionLifecycleCallback
    }

    private fun extractDiscoveryCallback(): EndpointDiscoveryCallback {
        val field = NearbyRepositoryImpl::class.java.getDeclaredField("endpointDiscoveryCallback")
        field.isAccessible = true
        return field.get(repo) as EndpointDiscoveryCallback
    }

    private fun setInternalState(field: String, key: String, value: Any) {
        val f = NearbyRepositoryImpl::class.java.getDeclaredField(field)
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = f.get(repo) as ConcurrentHashMap<String, Any>
        map[key] = value
    }

    private fun forceState(endpointId: String, state: ConnectionState) {
        val field = NearbyRepositoryImpl::class.java.getDeclaredField("_connectionStates")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(repo) as MutableStateFlow<Map<String, ConnectionState>>
        flow.value = flow.value + (endpointId to state)
    }

    @Before
    fun setUp() {
        val voidTask = mockk<com.google.android.gms.tasks.Task<Void>>(relaxed = true)
        every { voidTask.addOnSuccessListener(any()) } returns voidTask
        every { voidTask.addOnFailureListener(any()) } returns voidTask

        connectionsClient = mockk(relaxed = true)
        every { connectionsClient.requestConnection(any<String>(), any(), any()) } returns voidTask
        every { connectionsClient.acceptConnection(any(), any()) } returns voidTask

        handshakeManager = mockk(relaxed = true)
        routingTable = mockk(relaxed = true)
        every { routingTable.graphFlow } returns MutableStateFlow(emptyMap())

        adaptiveScanController = mockk(relaxed = true)
        every { adaptiveScanController.scanStrategy } returns MutableStateFlow(
            com.meshlink.app.domain.model.ScanStrategy.CONTINUOUS
        )

        val userProfileManager = mockk<UserProfileManager>(relaxed = true)
        every { userProfileManager.getDisplayName() } returns "test-local-device"

        repo = NearbyRepositoryImpl(
            connectionsClient = connectionsClient,
            localDeviceId = "local-device",
            messageRepository = mockk(relaxed = true),
            deviceRepository = mockk(relaxed = true),
            handshakeManager = handshakeManager,
            encryptionService = mockk(relaxed = true),
            sessionKeyStore = mockk(relaxed = true),
            meshRouter = mockk(relaxed = true),
            routingTable = routingTable,
            adaptiveScanController = adaptiveScanController,
            batteryMonitor = mockk(relaxed = true),
            userProfileManager = userProfileManager,
            meshtasticTransport = mockk(relaxed = true),
            locationSyncManager = mockk(relaxed = true),
            sosBeaconAdvertiser = mockk(relaxed = true)
        )

        connectionCallback = extractConnectionCallback()
        discoveryCallback = extractDiscoveryCallback()
    }

    @Test
    fun `onDisconnected cleans up crypto session, invalidates routes, and sets DISCONNECTED`() {
        val endpointId = "ep-disconnect-1"
        val peerDeviceId = "peer-device-123"

        forceState(endpointId, ConnectionState.AUTHENTICATED)
        setInternalState("endpointIdToDeviceId", endpointId, peerDeviceId)
        setInternalState("nameToEndpointId", "PeerNode", endpointId)

        // Trigger onDisconnected callback
        connectionCallback.onDisconnected(endpointId)

        // 1. Handshake session cleared
        verify(atLeast = 1) { handshakeManager.clearSession(endpointId) }

        // 2. Routes invalidated for peer in routing table
        verify(atLeast = 1) { routingTable.removeRoutesFor(peerDeviceId) }

        // 3. Connection state transitioned to DISCONNECTED
        assertEquals(ConnectionState.DISCONNECTED, repo.connectionStates.value[endpointId])

        // 4. Adaptive scan woken up to resume discovery
        verify(atLeast = 1) { adaptiveScanController.onUserActive() }
    }

    @Test
    fun `onConnectionResult failure transitions state to DISCONNECTED`() {
        val endpointId = "ep-fail-1"
        forceState(endpointId, ConnectionState.CONNECTING)

        val failedResolution = ConnectionResolution(Status(13, "Connection rejected or timed out"))
        connectionCallback.onConnectionResult(endpointId, failedResolution)

        assertEquals(ConnectionState.DISCONNECTED, repo.connectionStates.value[endpointId])
    }

    @Test
    fun `onConnectionResult success transitions state to HANDSHAKING`() {
        val endpointId = "ep-success-1"
        forceState(endpointId, ConnectionState.CONNECTING)

        val successResolution = ConnectionResolution(Status.RESULT_SUCCESS)
        connectionCallback.onConnectionResult(endpointId, successResolution)

        assertEquals(ConnectionState.HANDSHAKING, repo.connectionStates.value[endpointId])
    }

    @Test
    fun `onEndpointFound populates discovered devices and auto-connects when tie-breaker won`() {
        val endpointId = "ep-found-1"
        val info = mockk<DiscoveredEndpointInfo>(relaxed = true)
        // Local name is "test-local-device", which is > "PeerAlpha"
        every { info.endpointName } returns "PeerAlpha"

        discoveryCallback.onEndpointFound(endpointId, info)

        val devices = repo.discoveredDevices.value
        val found = devices.firstOrNull { it.endpointId == endpointId }
        assertNotNull(found)
        assertEquals("PeerAlpha", found?.name)
        // Initiates connection immediately, so state transitions to CONNECTING
        assertEquals(ConnectionState.CONNECTING, repo.connectionStates.value[endpointId])
    }

    @Test
    fun `onEndpointFound waits for peer when tie-breaker lost`() {
        val endpointId = "ep-found-2"
        val info = mockk<DiscoveredEndpointInfo>(relaxed = true)
        // Local name is "test-local-device", which is < "zzz-peer"
        every { info.endpointName } returns "zzz-peer"

        discoveryCallback.onEndpointFound(endpointId, info)

        val devices = repo.discoveredDevices.value
        val found = devices.firstOrNull { it.endpointId == endpointId }
        assertNotNull(found)
        assertEquals("zzz-peer", found?.name)
        // Waits for peer to initiate connection, so state remains DISCOVERED
        assertEquals(ConnectionState.DISCOVERED, repo.connectionStates.value[endpointId])
    }

    @Test
    fun `checkStalePeers marks authenticated peers with expired heartbeat as DISCONNECTED`() {
        val endpointId = "ep-stale-1"
        val peerDeviceId = "peer-stale-device"
        forceState(endpointId, ConnectionState.AUTHENTICATED)
        setInternalState("endpointIdToDeviceId", endpointId, peerDeviceId)

        val now = System.currentTimeMillis()
        // Set last seen timestamp to 100 seconds ago (exceeding 90s PEER_HEARTBEAT_TIMEOUT_MS)
        val expiredTime = now - 100_000L
        setInternalState("lastSeenTimestamps", endpointId, expiredTime)

        // Invoke checkStalePeers directly on repository
        val staleList = repo.checkStalePeers(now = now, timeoutMs = 90_000L)

        // Verify peer was reported as stale, transitioned to DISCONNECTED, and routes removed
        assertTrue("Stale list should contain the expired endpointId", staleList.contains(endpointId))
        assertEquals(ConnectionState.DISCONNECTED, repo.connectionStates.value[endpointId])
        verify(atLeast = 1) { routingTable.removeRoutesFor(peerDeviceId) }
    }
}
