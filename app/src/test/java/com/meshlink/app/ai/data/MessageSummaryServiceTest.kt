package com.meshlink.app.ai.data

import com.meshlink.app.ai.coordinator.OfflineAiCoordinatorImpl
import com.meshlink.app.ai.domain.LocalAiEngine
import com.meshlink.app.ai.model.AiError
import com.meshlink.app.ai.model.AiResult
import com.meshlink.app.domain.model.Message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import com.meshlink.app.ai.fakes.FakeLocalAiEngine
import com.meshlink.app.ai.fakes.FakeModelAssetManager

@OptIn(ExperimentalCoroutinesApi::class)
class MessageSummaryServiceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var coordinator: OfflineAiCoordinatorImpl
    private lateinit var mockEngine: FakeLocalAiEngine
    private lateinit var mockAssetManager: FakeModelAssetManager
    private lateinit var summaryService: MessageSummaryServiceImpl

    @Before
    fun setup() {
        mockEngine = FakeLocalAiEngine().apply { initialized = true }
        mockAssetManager = FakeModelAssetManager().apply {
            modelFileToReturn = java.io.File.createTempFile("test_gemma", ".bin").apply { deleteOnExit() }
        }
        coordinator = OfflineAiCoordinatorImpl(
            modelAssetManager = mockAssetManager,
            localAiEngine = mockEngine,
            dispatcher = testDispatcher
        )
        summaryService = MessageSummaryServiceImpl(coordinator, mockEngine)
    }

    @Test
    fun summarizeMessages_emptyList_returnsImmediateInformationalSuccessWithoutCallingEngine() = testScope.runTest {
        coordinator.enableAi()
        val result = summaryService.summarizeMessages(emptyList())

        assertTrue(result.isSuccess)
        assertEquals("No messages available to summarize.", (result as AiResult.Success).data)
        assertEquals(null, mockEngine.lastPromptReceived)
    }

    @Test
    fun summarizeMessages_deduplicatesAndOrdersMessagesChronologically() = testScope.runTest {
        coordinator.enableAi()

        val msg1 = Message(
            id = "msg-1",
            senderId = "nodeA",
            receiverId = "me",
            ciphertext = "Flood rising at bridge.".toByteArray(Charsets.UTF_8),
            timestamp = 1000L,
            delivered = true,
            senderName = "Alice"
        )
        val msg2Duplicate = Message(
            id = "msg-1",
            senderId = "nodeA",
            receiverId = "me",
            ciphertext = "Flood rising at bridge.".toByteArray(Charsets.UTF_8),
            timestamp = 1000L,
            delivered = true,
            senderName = "Alice"
        )
        val msg3Older = Message(
            id = "msg-2",
            senderId = "nodeB",
            receiverId = "me",
            ciphertext = "Heavy rain started.".toByteArray(Charsets.UTF_8),
            timestamp = 500L,
            delivered = true,
            senderName = "Bob"
        )

        val result = summaryService.summarizeMessages(listOf(msg1, msg2Duplicate, msg3Older))

        assertTrue(result.isSuccess)
        val capturedPrompt = mockEngine.lastPromptReceived
        assertTrue(capturedPrompt != null)

        // Bob (500L) must appear before Alice (1000L) despite input order
        val bobIndex = capturedPrompt!!.indexOf("Bob: Heavy rain started.")
        val aliceIndex = capturedPrompt.indexOf("Alice: Flood rising at bridge.")

        assertTrue(bobIndex >= 0)
        assertTrue(aliceIndex >= 0)
        assertTrue("Chronological order required: Bob earlier than Alice", bobIndex < aliceIndex)

        // msg-1 must only appear once
        val occurrences = capturedPrompt.split("Alice: Flood rising at bridge.").size - 1
        assertEquals("Duplicate message must be eliminated", 1, occurrences)
    }

    @Test
    fun summarizeMessages_preservesSosFlagInTranscript() = testScope.runTest {
        coordinator.enableAi()

        val sosMsg = Message(
            id = "sos-1",
            senderId = "rescuer",
            receiverId = "me",
            ciphertext = "Need medical kit at sector 4".toByteArray(Charsets.UTF_8),
            timestamp = 2000L,
            delivered = true,
            isSos = true
        )

        val result = summaryService.summarizeMessages(listOf(sosMsg))

        assertTrue(result.isSuccess)
        val prompt = mockEngine.lastPromptReceived!!
        assertTrue("SOS Emergency tag must be rendered in context transcript", prompt.contains("[SOS EMERGENCY]"))
    }

    @Test
    fun summarizeMessages_whenPreemptedBySos_returnsPreemptedFailure() = testScope.runTest {
        coordinator.enableAi()

        // Activate SOS on coordinator
        coordinator.onSosActivated()

        val msg = Message(
            id = "m1",
            senderId = "s1",
            receiverId = "me",
            ciphertext = "Hello".toByteArray(Charsets.UTF_8),
            timestamp = 100L,
            delivered = true
        )

        val result = summaryService.summarizeMessages(listOf(msg))

        assertTrue(result.isFailure)
        val error = (result as AiResult.Failure).error
        assertTrue(error is AiError.SosPriorityPreempted)
    }
}
