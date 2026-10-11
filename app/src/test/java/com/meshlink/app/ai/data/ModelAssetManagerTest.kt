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
    fun getVerifiedModelFile_whenUnverifiedAndUnconfirmed_returnsNull() = runTest {
        val modelsSubDir = File(tempDir, "ai_models").apply { mkdirs() }
        File(modelsSubDir, "gemma.bin").apply {
            writeText("unverified-model-bytes")
        }

        // Neither pinned in manifest nor confirmed by user -> must refuse to load
        val result = assetManager.getVerifiedModelFile()
        assertNull("Unverified model must never load", result)
    }

    @Test
    fun getVerifiedModelFile_whenPinnedSha256InManifestMatches_returnsFile() = runTest {
        val modelsSubDir = File(tempDir, "ai_models").apply { mkdirs() }
        val modelBytes = "verified-pinned-model-bytes"
        val modelFile = File(modelsSubDir, "gemma.task").apply {
            writeText(modelBytes)
        }

        val expectedSha256 = MessageDigest.getInstance("SHA-256")
            .digest(modelBytes.toByteArray())
            .joinToString("") { "%02x".format(it) }

        // Create manifest with pinnedSha256
        val manifestJson = org.json.JSONObject().apply {
            put("modelId", "gemma-3-1b-it")
            put("fileName", "gemma.task")
            put("pinnedSha256", expectedSha256)
            put("sha256", expectedSha256)
        }
        File(modelsSubDir, "manifest.json").writeText(manifestJson.toString())

        val result = assetManager.getVerifiedModelFile()
        assertNotNull(result)
        assertEquals(modelFile.absolutePath, result?.absolutePath)
    }

    @Test
    fun getVerifiedModelFile_whenPinnedSha256Mismatches_returnsNull() = runTest {
        val modelsSubDir = File(tempDir, "ai_models").apply { mkdirs() }
        File(modelsSubDir, "gemma.task").apply {
            writeText("tampered-model-bytes")
        }

        val manifestJson = org.json.JSONObject().apply {
            put("modelId", "gemma-3-1b-it")
            put("fileName", "gemma.task")
            put("pinnedSha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        }
        File(modelsSubDir, "manifest.json").writeText(manifestJson.toString())

        val result = assetManager.getVerifiedModelFile()
        assertNull("Mismatched hash against pinned manifest must return null", result)
    }

    @Test
    fun getVerifiedModelFile_whenUserExplicitlyConfirmsComputedHash_returnsFile() = runTest {
        val modelsSubDir = File(tempDir, "ai_models").apply { mkdirs() }
        val modelBytes = "user-imported-model-content"
        val modelFile = File(modelsSubDir, "gemma.task").apply {
            writeText(modelBytes)
        }

        val computedSha256 = assetManager.computeSha256(modelFile)
        assertTrue(computedSha256.isNotBlank())

        // Before confirmation -> returns null
        assertNull(assetManager.getVerifiedModelFile())

        // User inspects computed hash in confirmation dialog and confirms
        assetManager.confirmModelHash(computedSha256)
        assertTrue(assetManager.isHashConfirmed(computedSha256))

        // After user confirmation -> returns verified file
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
    fun deleteInstalledModel_removesFileAndConfirmationFromStorage() = runTest {
        val modelsSubDir = File(tempDir, "ai_models").apply { mkdirs() }
        val modelFile = File(modelsSubDir, "model.task").apply {
            writeText("dummy-content")
        }
        val confirmationFile = File(modelsSubDir, "confirmed_hash.txt").apply {
            writeText("somehash")
        }

        assertTrue(modelFile.exists())
        assertTrue(confirmationFile.exists())

        val result = assetManager.deleteInstalledModel()
        assertTrue(result.isSuccess)
        assertFalse(modelFile.exists())
        assertFalse(confirmationFile.exists())
    }
}
