package com.meshlink.app.ai.domain

import com.meshlink.app.ai.model.AiResult
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Service managing offline voice input (ASR) and text-to-speech (TTS).
 */
interface VoiceAssistantService {
    val isListening: StateFlow<Boolean>
    val isSpeaking: StateFlow<Boolean>
    val spokenText: SharedFlow<String>

    fun startListening(): AiResult<Unit>
    fun stopListening()

    fun speak(text: String): AiResult<Unit>
    fun stopSpeaking()

    fun release()
}
