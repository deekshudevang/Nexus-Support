package com.meshlink.app.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.meshlink.app.ai.security.AiLogger
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener, RecognitionListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsInitialized = false

    private val _spokenText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val spokenText: SharedFlow<String> = _spokenText

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    init {
        tts = TextToSpeech(context, this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)
            AiLogger.i("On-device speech recognizer initialized successfully.")
        } else {
            speechRecognizer = null
            AiLogger.w("On-device speech recognition is not supported or not installed on this device. Network speech is strictly forbidden offline; voice recognition disabled.")
        }
    }

    private var speechRate: Float = DEFAULT_SPEECH_RATE

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                AiLogger.e("TTS: Language not supported")
            } else {
                tts?.setSpeechRate(speechRate)
                tts?.setPitch(1.0f)
                isTtsInitialized = true
                AiLogger.i("TTS initialized successfully with speech rate: $speechRate")
            }
        } else {
            AiLogger.e("TTS Initialization failed")
        }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
        if (isTtsInitialized) {
            tts?.setSpeechRate(speechRate)
        }
    }

    fun getSpeechRate(): Float = speechRate

    fun speak(text: String) {
        if (isTtsInitialized) {
            val sanitized = sanitizeForTts(text)
            if (sanitized.isNotBlank()) {
                tts?.speak(sanitized, TextToSpeech.QUEUE_FLUSH, null, "SpeechManagerTTS")
            }
        }
    }

    fun sanitizeForTts(text: String): String {
        return text
            // Remove code blocks
            .replace(Regex("```[\\s\\S]*?```"), " Code block omitted for voice. ")
            // Remove inline code
            .replace(Regex("`([^`]+)`"), "$1")
            // Remove bold/italics
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("__([^_]+)__"), "$1")
            .replace(Regex("_([^_]+)_"), "$1")
            // Remove markdown headers
            .replace(Regex("(?m)^#{1,6}\\s*"), "")
            // Remove bullet point markers at start of lines
            .replace(Regex("(?m)^[\\s*•-]+\\s*"), "")
            // Remove horizontal rules
            .replace(Regex("(?m)^[-*_]{3,}\\s*$"), "")
            // Remove emergency bracket notices
            .replace(Regex("\\[Emergency Notice:[^\\]]*\\]"), "")
            // Remove verified emergency source lines
            .replace(Regex("(?m)^Verified Emergency Sources:.*$"), "")
            .replace(Regex("(?m)^Source ID:.*$"), "")
            // Collapse whitespace and newlines
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        const val DEFAULT_SPEECH_RATE = 0.85f // Calibrated calm conversational pacing (prevents 2x rushed speech)
    }
    
    fun stopSpeaking() {
        tts?.stop()
    }

    fun startListening() {
        if (speechRecognizer == null) {
            throw IllegalStateException("SpeechRecognizer is not initialized or unavailable")
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
        _isListening.value = true
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _isListening.value = false
    }

    // RecognitionListener Callbacks
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        _isListening.value = false
    }
    override fun onError(error: Int) {
        AiLogger.e("Speech recognition error: $error")
        _isListening.value = false
    }
    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            _spokenText.tryEmit(matches[0])
        }
        _isListening.value = false
    }
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun cleanup() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
