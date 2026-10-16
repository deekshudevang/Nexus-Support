package com.meshlink.app.ai.fakes

import android.net.Uri
import com.meshlink.app.ai.domain.ModelAssetManager
import com.meshlink.app.ai.model.AiResult
import com.meshlink.app.ai.model.ModelManifest
import java.io.File

/**
 * Test-only fake for ModelAssetManager. Strictly isolated to test source set.
 */
class FakeModelAssetManager : ModelAssetManager {
    var modelFileToReturn: File? = null
    var manifestToReturn: ModelManifest? = null
    var shouldChecksumMatch = true
    var deleteCalled = false

    override suspend fun getVerifiedModelFile(): File? = modelFileToReturn

    override suspend fun getInstalledManifest(): ModelManifest? = manifestToReturn

    override suspend fun importModelFromUri(
        sourceUri: Uri,
        expectedSha256: String,
        manifest: ModelManifest?
    ): AiResult<File> {
        val file = modelFileToReturn ?: File.createTempFile("fake_model", ".bin")
        return AiResult.Success(file)
    }

    override suspend fun verifyChecksum(file: File, expectedSha256: String): Boolean = shouldChecksumMatch

    override suspend fun deleteInstalledModel(): AiResult<Unit> {
        deleteCalled = true
        modelFileToReturn = null
        return AiResult.Success(Unit)
    }

    override suspend fun computeSha256(file: File): String = "mock_computed_hash_sha256"

    private var confirmedHash: String? = null

    override suspend fun confirmModelHash(hash: String) {
        confirmedHash = hash
    }

    override suspend fun isHashConfirmed(hash: String): Boolean = confirmedHash == hash

    var activeSlotToReturn: com.meshlink.app.ai.domain.ModelSlot? = null
    var stagedSlotToReturn: com.meshlink.app.ai.domain.ModelSlot? = null
    var previousSlotToReturn: com.meshlink.app.ai.domain.ModelSlot? = null

    var stageCandidateResult: AiResult<File>? = null
    var activateStagedResult: AiResult<File>? = null
    var rollbackResult: AiResult<File>? = null

    override suspend fun getActiveSlot(): com.meshlink.app.ai.domain.ModelSlot? = activeSlotToReturn

    override suspend fun getStagedSlot(): com.meshlink.app.ai.domain.ModelSlot? = stagedSlotToReturn

    override suspend fun getPreviousSlot(): com.meshlink.app.ai.domain.ModelSlot? = previousSlotToReturn

    override suspend fun stageCandidateFromUri(sourceUri: Uri, expectedSha256: String): AiResult<File> {
        val result = stageCandidateResult ?: AiResult.Success(File.createTempFile("staged_model", ".task"))
        if (result is AiResult.Success) {
            stagedSlotToReturn = com.meshlink.app.ai.domain.ModelSlot(
                slotId = "staged",
                file = result.data,
                sha256 = expectedSha256.ifBlank { "staged_hash" },
                sizeBytes = result.data.length(),
                format = "task",
                role = com.meshlink.app.ai.domain.ModelSlotRole.STAGED
            )
        }
        return result
    }

    override suspend fun activateStagedModel(): AiResult<File> {
        val result = activateStagedResult ?: run {
            val staged = stagedSlotToReturn?.file ?: File.createTempFile("activated_model", ".task")
            AiResult.Success(staged)
        }
        if (result is AiResult.Success) {
            previousSlotToReturn = activeSlotToReturn?.copy(role = com.meshlink.app.ai.domain.ModelSlotRole.PREVIOUS)
            activeSlotToReturn = com.meshlink.app.ai.domain.ModelSlot(
                slotId = "slot_active",
                file = result.data,
                sha256 = "activated_hash",
                sizeBytes = result.data.length(),
                format = "task",
                role = com.meshlink.app.ai.domain.ModelSlotRole.ACTIVE
            )
            stagedSlotToReturn = null
            modelFileToReturn = result.data
        }
        return result
    }

    override suspend fun rollbackToPrevious(): AiResult<File> {
        val result = rollbackResult ?: run {
            val prev = previousSlotToReturn?.file ?: return AiResult.Failure(com.meshlink.app.ai.model.AiError.ModelMissing)
            AiResult.Success(prev)
        }
        if (result is AiResult.Success) {
            val oldActive = activeSlotToReturn
            activeSlotToReturn = previousSlotToReturn?.copy(role = com.meshlink.app.ai.domain.ModelSlotRole.ACTIVE)
            previousSlotToReturn = oldActive?.copy(role = com.meshlink.app.ai.domain.ModelSlotRole.PREVIOUS)
            modelFileToReturn = result.data
        }
        return result
    }

    override suspend fun discardStagedModel(): AiResult<Unit> {
        stagedSlotToReturn = null
        return AiResult.Success(Unit)
    }
}
