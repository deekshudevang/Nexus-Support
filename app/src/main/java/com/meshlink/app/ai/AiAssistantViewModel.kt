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
    private val visionManager: VisionManager
) : ViewModel() {

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
                    text = "Welcome to Nexus Offline Emergency AI. Ask any question regarding disaster first aid, hypothermia, bleeding, water purification, or tap 'Summarize Incident' to analyze recent mesh messages.",
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

    fun sendMessage(query: String) {
        if (query.isBlank() || _isGenerating.value) return

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

        val userMsg = ChatMessage(text = query, isUser = true)
        _messages.value = _messages.value + userMsg
        _isGenerating.value = true

        viewModelScope.launch {
            try {
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    text = "[Error]: Failed to process emergency guidance. Tap to retry.",
                    isUser = false,
                    isWarning = true
                )
            } finally {
                _isGenerating.value = false
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
                    return@launch
                }

                val query = "Detected: ${labels.joinToString(", ")}. What emergency steps should be taken?"
                sendMessage(query)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    text = "[Error]: Failed to analyze scene hazard.",
                    isUser = false,
                    isWarning = true
                )
            } finally {
                _isGenerating.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.cleanup()
    }
}
