package com.meshlink.app.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meshlink.app.ai.data.ModelAssetManagerImpl
import com.meshlink.app.ai.domain.ModelSlotRole
import com.meshlink.app.ai.inference.MediaPipeLocalAiEngine
import com.meshlink.app.ai.model.AiResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class DualSlotRollbackDeviceTest {

    private val expectedChecksum = "e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee"
    private val expectedSize = 554661243L

    @Test
    fun physicalDualSlotLifecycle_activate_rollback_verifyIntegrity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetManager = ModelAssetManagerImpl(context)
        val engine = MediaPipeLocalAiEngine(context)

        println("=== PHASE 0D: PHYSICAL DUAL-SLOT ROLLBACK TEST START ===")

        // STEP 1 & 2: Preflight verification of on-device artifacts
        val baselineFile = File(File(context.filesDir, "models"), "gemma3-1B-it-int4.task")
        assertTrue("Baseline model must exist at ${baselineFile.absolutePath}", baselineFile.exists())
        assertEquals("Baseline model size must match", expectedSize, baselineFile.length())

        val baselineHash = computeSha256(baselineFile)
        println("Baseline model hash: $baselineHash")
        assertEquals("Baseline model hash must match known-good Gemma 3", expectedChecksum, baselineHash)

        val stagingCandidate = File(File(File(context.filesDir, "ai_models"), "staging"), "candidate.task")
        assertTrue("Staged candidate copy must exist", stagingCandidate.exists())
        assertEquals("Staged candidate copy size must match", expectedSize, stagingCandidate.length())

        val candidateHash = computeSha256(stagingCandidate)
        println("Staged candidate hash: $candidateHash")
        assertEquals("Candidate copy hash must match baseline", expectedChecksum, candidateHash)

        // Verify initial registry state
        val initialActive = assetManager.getActiveSlot()
        println("Initial active slot: ${initialActive?.slotId}")
        assertNotNull("Initial active slot must exist", initialActive)
        assertEquals("baseline", initialActive?.slotId)

        // STEP 5: Exercise real activation mechanism to promote staged model
        println("Executing real activation mechanism: activateStagedModel()...")
        val activateResult = assetManager.activateStagedModel()
        assertTrue("activateStagedModel must succeed: $activateResult", activateResult is AiResult.Success)
        val activatedFile = (activateResult as AiResult.Success).data
        println("Staged model successfully activated into slot file: ${activatedFile.absolutePath}")

        // Verify slot state post-activation
        val postActivationActive = assetManager.getActiveSlot()
        val postActivationPrevious = assetManager.getPreviousSlot()
        println("Post-activation active slot: ${postActivationActive?.slotId}")
        println("Post-activation retained previous slot: ${postActivationPrevious?.slotId}")

        assertEquals("slot_a", postActivationActive?.slotId)
        assertEquals(ModelSlotRole.ACTIVE, postActivationActive?.role)
        assertEquals("baseline", postActivationPrevious?.slotId)
        assertEquals(ModelSlotRole.PREVIOUS, postActivationPrevious?.role)

        // Baseline model must remain completely intact
        assertTrue("Baseline file must NOT be deleted during activation", baselineFile.exists())
        assertEquals("Baseline size must remain unchanged", expectedSize, baselineFile.length())

        // Test inference with the newly activated slot
        println("Initializing MediaPipe LocalAiEngine with newly activated slot model (${activatedFile.name})...")
        val initResult = engine.initialize(activatedFile)
        assertTrue("Engine initialization with activated slot model must succeed: $initResult", initResult is AiResult.Success)
        assertTrue("Engine must report ready", engine.isReady())

        println("Executing text generation query: 'What is 2 plus 2?'")
        val generateResult = engine.generate("What is 2 plus 2?")
        assertTrue("Generation with activated slot must succeed: $generateResult", generateResult is AiResult.Success)
        val outputText = (generateResult as AiResult.Success).data
        println("Activated slot model response: '$outputText'")
        assertTrue("Output text must be non-blank", outputText.isNotBlank())

        // STEP 6: Exercise real rollback mechanism to return to previous baseline
        println("Unloading engine prior to rollback...")
        engine.unload()
        assertFalse("Engine must be unloaded", engine.isReady())

        println("Executing real rollback mechanism: rollbackToPrevious()...")
        val rollbackResult = assetManager.rollbackToPrevious()
        assertTrue("rollbackToPrevious must succeed: $rollbackResult", rollbackResult is AiResult.Success)
        val rolledBackFile = (rollbackResult as AiResult.Success).data
        println("Rollback restored active model file: ${rolledBackFile.absolutePath}")

        // Verify slot state post-rollback
        val postRollbackActive = assetManager.getActiveSlot()
        val postRollbackPrevious = assetManager.getPreviousSlot()
        println("Post-rollback active slot: ${postRollbackActive?.slotId}")
        println("Post-rollback previous slot: ${postRollbackPrevious?.slotId}")

        assertEquals("baseline", postRollbackActive?.slotId)
        assertEquals(ModelSlotRole.ACTIVE, postRollbackActive?.role)
        assertEquals("slot_a", postRollbackPrevious?.slotId)
        assertEquals(ModelSlotRole.PREVIOUS, postRollbackPrevious?.role)

        // STEP 7: Restart / re-initialize baseline model and verify generation
        println("Re-initializing MediaPipe LocalAiEngine with restored baseline model (${rolledBackFile.name})...")
        val baselineInitResult = engine.initialize(rolledBackFile)
        assertTrue("Engine re-initialization with baseline model must succeed: $baselineInitResult", baselineInitResult is AiResult.Success)
        assertTrue("Engine must report ready after rollback", engine.isReady())

        println("Executing text generation query with restored baseline: 'What is 2 plus 2?'")
        val baselineGenerateResult = engine.generate("What is 2 plus 2?")
        assertTrue("Generation with restored baseline model must succeed", baselineGenerateResult is AiResult.Success)
        val baselineOutputText = (baselineGenerateResult as AiResult.Success).data
        println("Restored baseline model response: '$baselineOutputText'")
        assertTrue("Output text must be non-blank", baselineOutputText.isNotBlank())

        // STEP 8: Verify kill switch deallocation
        println("Triggering kill switch (engine.unload())...")
        engine.unload()
        assertFalse("Engine must report not ready after kill switch", engine.isReady())

        val postKillGenerate = engine.generate("Are you there?")
        assertTrue("Generation must fail after kill switch", postKillGenerate is AiResult.Failure)

        // STEP 9: Final inspection: baseline model was never overwritten or deleted
        assertTrue("Baseline model must exist", baselineFile.exists())
        assertEquals("Baseline size must remain exactly 554661243 bytes", expectedSize, baselineFile.length())
        val finalHash = computeSha256(baselineFile)
        println("Final baseline SHA-256: $finalHash")
        assertEquals("Final baseline checksum must match known-good Gemma 3", expectedChecksum, finalHash)

        println("=== PHASE 0D: PHYSICAL DUAL-SLOT ROLLBACK TEST PASSED SUCCESSFULLY ===")
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        file.inputStream().use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
