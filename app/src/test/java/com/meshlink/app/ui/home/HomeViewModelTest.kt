package com.meshlink.app.ui.home

import app.cash.turbine.test
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.KnownDevice
import com.meshlink.app.domain.model.Message
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var messageRepo: MessageRepository
    private lateinit var deviceRepo: DeviceRepository
    private lateinit var nearbyRepo: NearbyRepository
    private lateinit var pendingMessageRepo: PendingMessageRepository
    private lateinit var viewModel: HomeViewModel

    private val localDeviceId = "local-device-id"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        messageRepo = mockk(relaxed = true)
        deviceRepo  = mockk(relaxed = true)
        nearbyRepo  = mockk(relaxed = true)
        pendingMessageRepo = mockk(relaxed = true)

        // Default: no connections
        every { nearbyRepo.connectionStates } returns MutableStateFlow(emptyMap())
        every { pendingMessageRepo.getPendingCountFlow() } returns flowOf(0)
        every { nearbyRepo.scanStrategy } returns MutableStateFlow(com.meshlink.app.domain.model.ScanStrategy.CONTINUOUS)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `conversations is empty when no messages`() = runTest {
        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(emptyList())
        every { deviceRepo.getAllDevices() }                    returns flowOf(emptyList())

        viewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, pendingMessageRepo, localDeviceId)

        viewModel.conversations.test {
            assertTrue(awaitItem().isEmpty()) // initial empty state
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `conversations maps peer name from device repository`() = runTest {
        val now = System.currentTimeMillis()
        val msg = Message(
            id         = "msg-1",
            senderId   = "peer-id",
            receiverId = localDeviceId,
            ciphertext = "hello".toByteArray(),
            timestamp  = now,
            delivered  = true
        )
        val device = KnownDevice(
            deviceId    = "peer-id",
            displayName = "Alice",
            publicKey   = ByteArray(0),
            lastSeen    = now
        )
        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(listOf(msg))
        every { deviceRepo.getAllDevices() }                    returns flowOf(listOf(device))

        viewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, pendingMessageRepo, localDeviceId)

        viewModel.conversations.test {
            assertTrue(awaitItem().isEmpty()) // initial state
            
            val conversations = awaitItem() // computed state
            assertEquals(1, conversations.size)
            assertEquals("Alice", conversations[0].deviceName)
            assertEquals("peer-id", conversations[0].deviceId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `conversations falls back to truncated deviceId when device not in repo`() = runTest {
        val now = System.currentTimeMillis()
        val msg = Message(
            id         = "msg-1",
            senderId   = "unknown-peer-device",
            receiverId = localDeviceId,
            ciphertext = "hello".toByteArray(),
            timestamp  = now,
            delivered  = false
        )
        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(listOf(msg))
        every { deviceRepo.getAllDevices() }                    returns flowOf(emptyList())

        viewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, pendingMessageRepo, localDeviceId)

        viewModel.conversations.test {
            assertTrue(awaitItem().isEmpty()) // initial state
            
            val conversations = awaitItem() // computed state
            assertEquals(1, conversations.size)
            assertEquals("unknown-", conversations[0].deviceName)  // take(8)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `conversations are sorted newest-first`() = runTest {
        val older = Message("msg-1", "peer-a", localDeviceId, "old".toByteArray(), 1_000L, true)
        val newer = Message("msg-2", "peer-b", localDeviceId, "new".toByteArray(), 9_000L, true)

        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(listOf(older, newer))
        every { deviceRepo.getAllDevices() }                    returns flowOf(emptyList())

        viewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, pendingMessageRepo, localDeviceId)

        viewModel.conversations.test {
            assertTrue(awaitItem().isEmpty()) // initial state
            
            val conversations = awaitItem() // computed state
            assertEquals(2, conversations.size)
            assertTrue(conversations[0].timestamp > conversations[1].timestamp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `renameDevice delegates to deviceRepository`() = runTest {
        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(emptyList())
        every { deviceRepo.getAllDevices() }                    returns flowOf(emptyList())

        viewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, pendingMessageRepo, localDeviceId)

        viewModel.renameDevice("peer-id", "  Bob  ")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { deviceRepo.updateDisplayName("peer-id", "Bob") }
    }

    @Test
    fun `peerCount reflects connected peers`() = runTest {
        val states = MutableStateFlow(
            mapOf(
                "ep-1" to ConnectionState.CONNECTED,
                "ep-2" to ConnectionState.CONNECTED,
                "ep-3" to ConnectionState.CONNECTING
            )
        )
        every { nearbyRepo.connectionStates } returns states
        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(emptyList())
        every { deviceRepo.getAllDevices() }                    returns flowOf(emptyList())

        viewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, pendingMessageRepo, localDeviceId)

        viewModel.peerCount.test {
            assertEquals(2, awaitItem()) // initial state and computed are the same
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun `peerCount updates correctly upon state changes`() = runTest(kotlinx.coroutines.test.UnconfinedTestDispatcher()) {
        val statesFlow = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
        every { nearbyRepo.connectionStates } returns statesFlow
        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(emptyList())
        every { deviceRepo.getAllDevices() }                    returns flowOf(emptyList())

        viewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, pendingMessageRepo, localDeviceId)

        val collectJob = backgroundScope.launch {
            viewModel.peerCount.collect {}
        }
        testDispatcher.scheduler.runCurrent()
        assertEquals(0, viewModel.peerCount.value)

        statesFlow.value = mapOf("ep-1" to ConnectionState.CONNECTED)
        testDispatcher.scheduler.runCurrent()
        assertEquals(1, viewModel.peerCount.value)

        statesFlow.value = mapOf("ep-1" to ConnectionState.CONNECTED, "ep-2" to ConnectionState.AUTHENTICATED)
        testDispatcher.scheduler.runCurrent()
        assertEquals(2, viewModel.peerCount.value)

        statesFlow.value = mapOf("ep-2" to ConnectionState.AUTHENTICATED)
        testDispatcher.scheduler.runCurrent()
        assertEquals(1, viewModel.peerCount.value)

        statesFlow.value = mapOf(
            "ep-2" to ConnectionState.AUTHENTICATED,
            "ep-3" to ConnectionState.DISCONNECTED,
            "ep-4" to ConnectionState.CONNECTING,
            "ep-5" to ConnectionState.DISCOVERED
        )
        testDispatcher.scheduler.runCurrent()
        assertEquals(1, viewModel.peerCount.value)

        collectJob.cancel()
    }
    
    @Test
    fun `PAUSED strategy with active connections yields CONNECTED or SECURE_ACTIVE`() = runTest {
        val statesFlow = MutableStateFlow(mapOf("ep-1" to ConnectionState.CONNECTED))
        val strategyFlow = MutableStateFlow(com.meshlink.app.domain.model.ScanStrategy.PAUSED)
        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(emptyList())
        every { deviceRepo.getAllDevices() }                    returns flowOf(emptyList())
        every { nearbyRepo.connectionStates }                   returns statesFlow
        every { nearbyRepo.scanStrategy }                       returns strategyFlow
        every { pendingMessageRepo.getPendingCountFlow() }      returns flowOf(0)

        val viewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, pendingMessageRepo, localDeviceId)
        
        viewModel.networkState.test {
            // First item should be CONNECTED since we start with CONNECTED and PAUSED
            assertEquals(MeshNetworkState.CONNECTED, awaitItem())
            
            // Transition to AUTHENTICATED should emit SECURE_ACTIVE
            statesFlow.value = mapOf("ep-1" to ConnectionState.AUTHENTICATED)
            assertEquals(MeshNetworkState.SECURE_ACTIVE, awaitItem())
            
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun `networkState reflects highest priority connection state`() = runTest {
        val testCases = listOf(
            Triple(mapOf("ep-1" to ConnectionState.AUTHENTICATED, "ep-2" to ConnectionState.CONNECTED), com.meshlink.app.domain.model.ScanStrategy.CONTINUOUS, MeshNetworkState.SECURE_ACTIVE),
            Triple(mapOf("ep-1" to ConnectionState.CONNECTED, "ep-2" to ConnectionState.CONNECTING), com.meshlink.app.domain.model.ScanStrategy.CONTINUOUS, MeshNetworkState.CONNECTED),
            Triple(mapOf("ep-1" to ConnectionState.CONNECTING), com.meshlink.app.domain.model.ScanStrategy.CONTINUOUS, MeshNetworkState.CONNECTING),
            Triple(mapOf("ep-1" to ConnectionState.HANDSHAKING), com.meshlink.app.domain.model.ScanStrategy.CONTINUOUS, MeshNetworkState.CONNECTING),
            Triple(mapOf("ep-1" to ConnectionState.DISCOVERED), com.meshlink.app.domain.model.ScanStrategy.CONTINUOUS, MeshNetworkState.SCANNING),
            Triple(emptyMap<String, ConnectionState>(), com.meshlink.app.domain.model.ScanStrategy.CONTINUOUS, MeshNetworkState.SCANNING),
            Triple(emptyMap<String, ConnectionState>(), com.meshlink.app.domain.model.ScanStrategy.PAUSED, MeshNetworkState.PAUSED),
            Triple(mapOf("ep-1" to ConnectionState.DISCOVERED), com.meshlink.app.domain.model.ScanStrategy.PAUSED, MeshNetworkState.PAUSED),
            Triple(mapOf("ep-1" to ConnectionState.CONNECTED), com.meshlink.app.domain.model.ScanStrategy.PAUSED, MeshNetworkState.CONNECTED),
            Triple(mapOf("ep-1" to ConnectionState.AUTHENTICATED), com.meshlink.app.domain.model.ScanStrategy.PAUSED, MeshNetworkState.SECURE_ACTIVE)
        )
        
        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(emptyList())
        every { deviceRepo.getAllDevices() }                    returns flowOf(emptyList())

        testCases.forEach { (statesMap, strategy, expected) ->
            val statesFlow = MutableStateFlow(statesMap)
            val strategyFlow = MutableStateFlow(strategy)
            
            every { nearbyRepo.connectionStates } returns statesFlow
            every { nearbyRepo.scanStrategy } returns strategyFlow

            val tempViewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, pendingMessageRepo, localDeviceId)
            
            tempViewModel.networkState.test {
                val state = awaitItem()
                assertEquals("Failed for case: states=$statesMap, strategy=$strategy", expected, state)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `networkState reacts dynamically to flow emissions`() = runTest(kotlinx.coroutines.test.UnconfinedTestDispatcher()) {
        val statesFlow = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
        val strategyFlow = MutableStateFlow(com.meshlink.app.domain.model.ScanStrategy.CONTINUOUS)
        
        every { nearbyRepo.connectionStates } returns statesFlow
        every { nearbyRepo.scanStrategy } returns strategyFlow
        every { messageRepo.getLatestMessagePerConversation() } returns flowOf(emptyList())
        every { deviceRepo.getAllDevices() }                    returns flowOf(emptyList())

        viewModel = HomeViewModel(messageRepo, deviceRepo, nearbyRepo, pendingMessageRepo, localDeviceId)

        // Keep the StateFlow active so combine block is executed
        val collectJob = backgroundScope.launch {
            viewModel.networkState.collect {}
        }

        // Give stateIn a chance to collect
        testDispatcher.scheduler.runCurrent()
        assertEquals(MeshNetworkState.SCANNING, viewModel.networkState.value) // initial state
        
        // CONNECTING
        statesFlow.value = mapOf("ep-1" to ConnectionState.CONNECTING)
        testDispatcher.scheduler.runCurrent()
        assertEquals(MeshNetworkState.CONNECTING, viewModel.networkState.value)
        
        // CONNECTED
        statesFlow.value = mapOf("ep-1" to ConnectionState.CONNECTED)
        testDispatcher.scheduler.runCurrent()
        assertEquals(MeshNetworkState.CONNECTED, viewModel.networkState.value)
        
        // AUTHENTICATED
        statesFlow.value = mapOf("ep-1" to ConnectionState.AUTHENTICATED)
        testDispatcher.scheduler.runCurrent()
        assertEquals(MeshNetworkState.SECURE_ACTIVE, viewModel.networkState.value)
        
        // DISCONNECTED -> SCANNING
        statesFlow.value = emptyMap()
        testDispatcher.scheduler.runCurrent()
        assertEquals(MeshNetworkState.SCANNING, viewModel.networkState.value)
        
        // PAUSED
        strategyFlow.value = com.meshlink.app.domain.model.ScanStrategy.PAUSED
        testDispatcher.scheduler.runCurrent()
        assertEquals(MeshNetworkState.PAUSED, viewModel.networkState.value)
        
        // RESUMED
        strategyFlow.value = com.meshlink.app.domain.model.ScanStrategy.CONTINUOUS
        testDispatcher.scheduler.runCurrent()
        assertEquals(MeshNetworkState.SCANNING, viewModel.networkState.value)
        
        // reconnect
        statesFlow.value = mapOf("ep-1" to ConnectionState.CONNECTING)
        testDispatcher.scheduler.runCurrent()
        assertEquals(MeshNetworkState.CONNECTING, viewModel.networkState.value)
        
        // a stale DISCONNECTED entry not counted
        statesFlow.value = mapOf("ep-1" to ConnectionState.CONNECTING, "ep-2" to ConnectionState.DISCONNECTED)
        testDispatcher.scheduler.runCurrent()
        assertEquals(MeshNetworkState.CONNECTING, viewModel.networkState.value)
        
        // DISCOVERED while PAUSED
        statesFlow.value = emptyMap()
        strategyFlow.value = com.meshlink.app.domain.model.ScanStrategy.PAUSED
        testDispatcher.scheduler.runCurrent()
        assertEquals(MeshNetworkState.PAUSED, viewModel.networkState.value)
        
        statesFlow.value = mapOf("ep-1" to ConnectionState.DISCOVERED, "ep-2" to ConnectionState.DISCONNECTED)
        testDispatcher.scheduler.runCurrent()
        assertEquals(MeshNetworkState.PAUSED, viewModel.networkState.value)
        
        collectJob.cancel()
    }
}
