package com.meshlink.app.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.ai.domain.EmergencyRagService
import com.meshlink.app.ai.domain.MessageSummaryService
import com.meshlink.app.ai.domain.ModelAssetManager
import com.meshlink.app.ai.domain.OfflineAiCoordinator
import com.meshlink.app.ai.model.AiCapabilityState
import com.meshlink.app.ai.model.AiResult
import com.meshlink.app.ai.model.EmergencySourceCitation
import com.meshlink.app.ai.rag.RagConstants
import com.meshlink.app.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.meshlink.app.ai.domain.AiRoute
import com.meshlink.app.ai.domain.AiRoutingPolicy
import com.meshlink.app.ai.domain.LocalAiEngine
import com.meshlink.app.ai.model.AiError
import com.meshlink.app.ai.security.AiLogger
import com.meshlink.app.ai.security.AiSafetyGuard
import java.io.File

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val citations: List<EmergencySourceCitation> = emptyList(),
    val isWarning: Boolean = false,
    val isExcerptOnly: Boolean = false,
    val showModelImport: Boolean = false
)

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    val coordinator: OfflineAiCoordinator,
    private val ragService: EmergencyRagService,
    private val summaryService: MessageSummaryService,
    private val modelAssetManager: ModelAssetManager,
    private val messageRepository: MessageRepository,
    private val speechManager: SpeechManager,
    private val visionManager: VisionManager,
    private val localAiEngine: LocalAiEngine,
    val routingPolicy: AiRoutingPolicy
) : ViewModel() {

    // Overloaded secondary constructor for backward compatibility with 7-parameter test instantiations
    constructor(
        coordinator: OfflineAiCoordinator,
        ragService: EmergencyRagService,
        summaryService: MessageSummaryService,
        modelAssetManager: ModelAssetManager,
        messageRepository: MessageRepository,
        speechManager: SpeechManager,
        visionManager: VisionManager
    ) : this(
        coordinator,
        ragService,
        summaryService,
        modelAssetManager,
        messageRepository,
        speechManager,
        visionManager,
        localAiEngine = object : LocalAiEngine {
            override suspend fun initialize(modelFile: File) = AiResult.Success(Unit)
            override suspend fun generate(prompt: String) = AiResult.Success("Default local AI response.")
            override fun generateStream(prompt: String) = kotlinx.coroutines.flow.flowOf(AiResult.Success("Default local AI response."))
            override suspend fun unload() {}
            override fun isReady() = coordinator.state.value == AiCapabilityState.READY
        },
        routingPolicy = AiRoutingPolicy()
    )

    val capabilityState: StateFlow<AiCapabilityState> = coordinator.state
    val isAiEnabled: StateFlow<Boolean> = coordinator.isAiEnabled

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    val isListening: StateFlow<Boolean> = speechManager.isListening
    val spokenText = speechManager.spokenText

    init {
        viewModelScope.launch {
            coordinator.initialize()
            _messages.value = listOf(
                ChatMessage(
                    text = "Welcome to Nexus Offline Emergency AI & General Conversational Assistant. Ask any question regarding general knowledge, coding, studies, or disaster first aid (bleeding, CPR, flood, fire).",
                    isUser = false
                )
            )
        }

        viewModelScope.launch {
            spokenText.collect { text ->
                if (text.isNotBlank()) {
                    sendMessage(text)
                }
            }
        }
    }

    private var currentGenerationJob: kotlinx.coroutines.Job? = null

    fun cancelGeneration() {
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        _isGenerating.value = false
        speechManager.stopSpeaking()
    }

    fun stopSpeaking() {
        speechManager.stopSpeaking()
    }

    fun setSpeechRate(rate: Float) {
        speechManager.setSpeechRate(rate)
    }

    fun getSpeechRate(): Float = speechManager.getSpeechRate()

    fun clearConversation() {
        cancelGeneration()
        _messages.value = listOf(
            ChatMessage(
                text = "Welcome to Nexus Offline Emergency AI. Ask any question, emergency guidance, or technical assistance.",
                isUser = false
            )
        )
    }

    fun sendMessage(query: String) {
        if (query.isBlank() || _isGenerating.value) return
        submitQueryInternal(query)
    }

    private fun submitQueryInternal(query: String) {
        // 1. If Offline AI feature flag is OFF, post clear user feedback without invoking the model
        if (!isAiEnabled.value) {
            val userMsg = ChatMessage(text = query, isUser = true)
            val disabledNotice = ChatMessage(
                text = "Offline AI is currently disabled. Please enable it in Settings to process requests with on-device intelligence.",
                isUser = false,
                isWarning = true
            )
            _messages.value = _messages.value + userMsg + disabledNotice
            return
        }

        // 2. Safety filter checks for cyber attacks & offensive hacking
        if (AiSafetyGuard.isCyberAttackRequest(query)) {
            val userMsg = ChatMessage(text = query, isUser = true)
            val warningMsg = ChatMessage(
                text = AiSafetyGuard.CYBER_ATTACK_REFUSAL,
                isUser = false,
                isWarning = true
            )
            _messages.value = _messages.value + userMsg + warningMsg
            return
        }

        // 3. Safety filter checks for harmful physical content
        if (AiSafetyGuard.isHarmfulRequest(query)) {
            val userMsg = ChatMessage(text = query, isUser = true)
            val warningMsg = ChatMessage(
                text = AiSafetyGuard.HARMFUL_CONTENT_REFUSAL,
                isUser = false,
                isWarning = true
            )
            _messages.value = _messages.value + userMsg + warningMsg
            return
        }

        // 3. Prompt injection check
        if (AiSafetyGuard.isPromptInjection(query)) {
            val userMsg = ChatMessage(text = query, isUser = true)
            val aiMsg = ChatMessage(
                text = AiSafetyGuard.INJECTION_REFUSAL_MESSAGE,
                isUser = false,
                isWarning = true
            )
            _messages.value = _messages.value + userMsg + aiMsg
            return
        }

        val userMsg = ChatMessage(text = query, isUser = true)
        _messages.value = _messages.value + userMsg
        _isGenerating.value = true

        val decision = routingPolicy.route(query, _messages.value)

        currentGenerationJob = viewModelScope.launch {
            try {
                when (decision.route) {
                    AiRoute.EMERGENCY_GUIDANCE -> {
                        val result = ragService.answerQuery(query)
                        when (result) {
                            is AiResult.Success -> {
                                val response = result.data
                                val isExcerpt = response.answer.startsWith(RagConstants.GUIDE_EXCERPT_PREFIX)
                                val aiMsg = ChatMessage(
                                    text = response.answer,
                                    isUser = false,
                                    citations = response.citations,
                                    isExcerptOnly = isExcerpt,
                                    showModelImport = isExcerpt
                                )
                                _messages.value = _messages.value + aiMsg
                                speechManager.speak(response.answer)
                            }
                            is AiResult.Failure -> {
                                val errorMsg = ChatMessage(
                                    text = "[AI Error]: ${result.error.userRecoveryText}. Please consult verified emergency protocols directly.",
                                    isUser = false,
                                    isWarning = true
                                )
                                _messages.value = _messages.value + errorMsg
                            }
                        }
                    }

                    AiRoute.MIXED -> {
                        // Mixed: Retrieve verified local emergency knowledge
                        val ragResult = ragService.answerQuery(query)
                        val (contextPassages, citations) = if (ragResult is AiResult.Success && ragResult.data.hasSufficientEvidence) {
                            listOf(ragResult.data.answer) to ragResult.data.citations
                        } else {
                            emptyList<String>() to emptyList()
                        }

                        if (coordinator.state.value != AiCapabilityState.READY) {
                            // If model is not ready, surface grounded guide excerpt directly
                            if (ragResult is AiResult.Success && ragResult.data.hasSufficientEvidence) {
                                val aiMsg = ChatMessage(
                                    text = ragResult.data.answer,
                                    isUser = false,
                                    citations = citations,
                                    isExcerptOnly = true,
                                    showModelImport = true
                                )
                                _messages.value = _messages.value + aiMsg
                                speechManager.speak(ragResult.data.answer)
                            } else {
                                val aiMsg = ChatMessage(
                                    text = "AI model not installed: Please install or import the Gemma model in Settings to enable combined general and emergency offline reasoning.",
                                    isUser = false,
                                    isExcerptOnly = true,
                                    showModelImport = true
                                )
                                _messages.value = _messages.value + aiMsg
                            }
                            return@launch
                        }

                        // Local model is ready: build conversation prompt incorporating verified emergency passages
                        val historyPairs = _messages.value.map { it.text to it.isUser }
                        val prompt = RagConstants.buildConversationPrompt(
                            query = query,
                            history = historyPairs,
                            contextPassages = contextPassages
                        )

                        executeStreamingGeneration(prompt, citations)
                    }

                    AiRoute.GENERAL_CONVERSATION -> {
                        if (coordinator.state.value != AiCapabilityState.READY) {
                            val aiMsg = ChatMessage(
                                text = "AI model not installed: Please install or import the Gemma model in Settings to enable general-purpose offline conversation.",
                                isUser = false,
                                isExcerptOnly = true,
                                showModelImport = true
                            )
                            _messages.value = _messages.value + aiMsg
                            return@launch
                        }

                        val historyPairs = _messages.value.map { it.text to it.isUser }
                        val prompt = RagConstants.buildConversationPrompt(query, historyPairs)

                        executeStreamingGeneration(prompt, emptyList())
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                AiLogger.i("Generation coroutine cancelled")
                throw e
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    text = "[Error]: Failed to process request. Tap to retry.",
                    isUser = false,
                    isWarning = true
                )
            } finally {
                _isGenerating.value = false
                currentGenerationJob = null
            }
        }
    }

    private suspend fun executeStreamingGeneration(
        prompt: String,
        citations: List<EmergencySourceCitation>
    ) {
        val placeholder = ChatMessage(
            text = "",
            isUser = false,
            citations = citations
        )
        _messages.value = _messages.value + placeholder

        val accumulated = StringBuilder()

        val inferenceResult = coordinator.executeInference(timeoutMs = 120000L) {
            var streamFailure: AiError? = null
            try {
                localAiEngine.generateStream(prompt).collect { chunkResult ->
                    when (chunkResult) {
                        is AiResult.Success -> {
                            accumulated.append(chunkResult.data)
                            val currentList = _messages.value
                            if (currentList.isNotEmpty() && !currentList.last().isUser) {
                                _messages.value = currentList.dropLast(1) + currentList.last().copy(text = accumulated.toString())
                            }
                        }
                        is AiResult.Failure -> {
                            streamFailure = chunkResult.error
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                streamFailure = AiError.RuntimeError(e.message ?: "Streaming error")
            }

            val failure = streamFailure
            if (failure != null && accumulated.isEmpty()) {
                AiResult.Failure(failure)
            } else if (accumulated.isNotEmpty()) {
                AiResult.Success(accumulated.toString())
            } else {
                // If stream emitted no text (e.g. mocked or non-streaming engine), delegate to generate
                val fallbackResult = localAiEngine.generate(prompt)
                if (fallbackResult is AiResult.Success) {
                    accumulated.append(fallbackResult.data)
                    val currentList = _messages.value
                    if (currentList.isNotEmpty() && !currentList.last().isUser) {
                        _messages.value = currentList.dropLast(1) + currentList.last().copy(text = fallbackResult.data)
                    }
                }
                fallbackResult
            }
        }

        when (inferenceResult) {
            is AiResult.Success -> {
                val finalText = inferenceResult.data.ifBlank { accumulated.toString() }
                val currentList = _messages.value
                if (currentList.isNotEmpty() && !currentList.last().isUser) {
                    _messages.value = currentList.dropLast(1) + currentList.last().copy(
                        text = finalText,
                        citations = citations
                    )
                }
                if (finalText.isNotBlank()) {
                    speechManager.speak(finalText)
                }
            }
            is AiResult.Failure -> {
                val currentList = _messages.value
                val errorMsg = ChatMessage(
                    text = "[AI Error]: ${inferenceResult.error.userRecoveryText}. Tap to retry.",
                    isUser = false,
                    isWarning = true
                )
                if (currentList.isNotEmpty() && !currentList.last().isUser && currentList.last().text.isEmpty()) {
                    _messages.value = currentList.dropLast(1) + errorMsg
                } else {
                    _messages.value = currentList + errorMsg
                }
            }
        }
    }

    fun summarizeRecentMeshMessages() {
        if (_isGenerating.value) return
        if (!isAiEnabled.value) {
            _messages.value = _messages.value + ChatMessage(
                text = "Offline AI is currently disabled. Enable Offline AI in Settings to summarize mesh incidents.",
                isUser = false,
                isWarning = true
            )
            return
        }

        _isGenerating.value = true
        _messages.value = _messages.value + ChatMessage(text = "Summarize recent incident mesh messages", isUser = true)

        viewModelScope.launch {
            try {
                val latestMessages = messageRepository.getLatestMessagePerConversation().first()
                val result = summaryService.summarizeMessages(latestMessages)
                when (result) {
                    is AiResult.Success -> {
                        _messages.value = _messages.value + ChatMessage(
                            text = result.data,
                            isUser = false
                        )
                    }
                    is AiResult.Failure -> {
                        _messages.value = _messages.value + ChatMessage(
                            text = "[Summarization Failed]: ${result.error.userRecoveryText}",
                            isUser = false,
                            isWarning = true
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    text = "[Error]: Incident summarization failed.",
                    isUser = false,
                    isWarning = true
                )
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun startListening() {
        speechManager.startListening()
    }

    fun stopListening() {
        speechManager.stopListening()
    }

    fun onImageCaptured(bitmap: android.graphics.Bitmap) {
        if (_isGenerating.value) return
        _isGenerating.value = true

        viewModelScope.launch {
            try {
                val labels = visionManager.analyzeImage(bitmap)
                if (labels.isEmpty()) {
                    _messages.value = _messages.value + ChatMessage(
                        text = "No clear hazard identified in the picture. Please describe your situation directly.",
                        isUser = false
                    )
                    _isGenerating.value = false
                    return@launch
                }

                val query = "Detected: ${labels.joinToString(", ")}. What emergency steps should be taken?"
                _isGenerating.value = false
                submitQueryInternal(query)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _isGenerating.value = false
                throw e
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    text = "[Error]: Failed to analyze scene hazard.",
                    isUser = false,
                    isWarning = true
                )
                _isGenerating.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelGeneration()
        speechManager.cleanup()
    }
}
