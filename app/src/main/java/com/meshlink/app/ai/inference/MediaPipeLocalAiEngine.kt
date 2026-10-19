package com.meshlink.app.ai.inference

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.meshlink.app.ai.domain.LocalAiEngine
import com.meshlink.app.ai.model.AiError
import com.meshlink.app.ai.model.AiResult
import com.meshlink.app.ai.security.AiLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete on-device LLM engine using Google MediaPipe Tasks GenAI (LiteRT).
 * Executes quantized Gemma models entirely offline on local hardware.
 */
@Singleton
class MediaPipeLocalAiEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalAiEngine {

    private var llmInference: LlmInference? = null
    private val inferenceLock = Any()

    override suspend fun initialize(modelFile: File): AiResult<Unit> = withContext(Dispatchers.IO) {
        synchronized(inferenceLock) {
            try {
                val validationResult = com.meshlink.app.ai.model.ModelRuntimeCompatibility.validateModelArtifact(
                    modelFile,
                    com.meshlink.app.ai.model.SupportedAiRuntime.TASKS_GENAI
                )
                if (validationResult.isFailure) {
                    val err = (validationResult as AiResult.Failure).error
                    AiLogger.w("Model file validation failed for MediaPipe runtime: $err")
                    return@withContext validationResult.map { Unit }
                }

                val startTime = System.currentTimeMillis()
                AiLogger.i("Initializing MediaPipe LlmInference runtime with model at ${modelFile.name}")

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(2048)
                    .setMaxTopK(40)
                    .setPreferredBackend(LlmInference.Backend.GPU)
                    .build()

                llmInference?.close()
                llmInference = LlmInference.createFromOptions(context, options)

                val duration = System.currentTimeMillis() - startTime
                AiLogger.logEvent("model_initialized", durationMs = duration)
                AiResult.Success(Unit)
            } catch (e: OutOfMemoryError) {
                AiLogger.e(e, "OOM during LLM initialization")
                llmInference = null
                AiResult.Failure(AiError.LowMemory)
            } catch (e: Exception) {
                AiLogger.e(e, "Failed to initialize LLM runtime")
                llmInference = null
                AiResult.Failure(AiError.RuntimeError(e.message ?: "Failed to initialize runtime"))
            }
        }
    }

    override suspend fun generate(prompt: String): AiResult<String> = withContext(Dispatchers.IO) {
        synchronized(inferenceLock) {
            val engine = llmInference
            if (engine == null) {
                return@withContext AiResult.Failure(AiError.ModelMissing)
            }

            try {
                val inputTokens = try { engine.sizeInTokens(prompt) } catch (e: Throwable) { 0 }
                AiLogger.i("Executing LLM generation with input tokens: $inputTokens (max: 2048)")

                val startTime = System.currentTimeMillis()
                val response = engine.generateResponse(prompt)
                val duration = System.currentTimeMillis() - startTime

                AiLogger.logEvent("inference_complete", durationMs = duration)
                AiResult.Success(response.trim())
            } catch (e: OutOfMemoryError) {
                AiLogger.e(e, "OOM during inference execution")
                unloadSync()
                AiResult.Failure(AiError.LowMemory)
            } catch (e: Throwable) {
                AiLogger.e(e, "Inference error occurred")
                AiResult.Failure(AiError.RuntimeError(e.message ?: "Inference failure"))
            }
        }
    }

    override fun generateStream(prompt: String): Flow<AiResult<String>> = kotlinx.coroutines.flow.callbackFlow {
        val engine = synchronized(inferenceLock) { llmInference }
        if (engine == null) {
            trySend(AiResult.Failure(AiError.ModelMissing))
            close()
            return@callbackFlow
        }

        try {
            val listener = com.google.mediapipe.tasks.genai.llminference.ProgressListener<String> { partialResult, done ->
                if (!partialResult.isNullOrEmpty()) {
                    trySend(AiResult.Success(partialResult))
                }
                if (done) {
                    close()
                }
            }

            val future = engine.generateResponseAsync(prompt, listener)

            awaitClose {
                try {
                    future.cancel(true)
                } catch (e: Throwable) {
                    AiLogger.w("Failed to cancel native streaming future: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            AiLogger.e(e, "Error initiating streaming generation")
            trySend(AiResult.Failure(AiError.RuntimeError(e.message ?: "Streaming error")))
            close()
        }
    }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        synchronized(inferenceLock) {
            unloadSync()
        }
    }

    private fun unloadSync() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            AiLogger.e(e, "Error closing native LlmInference")
        } finally {
            llmInference = null
            AiLogger.i("LocalAiEngine native resources deallocated.")
        }
    }

    override fun isReady(): Boolean = synchronized(inferenceLock) {
        llmInference != null
    }
}
