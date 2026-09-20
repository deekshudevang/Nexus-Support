package com.meshlink.app.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.domain.repository.EmergencyGuideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(val text: String, val isUser: Boolean)

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val llmManager: LlmInferenceManager,
    private val speechManager: SpeechManager,
    private val guideRepository: EmergencyGuideRepository,
    private val visionManager: VisionManager
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    val isListening: StateFlow<Boolean> = speechManager.isListening
    val spokenText: kotlinx.coroutines.flow.SharedFlow<String> = speechManager.spokenText

    init {
        viewModelScope.launch {
            llmManager.initializeModel()
            _messages.value = listOf(ChatMessage("Hello. I am your offline Gemma AI responder. How can I help you?", false))
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
        if (query.isBlank()) return

        val newMsgs = _messages.value.toMutableList()
        newMsgs.add(ChatMessage(query, true))
        _messages.value = newMsgs
        _isGenerating.value = true

        viewModelScope.launch {
            // RAG: 1. Retrieve context from Emergency Guides
            val guides = guideRepository.searchGuides(query).first()
            val contextData = if (guides.isNotEmpty()) {
                val topGuide = guides.first()
                "Protocol: ${topGuide.title}. Warnings: ${topGuide.warnings.joinToString()}. Steps: ${topGuide.steps.joinToString()}"
            } else ""

            // RAG: 2. Generate response with LLM
            val responseText = llmManager.generateRagResponse(query, contextData)
            
            val updatedMsgs = _messages.value.toMutableList()
            updatedMsgs.add(ChatMessage(responseText, false))
            _messages.value = updatedMsgs
            _isGenerating.value = false

            // Text to speech for hands-free operation
            speechManager.speak(responseText)
        }
    }

    fun startListening() {
        speechManager.startListening()
    }
    
    fun stopListening() {
        speechManager.stopListening()
    }

    fun onImageCaptured(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            _isGenerating.value = true
            
            // 1. Analyze image using ML Kit
            val labels = visionManager.analyzeImage(bitmap)
            
            if (labels.isEmpty()) {
                val updatedMsgs = _messages.value.toMutableList()
                updatedMsgs.add(ChatMessage("I couldn't clearly identify the emergency in that picture. Could you describe it?", false))
                _messages.value = updatedMsgs
                _isGenerating.value = false
                speechManager.speak("I couldn't clearly identify the emergency in that picture. Could you describe it?")
                return@launch
            }
            
            // 2. Build the automated query
            val query = "I see ${labels.joinToString(", ")}. What should I do?"
            
            // Show the user's automated query in the chat
            val newMsgs = _messages.value.toMutableList()
            newMsgs.add(ChatMessage(query, true))
            _messages.value = newMsgs
            
            // 3. RAG Retrieval
            val guides = guideRepository.searchGuides(labels.joinToString(" ")).first()
            val contextData = if (guides.isNotEmpty()) {
                val topGuide = guides.first()
                "Protocol: ${topGuide.title}. Warnings: ${topGuide.warnings.joinToString()}. Steps: ${topGuide.steps.joinToString()}"
            } else ""

            // 4. LLM Generation
            val responseText = llmManager.generateRagResponse(query, contextData)
            
            val finalMsgs = _messages.value.toMutableList()
            finalMsgs.add(ChatMessage(responseText, false))
            _messages.value = finalMsgs
            _isGenerating.value = false

            speechManager.speak(responseText)
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.cleanup()
    }
}
