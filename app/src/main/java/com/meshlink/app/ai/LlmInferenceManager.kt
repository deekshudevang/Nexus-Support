package com.meshlink.app.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.meshlink.app.ai.security.AiLogger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmInferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var llmInference: LlmInference? = null
    private val modelName = "gemma.bin"

    // Initializes the MediaPipe LLM Inference API with a Gemma 4b/2b model
    suspend fun initializeModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(context.filesDir, modelName)
            if (!modelFile.exists()) {
                AiLogger.w("Gemma model not found at ${modelFile.absolutePath}. Falling back to stub.")
                return@withContext false
            }
            
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .build()
                
            llmInference = LlmInference.createFromOptions(context, options)
            true
        } catch (e: Exception) {
            AiLogger.e(e, "Failed to initialize LLM Inference")
            false
        }
    }

    /**
     * Generates a response using Retrieval-Augmented Generation (RAG).
     */
    suspend fun generateRagResponse(query: String, contextData: String): String = withContext(Dispatchers.IO) {
        if (llmInference == null) {
            return@withContext "[System: AI Unavailable] The local AI model is missing or not initialized. Please refer to the standard emergency protocols in the meantime."
        }
        
        try {
            val prompt = if (contextData.isNotBlank()) {
                "You are an offline disaster response AI. Using this emergency protocol context: $contextData\n\nAnswer the user's query: $query"
            } else {
                "You are an offline disaster response AI. Please answer the user's query: $query"
            }
            
            val response = llmInference?.generateResponse(prompt) ?: throw IllegalStateException("LLM not initialized")
            return@withContext response.trim()
        } catch (e: Exception) {
            AiLogger.e(e, "Failed to generate LLM response")
            return@withContext "[System: AI Error] An error occurred during inference (${e.message}). Please refer to the standard emergency protocols."
        }
    }
}
