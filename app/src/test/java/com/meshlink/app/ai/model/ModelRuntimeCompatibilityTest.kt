package com.meshlink.app.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelRuntimeCompatibilityTest {

    @Test
    fun detectFormat_mapsExtensionsAccurately() {
        assertEquals(
            ModelContainerFormat.MEDIAPIPE_TASK,
            ModelRuntimeCompatibility.detectFormat(File("model.task"))
        )
        assertEquals(
            ModelContainerFormat.TFLITE_FLATBUFFER,
            ModelRuntimeCompatibility.detectFormat(File("model.bin"))
        )
        assertEquals(
            ModelContainerFormat.LITERT_LM_CONTAINER,
            ModelRuntimeCompatibility.detectFormat(File("model.litertlm"))
        )
        assertEquals(
            ModelContainerFormat.UNKNOWN,
            ModelRuntimeCompatibility.detectFormat(File("model.gguf"))
        )
        assertEquals(
            ModelContainerFormat.UNKNOWN,
            ModelRuntimeCompatibility.detectFormat(File("model.onnx"))
        )
    }

    @Test
    fun isFormatSupportedByRuntime_validatesPackagedTasksGenAiRuntime() {
        // MediaPipe Tasks GenAI supports .task and .bin
        assertTrue(
            ModelRuntimeCompatibility.isFormatSupportedByRuntime(
                ModelContainerFormat.MEDIAPIPE_TASK,
                SupportedAiRuntime.TASKS_GENAI
            )
        )
        assertTrue(
            ModelRuntimeCompatibility.isFormatSupportedByRuntime(
                ModelContainerFormat.TFLITE_FLATBUFFER,
                SupportedAiRuntime.TASKS_GENAI
            )
        )
        // LiteRT-LM format is NOT supported by Tasks GenAI runtime
        assertFalse(
            ModelRuntimeCompatibility.isFormatSupportedByRuntime(
                ModelContainerFormat.LITERT_LM_CONTAINER,
                SupportedAiRuntime.TASKS_GENAI
            )
        )
    }

    @Test
    fun validateModelArtifact_whenLiteRtLmPassedToTasksGenAi_failsWithClearError() {
        val dummyLiteRtLm = File.createTempFile("candidate", ".litertlm").apply {
            writeText("dummy-litertlm-bytes")
            deleteOnExit()
        }

        val result = ModelRuntimeCompatibility.validateModelArtifact(
            dummyLiteRtLm,
            SupportedAiRuntime.TASKS_GENAI
        )

        assertTrue("LiteRT-LM format must be rejected for Tasks GenAI runtime", result.isFailure)
        val error = (result as AiResult.Failure).error
        assertTrue(error is AiError.RuntimeError)
        val message = (error as AiError.RuntimeError).message
        assertTrue(message.contains("Incompatible model format") && message.contains("litertlm"))
    }

    @Test
    fun validateModelArtifact_whenFileIsEmpty_failsWithModelCorrupt() {
        val emptyFile = File.createTempFile("empty", ".task").apply {
            deleteOnExit()
        }

        val result = ModelRuntimeCompatibility.validateModelArtifact(emptyFile)
        assertTrue(result.isFailure)
        val error = (result as AiResult.Failure).error
        assertTrue(error is AiError.ModelCorrupt)
    }

    @Test
    fun validateModelArtifact_whenFileMissing_failsWithModelMissing() {
        val nonExistent = File("non_existent_file.task")
        val result = ModelRuntimeCompatibility.validateModelArtifact(nonExistent)
        assertTrue(result.isFailure)
        assertEquals(AiError.ModelMissing, (result as AiResult.Failure).error)
    }

    @Test
    fun verifyBinaryHeader_identifiesValidZipAndRejectsCorrupted() {
        // Valid ZIP local header: PK\x03\x04
        val validZipFile = File.createTempFile("valid_task", ".task").apply {
            writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00))
            deleteOnExit()
        }
        assertTrue(
            ModelRuntimeCompatibility.verifyBinaryHeader(
                validZipFile,
                ModelContainerFormat.MEDIAPIPE_TASK
            )
        )

        // Corrupted header
        val corruptZipFile = File.createTempFile("corrupt_task", ".task").apply {
            writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07))
            deleteOnExit()
        }
        assertFalse(
            ModelRuntimeCompatibility.verifyBinaryHeader(
                corruptZipFile,
                ModelContainerFormat.MEDIAPIPE_TASK
            )
        )

        // Truncated file (< 4 bytes)
        val truncatedFile = File.createTempFile("truncated", ".task").apply {
            writeBytes(byteArrayOf(0x50, 0x4B))
            deleteOnExit()
        }
        assertFalse(
            ModelRuntimeCompatibility.verifyBinaryHeader(
                truncatedFile,
                ModelContainerFormat.MEDIAPIPE_TASK
            )
        )
    }

    @Test
    fun verifyBinaryHeader_identifiesValidTfLiteFlatbuffer() {
        // Valid TFLite flatbuffer: offset 4 is 'TFL3'
        val validTflite = File.createTempFile("valid_tflite", ".bin").apply {
            writeBytes(byteArrayOf(0x18, 0x00, 0x00, 0x00, 'T'.code.toByte(), 'F'.code.toByte(), 'L'.code.toByte(), '3'.code.toByte()))
            deleteOnExit()
        }
        assertTrue(
            ModelRuntimeCompatibility.verifyBinaryHeader(
                validTflite,
                ModelContainerFormat.TFLITE_FLATBUFFER
            )
        )

        val corruptTflite = File.createTempFile("corrupt_tflite", ".bin").apply {
            writeBytes(byteArrayOf(0x18, 0x00, 0x00, 0x00, 'X'.code.toByte(), 'Y'.code.toByte(), 'Z'.code.toByte(), '0'.code.toByte()))
            deleteOnExit()
        }
        assertFalse(
            ModelRuntimeCompatibility.verifyBinaryHeader(
                corruptTflite,
                ModelContainerFormat.TFLITE_FLATBUFFER
            )
        )
    }

    @Test
    fun validateModelIdentity_acceptsGemma3AndRejectsUnknownArchitecture() {
        assertTrue(ModelRuntimeCompatibility.validateModelIdentity("gemma-3-1b-it").isSuccess)
        assertTrue(ModelRuntimeCompatibility.validateModelIdentity("gemma3-1b-it").isSuccess)
        assertTrue(ModelRuntimeCompatibility.validateModelIdentity("gemma3").isSuccess)

        // Unknown / unverified model architectures
        val unknownResult = ModelRuntimeCompatibility.validateModelIdentity("llama-3.2-1b")
        assertTrue(unknownResult.isFailure)
        assertTrue((unknownResult as AiResult.Failure).error is AiError.Unsupported)

        val blankResult = ModelRuntimeCompatibility.validateModelIdentity("   ")
        assertTrue(blankResult.isFailure)
        assertTrue((blankResult as AiResult.Failure).error is AiError.Unsupported)
    }
}
