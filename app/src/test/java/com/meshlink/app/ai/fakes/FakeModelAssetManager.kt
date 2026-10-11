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
}
