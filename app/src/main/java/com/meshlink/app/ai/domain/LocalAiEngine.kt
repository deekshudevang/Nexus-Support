package com.meshlink.app.ai.domain

import com.meshlink.app.ai.model.AiCapabilityState
import com.meshlink.app.ai.model.AiResult
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Low-level interface for local LLM inference engines (e.g. MediaPipe Tasks GenAI / LiteRT).
 */
interface LocalAiEngine {
    /**
     * Initializes the native runtime with the specified model binary.
     */
    suspend fun initialize(modelFile: File): AiResult<Unit>

    /**
     * Generates a synchronous completion given a bounded prompt.
     */
    suspend fun generate(prompt: String): AiResult<String>

    /**
     * Generates a streaming completion token-by-token if supported by runtime.
     */
    fun generateStream(prompt: String): Flow<AiResult<String>>

    /**
     * Closes the native runtime and immediately deallocates resident GPU/RAM buffers.
     */
    suspend fun unload()

    /**
     * Returns true if the native runtime is initialized and ready for inference.
     */
    fun isReady(): Boolean
}
