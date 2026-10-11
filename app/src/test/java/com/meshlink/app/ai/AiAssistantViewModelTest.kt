package com.meshlink.app.ai

import com.meshlink.app.ai.domain.EmergencyRagService
import com.meshlink.app.ai.domain.MessageSummaryService
import com.meshlink.app.ai.domain.ModelAssetManager
import com.meshlink.app.ai.domain.OfflineAiCoordinator
import com.meshlink.app.ai.model.AiCapabilityState
import com.meshlink.app.ai.model.AiError
import com.meshlink.app.ai.model.AiResult
import com.meshlink.app.ai.model.EmergencySourceCitation
import com.meshlink.app.ai.model.RagResponse
import com.meshlink.app.domain.model.Message
import com.meshlink.app.domain.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiAssistantViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockCoordinator: OfflineAiCoordinator = mockk(relaxed = true)
    private val mockRagService: EmergencyRagService = mockk(relaxed = true)
    private val mockSummaryService: MessageSummaryService = mockk(relaxed = true)
    private val mockAssetManager: ModelAssetManager = mockk(relaxed = true)
    private val mockMessageRepository: MessageRepository = mockk(relaxed = true)
    private val mockSpeechManager: SpeechManager = mockk(relaxed = true)
    private val mockVisionManager: VisionManager = mockk(relaxed = true)

    private val coordinatorState = MutableStateFlow(AiCapabilityState.READY)
    private val isAiEnabled = MutableStateFlow(true)
    private val spokenTextFlow = MutableSharedFlow<String>()
    private val isListeningFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockCoordinator.state } returns coordinatorState
        every { mockCoordinator.isAiEnabled } returns isAiEnabled
        every { mockSpeechManager.spokenText } returns spokenTextFlow
        every { mockSpeechManager.isListening } returns isListeningFlow
        coEvery { mockCoordinator.initialize() } returns AiCapabilityState.READY
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AiAssistantViewModel {
        return AiAssistantViewModel(
            coordinator = mockCoordinator,
            ragService = mockRagService,
            summaryService = mockSummaryService,
            modelAssetManager = mockAssetManager,
            messageRepository = mockMessageRepository,
            speechManager = mockSpeechManager,
            visionManager = mockVisionManager
        )
    }

    @Test
    fun init_initializesCoordinatorAndSetsWelcomeMessage() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        coVerify { mockCoordinator.initialize() }
        val messages = vm.messages.value
        assertEquals(1, messages.size)
        assertFalse(messages[0].isUser)
        assertTrue(messages[0].text.contains("Welcome to Nexus Offline Emergency AI"))
    }

    @Test
    fun sendMessage_validQuery_postsUserAndAiMessagesWithCitations() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        val citation = EmergencySourceCitation(
            guideId = "cpr",
            title = "CPR Guide",
            section = "30 compressions",
            revisionOrDate = "2024",
            relevanceScore = 0.95f
        )
        val ragResponse = RagResponse(
            answer = "Begin chest compressions immediately.",
            citations = listOf(citation),
            hasSufficientEvidence = true,
            latencyMs = 45L
        )
        coEvery { mockRagService.answerQuery("How to do CPR?") } returns AiResult.Success(ragResponse)

        vm.sendMessage("How to do CPR?")
        advanceUntilIdle()

        val messages = vm.messages.value
        assertEquals(3, messages.size) // Welcome + User + AI
        assertEquals("How to do CPR?", messages[1].text)
        assertTrue(messages[1].isUser)

        assertEquals("Begin chest compressions immediately.", messages[2].text)
        assertFalse(messages[2].isUser)
        assertEquals(1, messages[2].citations.size)
        assertEquals("cpr", messages[2].citations[0].guideId)

        verify { mockSpeechManager.speak("Begin chest compressions immediately.") }
        assertFalse(vm.isGenerating.value)
    }

    @Test
    fun sendMessage_blankQuery_doesNothing() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendMessage("   ")
        advanceUntilIdle()

        assertEquals(1, vm.messages.value.size) // Only welcome message
        coVerify(exactly = 0) { mockRagService.answerQuery(any()) }
    }

    @Test
    fun sendMessage_onFailure_postsWarningMessage() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        coEvery { mockRagService.answerQuery(any()) } returns AiResult.Failure(AiError.DisabledByKillSwitch)

        vm.sendMessage("Help me")
        advanceUntilIdle()

        val messages = vm.messages.value
        assertEquals(3, messages.size)
        assertTrue(messages[2].isWarning)
        assertTrue(messages[2].text.contains("[AI Error]"))
    }

    @Test
    fun summarizeRecentMeshMessages_fetchesAndSummarizes() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        val mockMessages = listOf(
            Message(
                id = "m1",
                senderId = "s1",
                receiverId = "me",
                ciphertext = "Road flooded".toByteArray(Charsets.UTF_8),
                timestamp = 1000L,
                delivered = true
            )
        )
        every { mockMessageRepository.getLatestMessagePerConversation() } returns flowOf(mockMessages)
        coEvery { mockSummaryService.summarizeMessages(mockMessages) } returns AiResult.Success("Summary: Sector 3 flooded.")

        vm.summarizeRecentMeshMessages()
        advanceUntilIdle()

        val messages = vm.messages.value
        assertEquals(3, messages.size) // Welcome + User request + Summary response
        assertEquals("Summary: Sector 3 flooded.", messages[2].text)
        assertFalse(messages[2].isWarning)
    }
}
