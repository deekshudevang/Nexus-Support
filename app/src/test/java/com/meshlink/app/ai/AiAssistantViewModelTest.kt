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
    fun sendMessage_featureFlagOff_doesNotInvokeModel_retainsQueryAndShowsDisabledNotice() = runTest(testDispatcher) {
        isAiEnabled.value = false
        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendMessage("How to treat a burn?")
        advanceUntilIdle()

        coVerify(exactly = 0) { mockRagService.answerQuery(any()) }
        val messages = vm.messages.value
        assertEquals(3, messages.size) // Welcome + User + Disabled Notice
        assertEquals("How to treat a burn?", messages[1].text)
        assertTrue(messages[1].isUser)

        assertFalse(messages[2].isUser)
        assertTrue(messages[2].isWarning)
        assertTrue(messages[2].text.contains("Offline AI is currently disabled"))
        assertFalse(vm.isGenerating.value)
    }

    @Test
    fun sendMessage_modelMissingOrNotReady_showsGuideExcerptsAndImportPrompt() = runTest(testDispatcher) {
        coordinatorState.value = AiCapabilityState.NOT_INSTALLED
        val vm = createViewModel()
        advanceUntilIdle()

        val citation = EmergencySourceCitation(
            guideId = "burns",
            title = "Thermal Burns First Aid",
            section = "Cooling & Dressing",
            revisionOrDate = "v1.0",
            relevanceScore = 0.88f
        )
        val excerptText = "${com.meshlink.app.ai.rag.RagConstants.GUIDE_EXCERPT_PREFIX}\n1. Cool under clean running water for 20 minutes."
        val ragResponse = RagResponse(
            answer = excerptText,
            citations = listOf(citation),
            hasSufficientEvidence = true,
            latencyMs = 30L
        )
        coEvery { mockRagService.answerQuery("How to treat a burn?") } returns AiResult.Success(ragResponse)

        vm.sendMessage("How to treat a burn?")
        advanceUntilIdle()

        val messages = vm.messages.value
        assertEquals(3, messages.size)
        assertEquals("How to treat a burn?", messages[1].text)
        assertTrue(messages[1].isUser)

        val aiMsg = messages[2]
        assertFalse(aiMsg.isUser)
        assertTrue(aiMsg.isExcerptOnly)
        assertTrue(aiMsg.showModelImport)
        assertTrue(aiMsg.text.contains("AI model not installed: showing guide excerpts only"))
        assertEquals(1, aiMsg.citations.size)
        assertEquals("burns", aiMsg.citations[0].guideId)
        assertEquals("Thermal Burns First Aid", aiMsg.citations[0].title)
        assertFalse(vm.isGenerating.value)
    }

    @Test
    fun sendMessage_noEvidence_showsAbstentionMessage() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        val ragResponse = RagResponse(
            answer = com.meshlink.app.ai.rag.RagConstants.ABSTENTION_MESSAGE,
            citations = emptyList(),
            hasSufficientEvidence = false,
            latencyMs = 15L
        )
        coEvery { mockRagService.answerQuery("What is quantum computing?") } returns AiResult.Success(ragResponse)

        vm.sendMessage("What is quantum computing?")
        advanceUntilIdle()

        val messages = vm.messages.value
        assertEquals(3, messages.size)
        assertEquals(com.meshlink.app.ai.rag.RagConstants.ABSTENTION_MESSAGE, messages[2].text)
        assertTrue(messages[2].citations.isEmpty())
        assertFalse(vm.isGenerating.value)
    }

    @Test
    fun sendMessage_cancellation_propagatesAndResetsLoadingState() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        coEvery { mockRagService.answerQuery(any()) } throws kotlinx.coroutines.CancellationException("Coroutines cancelled")

        try {
            vm.sendMessage("Test query")
            advanceUntilIdle()
        } catch (_: kotlinx.coroutines.CancellationException) {
            // expected
        }

        assertFalse(vm.isGenerating.value)
    }

    @Test
    fun sendMessage_loadingReset_allowsSubsequentSendsAfterSuccessErrorOrCancellation() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        // 1. First send fails
        coEvery { mockRagService.answerQuery("Query 1") } returns AiResult.Failure(AiError.RuntimeError("Corrupt"))
        vm.sendMessage("Query 1")
        advanceUntilIdle()
        assertFalse(vm.isGenerating.value)

        // 2. Second send succeeds because loading was reset in finally
        coEvery { mockRagService.answerQuery("Query 2") } returns AiResult.Success(
            RagResponse(answer = "Answer 2", citations = emptyList(), hasSufficientEvidence = true, latencyMs = 10L)
        )
        vm.sendMessage("Query 2")
        advanceUntilIdle()
        assertFalse(vm.isGenerating.value)

        val messages = vm.messages.value
        assertTrue(messages.any { it.text == "Answer 2" })
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
