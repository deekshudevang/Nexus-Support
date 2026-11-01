package com.meshlink.app.ai.inference

import android.content.Context
import com.meshlink.app.ai.model.AiError
import com.meshlink.app.ai.model.AiResult
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class MediaPipeLocalAiEngineTest {

    private lateinit var mockContext: Context
    private lateinit var tempDir: File
    private lateinit var engine: MediaPipeLocalAiEngine

    @Before
    fun setUp() {
        tempDir = File.createTempFile("mediapipe_test", "").apply {
            delete()
            mkdirs()
        }
        mockContext = mockk(relaxed = true)
        engine = MediaPipeLocalAiEngine(mockContext)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun initialize_whenFileDoesNotExist_returnsModelMissing() = runTest {
        val nonExistent = File(tempDir, "missing.task")
        val result = engine.initialize(nonExistent)

        assertTrue(result.isFailure)
        assertEquals(AiError.ModelMissing, (result as AiResult.Failure).error)
    }

    @Test
    fun initialize_whenFileHasMismatchedExtension_returnsClearErrorWithoutCrash() = runTest {
        // e.g. Trying to load a .litertlm file into MediaPipe Tasks GenAI
        val litertlmFile = File(tempDir, "gemma-3-1b-it.litertlm").apply {
            writeText("dummy-litertlm-bytes")
        }

        val result = engine.initialize(litertlmFile)

        assertTrue(result.isFailure)
        val error = (result as AiResult.Failure).error
        assertTrue(error is AiError.RuntimeError)
        val message = (error as AiError.RuntimeError).message
        assertTrue(
            "Expected incompatible format error message but was: $message",
            message.contains("Incompatible model format") && message.contains("litertlm")
        )
    }

    @Test
    fun initialize_whenFileHasUnsupportedGgufExtension_returnsClearErrorWithoutCrash() = runTest {
        val ggufFile = File(tempDir, "model.gguf").apply {
            writeText("dummy-gguf-bytes")
        }

        val result = engine.initialize(ggufFile)

        assertTrue(result.isFailure)
        val error = (result as AiResult.Failure).error
        assertTrue(error is AiError.RuntimeError)
        val message = (error as AiError.RuntimeError).message
        assertTrue(
            "Expected incompatible format error message but was: $message",
            message.contains("MediaPipe Tasks GenAI runtime requires .task or .bin")
        )
    }

    @Test
    fun generate_whenEngineNotInitialized_returnsModelMissing() = runTest {
        val result = engine.generate("Hello")

        assertTrue(result.isFailure)
        assertEquals(AiError.ModelMissing, (result as AiResult.Failure).error)
    }

    @Test
    fun isReady_whenNotInitialized_returnsFalse() {
        assertFalse(engine.isReady())
    }
}
