package com.meshlink.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.meshlink.app.crypto.session.HandshakeManager
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.KnownDevice
import com.meshlink.app.domain.model.Message
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.mesh.battery.BatteryMonitor
import io.mockk.coEvery
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var messageRepo: MessageRepository
    private lateinit var nearbyRepo: NearbyRepository
    private lateinit var deviceRepo: DeviceRepository
    private lateinit var handshakeManager: HandshakeManager
    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var viewModel: ChatViewModel

    private val localDeviceId = "local-node-123"
    private val peerEndpoint = "ep-alice"
    private val peerDeviceId = "sha256-alice-device-id"
    private val peerName = "Alice"

    private val connStatesFlow = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        messageRepo = mockk(relaxed = true)
        nearbyRepo = mockk(relaxed = true)
        deviceRepo = mockk(relaxed = true)
        handshakeManager = mockk(relaxed = true)
        batteryMonitor = mockk(relaxed = true)

        every { batteryMonitor.getBatteryLevel() } returns 85
        every { nearbyRepo.connectionStates } returns connStatesFlow
        every { nearbyRepo.currentEndpointForName(peerName) } returns peerEndpoint
        every { nearbyRepo.peerDeviceIdForEndpoint(peerEndpoint) } returns peerDeviceId
        every { messageRepo.getMessagesByConversation(any()) } returns flowOf(emptyList())
        every { deviceRepo.getAllDevices() } returns flowOf(emptyList())

        val savedStateHandle = SavedStateHandle(
            mapOf("deviceId" to peerEndpoint, "deviceName" to peerName)
        )

        viewModel = ChatViewModel(
            savedStateHandle = savedStateHandle,
            messageRepository = messageRepo,
            nearbyRepository = nearbyRepo,
            deviceRepository = deviceRepo,
            handshakeManager = handshakeManager,
            batteryMonitor = batteryMonitor,
            localDeviceId = localDeviceId
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial properties match savedStateHandle and battery`() {
        assertEquals(peerEndpoint, viewModel.deviceId)
        assertEquals(peerName, viewModel.deviceName)
        assertEquals("85", viewModel.battery.value)
    }

    @Test
    fun `onInputChanged updates inputText`() {
        assertEquals("", viewModel.inputText.value)
        viewModel.onInputChanged("Need medical supplies")
        assertEquals("Need medical supplies", viewModel.inputText.value)
    }

    @Test
    fun `onSendClick clears input and routes packet`() = runTest {
        viewModel.onInputChanged("Bridge is impassable")
        viewModel.onSendClick()

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.inputText.value)
        coVerify(exactly = 1) {
            nearbyRepo.routeToDevice(
                any(),
                match {
                    it.content == "Bridge is impassable" &&
                    it.originId == localDeviceId &&
                    it.senderId == localDeviceId
                }
            )
        }
    }

    @Test
    fun `onSendSafeSignal routes predefined safe message`() = runTest {
        viewModel.onSendSafeSignal()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            nearbyRepo.routeToDevice(
                any(),
                match {
                    it.content.contains("I AM SAFE") &&
                    it.originId == localDeviceId
                }
            )
        }
    }

    @Test
    fun `onSendBroadcast routes via sendBroadcast`() {
        viewModel.onSendBroadcast("Emergency evacuation alert")
        coVerify(exactly = 1) { nearbyRepo.sendBroadcast("Emergency evacuation alert") }
    }

    @Test
    fun `onVerifyContactConfirmed marks device verified in repository`() = runTest {
        viewModel.onVerifyContactConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { deviceRepo.markDeviceAsVerified(any()) }
    }

    @Test
    fun `messages flow collects from messageRepository for peer conversation`() = runTest {
        val testMessage = Message(
            id = "msg-99",
            senderId = peerDeviceId,
            receiverId = localDeviceId,
            ciphertext = "secure text".toByteArray(),
            timestamp = 1000L,
            delivered = true
        )
        every { messageRepo.getMessagesByConversation(peerDeviceId) } returns flowOf(listOf(testMessage))

        // Trigger connection state update so peerDeviceId resolves
        connStatesFlow.value = mapOf(peerEndpoint to ConnectionState.CONNECTED)

        viewModel.messages.test {
            assertEquals(emptyList<Message>(), awaitItem()) // initial stateIn value

            testDispatcher.scheduler.advanceUntilIdle()
            val list = awaitItem()
            assertTrue(list.isNotEmpty())
            assertEquals("msg-99", list[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
