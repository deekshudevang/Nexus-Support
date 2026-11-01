package com.meshlink.app.ai

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meshlink.app.ai.domain.AiRoutingPolicy
import com.meshlink.app.ai.domain.EmergencyRagService
import com.meshlink.app.ai.domain.LocalAiEngine
import com.meshlink.app.ai.domain.MessageSummaryService
import com.meshlink.app.ai.domain.ModelAssetManager
import com.meshlink.app.ai.domain.OfflineAiCoordinator
import com.meshlink.app.ai.model.AiCapabilityState
import com.meshlink.app.ai.model.AiResult
import com.meshlink.app.ai.model.EmergencySourceCitation
import com.meshlink.app.ai.model.RagResponse
import com.meshlink.app.domain.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiAssistantScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

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
        every { mockCoordinator.state } returns coordinatorState
        every { mockCoordinator.isAiEnabled } returns isAiEnabled
        every { mockSpeechManager.spokenText } returns spokenTextFlow
        every { mockSpeechManager.isListening } returns isListeningFlow
        coEvery { mockCoordinator.initialize() } returns AiCapabilityState.READY
    }

    private val mockLocalAiEngine: LocalAiEngine = mockk(relaxed = true)
    private val mockRoutingPolicy: AiRoutingPolicy = mockk(relaxed = true)

    private fun createViewModel(): AiAssistantViewModel {
        return AiAssistantViewModel(
            coordinator = mockCoordinator,
            ragService = mockRagService,
            summaryService = mockSummaryService,
            modelAssetManager = mockAssetManager,
            messageRepository = mockMessageRepository,
            speechManager = mockSpeechManager,
            visionManager = mockVisionManager,
            localAiEngine = mockLocalAiEngine,
            routingPolicy = mockRoutingPolicy
        )
    }

    @Test
    fun sendQuestion_viaSendButton_displaysUserMessageAndTriggersViewModel() {
        val viewModel = createViewModel()
        val query = "What should I do for a serious bleeding injury?"
        val citation = EmergencySourceCitation(
            guideId = "bleeding",
            title = "Severe Bleeding Control",
            section = "Direct Pressure",
            revisionOrDate = "v1.0",
            relevanceScore = 0.96f
        )
        val ragResponse = RagResponse(
            answer = "Apply firm direct pressure with clean cloth immediately.",
            citations = listOf(citation),
            hasSufficientEvidence = true,
            latencyMs = 25L
        )
        coEvery { mockRagService.answerQuery(query) } returns AiResult.Success(ragResponse)

        composeRule.setContent {
            AiAssistantScreen(
                viewModel = viewModel,
                onNavigateBack = {},
                onNavigateToSettings = {}
            )
        }

        // 1. Initial screen displays welcome message
        composeRule.onNodeWithText("Welcome to Nexus", substring = true)
            .assertIsDisplayed()

        // 2. Type non-empty question into text field
        composeRule.onNodeWithText("Ask emergency response...")
            .performTextInput(query)

        // 3. Verify Send action is available and enabled
        composeRule.onNodeWithContentDescription("Send message")
            .assertIsDisplayed()
            .assertIsEnabled()

        // 4. Click Send button
        composeRule.onNodeWithContentDescription("Send message")
            .performClick()

        // 5. Verify user message appears in list and ViewModel action occurred
        composeRule.onNodeWithText(query)
            .assertIsDisplayed()

        coVerify { mockRagService.answerQuery(query) }
    }

    @Test
    fun sendGeneralQuestion_generatesLocalAnswerWithoutGuideCitations() {
        val viewModel = createViewModel()
        val query = "What is photosynthesis?"
        val modelAnswer = "Photosynthesis is the process used by plants to convert light energy into chemical energy."
        coEvery { mockLocalAiEngine.generate(any()) } returns AiResult.Success(modelAnswer)

        composeRule.setContent {
            AiAssistantScreen(
                viewModel = viewModel,
                onNavigateBack = {},
                onNavigateToSettings = {}
            )
        }

        composeRule.onNodeWithText("Ask emergency response...")
            .performTextInput(query)
        composeRule.onNodeWithContentDescription("Send message")
            .performClick()

        composeRule.onNodeWithText(query)
            .assertIsDisplayed()
        composeRule.onNodeWithText(modelAnswer, substring = true)
            .assertIsDisplayed()

        // General questions do not call emergency RAG
        coVerify(exactly = 0) { mockRagService.answerQuery(any()) }
    }

    @Test
    fun sendQuestion_viaImeSendAction_triggersSendAndDisplaysMessage() {
        val viewModel = createViewModel()
        val query = "How to treat heat exhaustion?"
        val citation = EmergencySourceCitation(
            guideId = "heat",
            title = "Heat Stroke & Exhaustion",
            section = "Cooling",
            revisionOrDate = "v1.0",
            relevanceScore = 0.90f
        )
        val ragResponse = RagResponse(
            answer = "Move victim to shade and loosen tight clothing.",
            citations = listOf(citation),
            hasSufficientEvidence = true,
            latencyMs = 20L
        )
        coEvery { mockRagService.answerQuery(query) } returns AiResult.Success(ragResponse)

        composeRule.setContent {
            AiAssistantScreen(
                viewModel = viewModel,
                onNavigateBack = {},
                onNavigateToSettings = {}
            )
        }

        // Type query and trigger IME action
        composeRule.onNodeWithText("Ask emergency response...")
            .performTextInput(query)
        composeRule.onNode(hasSetTextAction())
            .performImeAction()

        // Verify message appeared and ViewModel was invoked
        composeRule.onNodeWithText(query)
            .assertIsDisplayed()

        coVerify { mockRagService.answerQuery(query) }
    }

    @Test
    fun featureFlagDisabled_showsDisabledNotice() {
        isAiEnabled.value = false
        val viewModel = createViewModel()

        composeRule.setContent {
            AiAssistantScreen(
                viewModel = viewModel,
                onNavigateBack = {},
                onNavigateToSettings = {}
            )
        }

        composeRule.onNodeWithText("Ask emergency response...")
            .performTextInput("Hi there!")
        composeRule.onNodeWithContentDescription("Send message")
            .performClick()

        composeRule.onNodeWithText("Offline AI is currently disabled", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun missingModel_onGeneralQuery_displaysImportAction() {
        coordinatorState.value = AiCapabilityState.NOT_INSTALLED
        val viewModel = createViewModel()

        composeRule.setContent {
            AiAssistantScreen(
                viewModel = viewModel,
                onNavigateBack = {},
                onNavigateToSettings = {}
            )
        }

        composeRule.onNodeWithText("Ask emergency response...")
            .performTextInput("Explain binary search")
        composeRule.onNodeWithContentDescription("Send message")
            .performClick()

        composeRule.onNodeWithText("AI model not installed", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Import Model in Settings")
            .assertIsDisplayed()
    }
}
