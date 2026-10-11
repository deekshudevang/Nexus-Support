package com.meshlink.app.ai.domain

import android.net.Uri
import com.meshlink.app.ai.model.AiResult
import com.meshlink.app.ai.model.ModelManifest
import java.io.File

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
}
