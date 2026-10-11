package com.meshlink.app.ai.data

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ModelAssetManagerTest {

    private lateinit var mockContext: Context
    private lateinit var tempDir: File
    private lateinit var assetManager: ModelAssetManagerImpl

    @Before
    fun setUp() {
        tempDir = File.createTempFile("test_models_dir", "").apply {
            delete()
            mkdirs()
        }
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir

        assetManager = ModelAssetManagerImpl(mockContext)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun getVerifiedModelFile_whenNoFileExists_returnsNull() = runTest {
        val file = assetManager.getVerifiedModelFile()
        assertNull(file)
    }

    @Test
    fun getVerifiedModelFile_whenFileExistsInModelsDir_returnsFile() = runTest {
        val modelsSubDir = File(tempDir, "ai_models").apply { mkdirs() }
        val modelFile = File(modelsSubDir, "gemma.bin").apply {
            writeText("dummy-model-content")
        }

        val result = assetManager.getVerifiedModelFile()
        assertNotNull(result)
        assertEquals(modelFile.absolutePath, result?.absolutePath)
    }

    @Test
    fun verifyChecksum_calculatesAndVerifiesRealSha256() = runTest {
        val testFile = File(tempDir, "checksum_test.bin").apply {
            writeText("hello-world-nexus-ai")
        }

        val expectedSha256 = MessageDigest.getInstance("SHA-256")
            .digest("hello-world-nexus-ai".toByteArray())
            .joinToString("") { "%02x".format(it) }

        val isValid = assetManager.verifyChecksum(testFile, expectedSha256)
        assertTrue(isValid)

        val isInvalid = assetManager.verifyChecksum(testFile, "0000000000000000000000000000000000000000000000000000000000000000")
        assertFalse(isInvalid)
    }

    @Test
    fun getVerifiedModelFile_whenLitertlmFileExists_returnsLitertlmFile() = runTest {
        val modelsSubDir = File(tempDir, "ai_models").apply { mkdirs() }
        val modelFile = File(modelsSubDir, "model.litertlm").apply {
            writeText("dummy-litertlm-content")
        }

        val result = assetManager.getVerifiedModelFile()
        assertNotNull(result)
        assertEquals(modelFile.absolutePath, result?.absolutePath)

        val manifest = assetManager.getInstalledManifest()
        assertNotNull(manifest)
        assertEquals("litertlm", manifest?.format)
        assertEquals("litert-lm", manifest?.runtime)
    }

    @Test
    fun getVerifiedModelFile_whenTaskFileExists_returnsTaskFile() = runTest {
        val modelsSubDir = File(tempDir, "ai_models").apply { mkdirs() }
        val modelFile = File(modelsSubDir, "gemma.task").apply {
            writeText("dummy-task-content")
        }

        val result = assetManager.getVerifiedModelFile()
        assertNotNull(result)
        assertEquals(modelFile.absolutePath, result?.absolutePath)

        val manifest = assetManager.getInstalledManifest()
        assertNotNull(manifest)
        assertEquals("mediapipe_task", manifest?.format)
        assertEquals("tasks-genai", manifest?.runtime)
    }

    @Test
    fun deleteInstalledModel_removesFileFromStorage() = runTest {
        val modelsSubDir = File(tempDir, "ai_models").apply { mkdirs() }
        val modelFile = File(modelsSubDir, "model.litertlm").apply {
            writeText("dummy-content")
        }
        assertTrue(modelFile.exists())

        val result = assetManager.deleteInstalledModel()
        assertTrue(result.isSuccess)
        assertFalse(modelFile.exists())
    }
}
