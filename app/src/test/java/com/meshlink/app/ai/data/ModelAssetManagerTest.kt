package com.meshlink.app.ai.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.meshlink.app.ai.domain.ModelSlotRole
import com.meshlink.app.ai.model.AiError
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class ModelAssetManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver
    private lateinit var tempDir: File
    private lateinit var assetManager: ModelAssetManagerImpl

    @Before
    fun setUp() {
        tempDir = File.createTempFile("test_models_dir", "").apply {
            delete()
            mkdirs()
        }
        mockContext = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir
        every { mockContext.contentResolver } returns mockContentResolver

        assetManager = ModelAssetManagerImpl(mockContext)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun createBaselineModel(
        dirName: String = "models",
        fileName: String = "gemma3-1B-it-int4.task",
        content: String = "known-good-baseline-gemma3-weights"
    ): File {
        val dir = File(tempDir, dirName).apply { mkdirs() }
        val modelFile = File(dir, fileName).apply { writeText(content) }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
        File(dir, "confirmed_hash.txt").writeText(hash)
        return modelFile
    }

    private fun mockUri(fileName: String, content: ByteArray): Uri {
        val uri: Uri = mockk(relaxed = true)
        every { uri.lastPathSegment } returns fileName
        every { mockContentResolver.openInputStream(uri) } answers { ByteArrayInputStream(content) }
        every { mockContentResolver.query(uri, null, null, null, null) } returns null
        return uri
    }

    // 1. Successful candidate staging without touching active baseline
    @Test
    fun stageCandidateFromUri_successfulStaging_populatesStagedSlotAndPreservesBaseline() = runTest {
        val baselineFile = createBaselineModel()
        val originalBaselineBytes = baselineFile.readBytes()

        val candidateBytes = ("candidate-model-content-bytes-long-padding".repeat(30)).toByteArray()
        val candidateHash = MessageDigest.getInstance("SHA-256")
            .digest(candidateBytes)
            .joinToString("") { "%02x".format(it) }
        val uri = mockUri("candidate.task", candidateBytes)

        val result = assetManager.stageCandidateFromUri(uri, candidateHash)

        assertTrue("Candidate staging should succeed", result.isSuccess)
        val stagedSlot = assetManager.getStagedSlot()
        assertNotNull("Staged slot must exist", stagedSlot)
        assertEquals(ModelSlotRole.STAGED, stagedSlot?.role)
        assertEquals(candidateHash, stagedSlot?.sha256)

        // Baseline model must remain active and completely untouched
        val activeSlot = assetManager.getActiveSlot()
        assertNotNull("Active baseline slot must remain", activeSlot)
        assertEquals(ModelSlotRole.ACTIVE, activeSlot?.role)
        assertEquals("baseline", activeSlot?.slotId)
        assertTrue("Baseline file must still exist", baselineFile.exists())
        assertEquals("Baseline bytes must be unmodified", originalBaselineBytes.toList(), baselineFile.readBytes().toList())
    }

    // 2. Candidate checksum failure
    @Test
    fun stageCandidateFromUri_checksumFailure_deletesStagingFileAndPreservesBaseline() = runTest {
        val baselineFile = createBaselineModel()
        val originalBaselineBytes = baselineFile.readBytes()

        val candidateBytes = ("candidate-bytes-with-mismatch".repeat(30)).toByteArray()
        val wrongHash = "0000000000000000000000000000000000000000000000000000000000000000"
        val uri = mockUri("candidate.task", candidateBytes)

        val result = assetManager.stageCandidateFromUri(uri, wrongHash)

        assertTrue("Import must fail on checksum mismatch", result.isFailure)
        assertTrue((result as com.meshlink.app.ai.model.AiResult.Failure).error is AiError.ModelCorrupt)

        // Staged slot must be empty and staging dir must not contain candidate
        assertNull(assetManager.getStagedSlot())
        val stagingDir = File(File(tempDir, "ai_models"), "staging")
        val stagedFiles = stagingDir.listFiles()?.filter { it.isFile && it.name.startsWith("candidate") } ?: emptyList()
        assertTrue("Staged candidate must be cleaned up", stagedFiles.isEmpty())

        // Active baseline model preserved
        assertEquals("baseline", assetManager.getActiveSlot()?.slotId)
        assertEquals(originalBaselineBytes.toList(), baselineFile.readBytes().toList())
    }

    // 3. Invalid model format
    @Test
    fun stageCandidateFromUri_invalidFormat_rejectedAndPreservesBaseline() = runTest {
        val baselineFile = createBaselineModel()
        val originalBaselineBytes = baselineFile.readBytes()

        val candidateBytes = ("some-text-file-content".repeat(50)).toByteArray()
        val uri = mockUri("malicious.txt", candidateBytes)

        val result = assetManager.stageCandidateFromUri(uri, "")

        assertTrue("Invalid extension must fail", result.isFailure)
        assertNull(assetManager.getStagedSlot())
        assertEquals("baseline", assetManager.getActiveSlot()?.slotId)
        assertEquals(originalBaselineBytes.toList(), baselineFile.readBytes().toList())
    }

    // 4. Interrupted or failed import stream
    @Test
    fun stageCandidateFromUri_interruptedStream_cleansUpAndPreservesBaseline() = runTest {
        val baselineFile = createBaselineModel()
        val originalBaselineBytes = baselineFile.readBytes()

        val uri: Uri = mockk(relaxed = true)
        every { uri.lastPathSegment } returns "candidate.task"
        every { mockContentResolver.openInputStream(uri) } answers {
            object : InputStream() {
                override fun read(): Int = throw IOException("Simulated network/disk disconnect")
            }
        }

        val result = assetManager.stageCandidateFromUri(uri, "")

        assertTrue("Interrupted import must fail", result.isFailure)
        assertNull(assetManager.getStagedSlot())
        assertEquals("baseline", assetManager.getActiveSlot()?.slotId)
        assertEquals(originalBaselineBytes.toList(), baselineFile.readBytes().toList())
    }

    // 5. Activation of a validated candidate promotes to ACTIVE and retains baseline as PREVIOUS
    @Test
    fun activateStagedModel_promotesToActiveAndRetainsPrevious() = runTest {
        val baselineFile = createBaselineModel()
        val originalBaselineBytes = baselineFile.readBytes()

        val candidateBytes = ("new-candidate-gemma-model-weights".repeat(40)).toByteArray()
        val candidateHash = MessageDigest.getInstance("SHA-256")
            .digest(candidateBytes)
            .joinToString("") { "%02x".format(it) }
        val uri = mockUri("candidate.task", candidateBytes)

        assetManager.stageCandidateFromUri(uri, candidateHash)
        val activationResult = assetManager.activateStagedModel()

        assertTrue("Activation must succeed", activationResult.isSuccess)
        val activeSlot = assetManager.getActiveSlot()
        val prevSlot = assetManager.getPreviousSlot()

        assertNotNull("Active slot must exist", activeSlot)
        assertEquals("slot_a", activeSlot?.slotId)
        assertEquals(ModelSlotRole.ACTIVE, activeSlot?.role)
        assertEquals(candidateHash, activeSlot?.sha256)

        assertNotNull("Previous slot must be retained for rollback", prevSlot)
        assertEquals("baseline", prevSlot?.slotId)
        assertEquals(ModelSlotRole.PREVIOUS, prevSlot?.role)

        // Verify baseline file is still fully intact on disk
        assertTrue(baselineFile.exists())
        assertEquals(originalBaselineBytes.toList(), baselineFile.readBytes().toList())
    }

    // 6. Rollback to the retained baseline
    @Test
    fun rollbackToPrevious_restoresBaselineAsActive() = runTest {
        val baselineFile = createBaselineModel()
        val originalBaselineBytes = baselineFile.readBytes()

        val candidateBytes = ("candidate-model-weights-slot-b".repeat(40)).toByteArray()
        val candidateHash = MessageDigest.getInstance("SHA-256")
            .digest(candidateBytes)
            .joinToString("") { "%02x".format(it) }
        val uri = mockUri("candidate.task", candidateBytes)

        assetManager.stageCandidateFromUri(uri, candidateHash)
        assetManager.activateStagedModel()

        assertEquals("slot_a", assetManager.getActiveSlot()?.slotId)
        assertEquals("baseline", assetManager.getPreviousSlot()?.slotId)

        // Perform rollback
        val rollbackResult = assetManager.rollbackToPrevious()
        assertTrue("Rollback must succeed", rollbackResult.isSuccess)

        val restoredActive = assetManager.getActiveSlot()
        val restoredPrev = assetManager.getPreviousSlot()

        assertEquals("baseline", restoredActive?.slotId)
        assertEquals(ModelSlotRole.ACTIVE, restoredActive?.role)
        assertEquals("slot_a", restoredPrev?.slotId)
        assertEquals(ModelSlotRole.PREVIOUS, restoredPrev?.role)

        val verifiedFile = assetManager.getVerifiedModelFile()
        assertEquals(baselineFile.absolutePath, verifiedFile?.absolutePath)
        assertEquals(originalBaselineBytes.toList(), baselineFile.readBytes().toList())
    }

    // 7. Process restart with a valid active-model manifest loads slots accurately
    @Test
    fun processRestart_withValidManifest_restoresSlotsAccurately() = runTest {
        val baselineFile = createBaselineModel()
        val candidateBytes = ("candidate-model-persist-test".repeat(40)).toByteArray()
        val candidateHash = MessageDigest.getInstance("SHA-256")
            .digest(candidateBytes)
            .joinToString("") { "%02x".format(it) }
        val uri = mockUri("candidate.task", candidateBytes)

        assetManager.stageCandidateFromUri(uri, candidateHash)
        assetManager.activateStagedModel()

        // Simulate app kill & process restart by creating fresh manager instance
        val restartedManager = ModelAssetManagerImpl(mockContext)

        val activeSlot = restartedManager.getActiveSlot()
        val prevSlot = restartedManager.getPreviousSlot()

        assertNotNull("Active slot must persist across process restart", activeSlot)
        assertEquals("slot_a", activeSlot?.slotId)
        assertEquals(candidateHash, activeSlot?.sha256)

        assertNotNull("Previous slot must persist across process restart", prevSlot)
        assertEquals("baseline", prevSlot?.slotId)
        assertEquals(baselineFile.absolutePath, prevSlot?.file?.absolutePath)
    }

    // 8. Corrupt or missing manifest recovery
    @Test
    fun corruptManifestRecovery_safelyRecoversToExistingModelOnDisk() = runTest {
        val baselineFile = createBaselineModel()
        val modelsSubDir = File(tempDir, "ai_models").apply { mkdirs() }
        val registryFile = File(modelsSubDir, "model_registry.json")

        // Corrupt registry file with malformed JSON
        registryFile.writeText("{ corrupt_json: [ unclosed_array ")

        val recoveredManager = ModelAssetManagerImpl(mockContext)
        val verifiedFile = recoveredManager.getVerifiedModelFile()

        assertNotNull("Manager must recover and return baseline model without throwing", verifiedFile)
        assertEquals(baselineFile.absolutePath, verifiedFile?.absolutePath)
    }

    // 9. Concurrent activation, rollback and slot inspection serialized safely
    @Test
    fun concurrentLifecycleOperations_areSerializedSafelyWithoutExceptions() = runTest {
        val baselineFile = createBaselineModel()
        val candidateBytes = ("candidate-concurrent-test".repeat(40)).toByteArray()
        val candidateHash = MessageDigest.getInstance("SHA-256")
            .digest(candidateBytes)
            .joinToString("") { "%02x".format(it) }
        val uri = mockUri("candidate.task", candidateBytes)

        assetManager.stageCandidateFromUri(uri, candidateHash)

        // Launch concurrent operations
        val d1 = async(Dispatchers.IO) { assetManager.activateStagedModel() }
        val d2 = async(Dispatchers.IO) { assetManager.getActiveSlot() }
        val d3 = async(Dispatchers.IO) { assetManager.getPreviousSlot() }

        val results = awaitAll(d1, d2, d3)
        assertNotNull(results)

        val activeSlot = assetManager.getActiveSlot()
        assertNotNull("Active slot must be consistent after concurrent operations", activeSlot)
    }

    // 10. Preservation of previous known-good model in corrupt activation path
    @Test
    fun activationFailure_whenStagedFileCorrupted_abortsAndPreservesBaseline() = runTest {
        val baselineFile = createBaselineModel()
        val originalBaselineBytes = baselineFile.readBytes()

        val candidateBytes = ("candidate-to-be-corrupted".repeat(40)).toByteArray()
        val candidateHash = MessageDigest.getInstance("SHA-256")
            .digest(candidateBytes)
            .joinToString("") { "%02x".format(it) }
        val uri = mockUri("candidate.task", candidateBytes)

        assetManager.stageCandidateFromUri(uri, candidateHash)

        // Tamper with the staged candidate on disk before activation
        val stagingDir = File(File(tempDir, "ai_models"), "staging")
        val stagedFile = File(stagingDir, "candidate.task")
        stagedFile.writeText("tampered-corrupted-candidate-content")

        // Activation must detect hash mismatch and fail
        val activationResult = assetManager.activateStagedModel()
        assertTrue("Activation of tampered staged model must fail", activationResult.isFailure)

        // Baseline model must remain active and completely intact
        val activeSlot = assetManager.getActiveSlot()
        assertEquals("baseline", activeSlot?.slotId)
        assertEquals(originalBaselineBytes.toList(), baselineFile.readBytes().toList())
    }

    // 11. Legacy verification paths preserved
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

        val manifestJson = org.json.JSONObject().apply {
            put("modelId", "gemma-3-1b-it")
            put("fileName", "gemma.task")
            put("pinnedSha256", expectedSha256)
            put("sha256", expectedSha256)
        }
        File(modelsSubDir, "manifest.json").writeText(manifestJson.toString())

        assetManager.confirmModelHash(expectedSha256)
        val result = assetManager.getVerifiedModelFile()
        assertNotNull(result)
        assertEquals(modelFile.absolutePath, result?.absolutePath)
    }

    // 12. Delete installed model clears slots and staging but preserves baseline
    @Test
    fun deleteInstalledModel_removesSlotsAndStaging() = runTest {
        val baselineFile = createBaselineModel()
        val candidateBytes = ("candidate-delete-test".repeat(40)).toByteArray()
        val uri = mockUri("candidate.task", candidateBytes)

        assetManager.stageCandidateFromUri(uri, "")
        assetManager.activateStagedModel()

        val deleteResult = assetManager.deleteInstalledModel()
        assertTrue(deleteResult.isSuccess)

        assertNull("Staged slot must be null after delete", assetManager.getStagedSlot())
        assertTrue("Baseline file in files/models must be preserved", baselineFile.exists())
    }

    // 13. Rejection of LiteRT-LM format prior to activation
    @Test
    fun activateStagedModel_whenFormatIsLiteRtLm_abortsActivationAndPreservesBaseline() = runTest {
        val baselineFile = createBaselineModel()
        val originalBaselineBytes = baselineFile.readBytes()

        val candidateBytes = ("candidate-litertlm-bytes-padding".repeat(40)).toByteArray()
        val candidateHash = MessageDigest.getInstance("SHA-256")
            .digest(candidateBytes)
            .joinToString("") { "%02x".format(it) }
        val uri = mockUri("candidate.litertlm", candidateBytes)

        val stageResult = assetManager.stageCandidateFromUri(uri, candidateHash)
        assertTrue("Staging of file can proceed to isolated staging area", stageResult.isSuccess)

        // Activation must fail because Tasks GenAI runtime does not support .litertlm
        val activationResult = assetManager.activateStagedModel()
        assertTrue("Activation of LiteRT-LM on Tasks GenAI must fail", activationResult.isFailure)

        val error = (activationResult as com.meshlink.app.ai.model.AiResult.Failure).error
        assertTrue(error is AiError.RuntimeError)
        val msg = (error as AiError.RuntimeError).message
        assertTrue(msg.contains("Incompatible model format") && msg.contains("litertlm"))

        // Baseline model remains active and completely untouched
        val activeSlot = assetManager.getActiveSlot()
        assertEquals("baseline", activeSlot?.slotId)
        assertEquals(originalBaselineBytes.toList(), baselineFile.readBytes().toList())
    }

    // 14. Rejection of empty candidate file
    @Test
    fun stageCandidateFromUri_whenEmptyFile_failsWithModelCorrupt() = runTest {
        val baselineFile = createBaselineModel()
        val originalBaselineBytes = baselineFile.readBytes()

        val uri = mockUri("empty.task", ByteArray(0))
        val result = assetManager.stageCandidateFromUri(uri, "")

        assertTrue(result.isFailure)
        assertTrue((result as com.meshlink.app.ai.model.AiResult.Failure).error is AiError.ModelCorrupt)
        assertNull(assetManager.getStagedSlot())
        assertEquals(originalBaselineBytes.toList(), baselineFile.readBytes().toList())
    }

    // 15. Rejection of truncated candidate file (< 1024 bytes)
    @Test
    fun stageCandidateFromUri_whenTruncatedFile_failsWithModelCorrupt() = runTest {
        val baselineFile = createBaselineModel()
        val originalBaselineBytes = baselineFile.readBytes()

        val truncatedBytes = ByteArray(100) { 0x01 }
        val uri = mockUri("truncated.task", truncatedBytes)
        val result = assetManager.stageCandidateFromUri(uri, "")

        assertTrue(result.isFailure)
        assertTrue((result as com.meshlink.app.ai.model.AiResult.Failure).error is AiError.ModelCorrupt)
        assertNull(assetManager.getStagedSlot())
        assertEquals(originalBaselineBytes.toList(), baselineFile.readBytes().toList())
    }

    // 16. Recovery from .bak when registry is corrupted by crash
    @Test
    fun crashRecovery_whenRegistryCorruptButBackupExists_recoversFromBackup() = runTest {
        val baselineFile = createBaselineModel()
        val modelsSubDir = File(tempDir, "ai_models").apply { mkdirs() }
        val registryFile = File(modelsSubDir, "model_registry.json")
        val bakFile = File(modelsSubDir, "model_registry.json.bak")

        // First activate a candidate to generate valid registry (> 1024 bytes)
        val candidateBytes = ("candidate-backup-test".repeat(60)).toByteArray()
        val candidateHash = MessageDigest.getInstance("SHA-256")
            .digest(candidateBytes)
            .joinToString("") { "%02x".format(it) }
        val uri = mockUri("candidate.task", candidateBytes)
        val stageResult = assetManager.stageCandidateFromUri(uri, candidateHash)
        assertTrue("Stage candidate must succeed", stageResult.isSuccess)
        val activateResult = assetManager.activateStagedModel()
        assertTrue("Activate staged model must succeed: $activateResult", activateResult.isSuccess)

        // Copy registry to .bak and corrupt primary registry with truncated JSON
        assertTrue("Registry file must exist before backup", registryFile.exists())
        registryFile.copyTo(bakFile, overwrite = true)
        registryFile.writeText("{ \"version\": 1, \"activeSlotId\": ") // truncated mid-write

        val recoveredManager = ModelAssetManagerImpl(mockContext)
        val activeSlot = recoveredManager.getActiveSlot()

        assertNotNull("Must recover from .bak file", activeSlot)
        assertEquals("slot_a", activeSlot?.slotId)
        assertEquals(candidateHash, activeSlot?.sha256)
    }

    // 17. Automatic recovery to previous slot when active model file is missing on disk
    @Test
    fun crashRecovery_whenActiveFileMissingOnDisk_recoversToPreviousSlot() = runTest {
        val baselineFile = createBaselineModel()
        val candidateBytes = ("candidate-active-missing-test".repeat(40)).toByteArray()
        val candidateHash = MessageDigest.getInstance("SHA-256")
            .digest(candidateBytes)
            .joinToString("") { "%02x".format(it) }
        val uri = mockUri("candidate.task", candidateBytes)

        assetManager.stageCandidateFromUri(uri, candidateHash)
        assetManager.activateStagedModel()

        val activeSlot = assetManager.getActiveSlot()
        assertNotNull(activeSlot)
        assertEquals("slot_a", activeSlot?.slotId)

        // Delete the active model file from disk (e.g. process terminated or storage wiped)
        activeSlot!!.file.delete()
        assertFalse(activeSlot.file.exists())

        // Recreate manager to simulate restart and automatic recovery
        val restartedManager = ModelAssetManagerImpl(mockContext)
        val recoveredActive = restartedManager.getActiveSlot()

        assertNotNull("Should recover previous baseline model as active", recoveredActive)
        assertEquals("baseline", recoveredActive?.slotId)
        assertEquals(baselineFile.absolutePath, recoveredActive?.file?.absolutePath)
        assertTrue(baselineFile.exists())
    }

    // 18. Rollback fails gracefully without corrupting active model when previous file is missing
    @Test
    fun rollbackToPrevious_whenPreviousSlotFileMissing_failsGracefullyWithoutCorruptingActive() = runTest {
        val baselineFile = createBaselineModel()
        val candidateBytes = ("candidate-rollback-missing-test".repeat(40)).toByteArray()
        val candidateHash = MessageDigest.getInstance("SHA-256")
            .digest(candidateBytes)
            .joinToString("") { "%02x".format(it) }
        val uri = mockUri("candidate.task", candidateBytes)

        assetManager.stageCandidateFromUri(uri, candidateHash)
        assetManager.activateStagedModel()

        // Delete the previous baseline file
        baselineFile.delete()

        val rollbackResult = assetManager.rollbackToPrevious()
        assertTrue("Rollback must fail when previous file is missing", rollbackResult.isFailure)
        assertEquals(AiError.ModelMissing, (rollbackResult as com.meshlink.app.ai.model.AiResult.Failure).error)

        // Active slot remains active
        val activeSlot = assetManager.getActiveSlot()
        assertEquals("slot_a", activeSlot?.slotId)
        assertTrue(activeSlot!!.file.exists())
    }

    // 19. Rollback fails gracefully when previous file has corrupted checksum
    @Test
    fun rollbackToPrevious_whenPreviousSlotFileCorrupted_failsGracefully() = runTest {
        val baselineFile = createBaselineModel()
        val candidateBytes = ("candidate-corrupt-rollback-test".repeat(40)).toByteArray()
        val candidateHash = MessageDigest.getInstance("SHA-256")
            .digest(candidateBytes)
            .joinToString("") { "%02x".format(it) }
        val uri = mockUri("candidate.task", candidateBytes)

        assetManager.stageCandidateFromUri(uri, candidateHash)
        assetManager.activateStagedModel()

        // Corrupt previous baseline content on disk
        baselineFile.writeText("corrupted-tampered-baseline-bytes")

        val rollbackResult = assetManager.rollbackToPrevious()
        assertTrue("Rollback must fail on checksum mismatch of previous model", rollbackResult.isFailure)
        assertTrue((rollbackResult as com.meshlink.app.ai.model.AiResult.Failure).error is AiError.ModelCorrupt)

        // Active slot remains active
        val activeSlot = assetManager.getActiveSlot()
        assertEquals("slot_a", activeSlot?.slotId)
    }

    // 20. Activation fails when staged candidate file is missing
    @Test
    fun activateStagedModel_whenStagedFileDeletedBeforeActivation_returnsModelMissing() = runTest {
        val baselineFile = createBaselineModel()
        val candidateBytes = ("candidate-missing-before-act".repeat(40)).toByteArray()
        val uri = mockUri("candidate.task", candidateBytes)

        assetManager.stageCandidateFromUri(uri, "")
        val stagedSlot = assetManager.getStagedSlot()
        assertNotNull(stagedSlot)

        // Delete staged candidate file before calling activateStagedModel
        stagedSlot!!.file.delete()

        val activationResult = assetManager.activateStagedModel()
        assertTrue(activationResult.isFailure)
        assertEquals(AiError.ModelMissing, (activationResult as com.meshlink.app.ai.model.AiResult.Failure).error)

        // Baseline preserved
        assertEquals("baseline", assetManager.getActiveSlot()?.slotId)
    }
}
