package com.meshlink.app.ai.fakes

import com.meshlink.app.ai.domain.LocalAiEngine
import com.meshlink.app.ai.model.AiError
import com.meshlink.app.ai.model.AiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Test-only fake for LocalAiEngine. Strictly isolated to test source set.
 */
class FakeLocalAiEngine : LocalAiEngine {
    var initialized = false
    var shouldFailInit = false
    var failInitPredicate: ((File) -> Boolean)? = null
    var responseToReturn = "Sample test response"
    var unloadCalled = false

    override suspend fun initialize(modelFile: File): AiResult<Unit> {
        if (shouldFailInit || failInitPredicate?.invoke(modelFile) == true) {
            return AiResult.Failure(AiError.ModelCorrupt("Simulated corrupt model"))
        }
        initialized = true
        return AiResult.Success(Unit)
    }

    var lastPromptReceived: String? = null

    override suspend fun generate(prompt: String): AiResult<String> {
        lastPromptReceived = prompt
        if (!initialized) return AiResult.Failure(AiError.ModelMissing)
        return AiResult.Success(responseToReturn)
    }

    override fun generateStream(prompt: String): Flow<AiResult<String>> = flow {
        if (!initialized) {
            emit(AiResult.Failure(AiError.ModelMissing))
        } else {
            emit(AiResult.Success(responseToReturn))
        }
    }

    override suspend fun unload() {
        initialized = false
        unloadCalled = true
    }

    override fun isReady(): Boolean = initialized
}
