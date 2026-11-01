package com.meshlink.app.ai.domain

import android.net.Uri
import com.meshlink.app.ai.model.AiResult
import com.meshlink.app.ai.model.ModelManifest
import java.io.File


enum class ModelSlotRole {
    ACTIVE,
    STAGED,
    PREVIOUS
}

data class ModelSlot(
    val slotId: String,
    val file: File,
    val sha256: String,
    val sizeBytes: Long,
    val format: String,
    val role: ModelSlotRole
)

/**
 * Interface managing offline model provisioning, storage, verification, and lifecycle.
 */
interface ModelAssetManager {
    /**
     * Checks if a valid, verified model exists in app-private storage.
     */
    suspend fun getVerifiedModelFile(): File?

    /**
     * Retrieves the current model manifest if installed.
     */
    suspend fun getInstalledManifest(): ModelManifest?

    /**
     * Imports a model binary from an external URI (Storage Access Framework)
     * using atomic write to temporary file followed by SHA-256 verification.
     */
    suspend fun importModelFromUri(
        sourceUri: Uri,
        expectedSha256: String,
        manifest: ModelManifest? = null
    ): AiResult<File>

    /**
     * Computes the SHA-256 checksum of the target file using buffered streaming.
     */
    suspend fun verifyChecksum(file: File, expectedSha256: String): Boolean

    /**
     * Safely deletes the installed model and reclaims disk storage.
     */
    suspend fun deleteInstalledModel(): AiResult<Unit>

    /**
     * Computes the SHA-256 checksum of the target file using buffered streaming.
     */
    suspend fun computeSha256(file: File): String

    /**
     * Explicitly records that the user inspected and confirmed the computed model hash.
     */
    suspend fun confirmModelHash(hash: String)

    /**
     * Returns true if the given hash has been explicitly confirmed by the user.
     */
    suspend fun isHashConfirmed(hash: String): Boolean

    /**
     * Retrieves the currently active model slot descriptor, if installed and active.
     */
    suspend fun getActiveSlot(): ModelSlot?

    /**
     * Retrieves the staged candidate model slot descriptor, if staged.
     */
    suspend fun getStagedSlot(): ModelSlot?

    /**
     * Retrieves the retained model slot descriptor available for rollback, if retained.
     */
    suspend fun getPreviousSlot(): ModelSlot?

    /**
     * Stages a candidate model from an external URI into a staging slot without touching or
     * overwriting the active baseline model. Verifies storage space, computes SHA-256,
     * and validates format.
     */
    suspend fun stageCandidateFromUri(
        sourceUri: Uri,
        expectedSha256: String = ""
    ): AiResult<File>

    /**
     * Validates and activates the currently staged candidate model.
     * Retains the existing active model as PREVIOUS for rollback.
     */
    suspend fun activateStagedModel(): AiResult<File>

    /**
     * Explicit rollback operation that restores the PREVIOUS model as ACTIVE.
     */
    suspend fun rollbackToPrevious(): AiResult<File>

    /**
     * Safely discards the currently staged candidate model without touching active or previous models.
     */
    suspend fun discardStagedModel(): AiResult<Unit>
}
