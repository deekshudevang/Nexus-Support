package com.meshlink.app.ai.data

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.meshlink.app.ai.domain.ModelAssetManager
import com.meshlink.app.ai.model.AiError
import com.meshlink.app.ai.model.AiResult
import com.meshlink.app.ai.model.ModelFileDescriptor
import com.meshlink.app.ai.model.ModelManifest
import com.meshlink.app.ai.security.AiLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelAssetManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ModelAssetManager {

    private val modelsDir: File
        get() = File(context.filesDir, "ai_models").apply { if (!exists()) mkdirs() }

    private val primaryModelName = "gemma.bin"

    override suspend fun getVerifiedModelFile(): File? = withContext(Dispatchers.IO) {
        val modelFile = File(modelsDir, primaryModelName)
        if (modelFile.exists() && modelFile.length() > 0L) {
            return@withContext modelFile
        }

        // Also check legacy root filesDir location for backward compatibility
        val legacyFile = File(context.filesDir, primaryModelName)
        if (legacyFile.exists() && legacyFile.length() > 0L) {
            return@withContext legacyFile
        }

        null
    }

    override suspend fun getInstalledManifest(): ModelManifest? = withContext(Dispatchers.IO) {
        val file = getVerifiedModelFile() ?: return@withContext null
        ModelManifest(
            schemaVersion = 1,
            modelId = "gemma-2b-it",
            revision = "v1.1",
            format = "mediapipe_task",
            runtime = "tasks-genai",
            files = listOf(
                ModelFileDescriptor(
                    fileName = file.name,
                    sizeBytes = file.length(),
                    sha256Checksum = ""
                )
            ),
            licence = "Gemma Terms of Use",
            capabilities = listOf("text_generation", "rag", "summarization"),
            minRamMb = 2048,
            source = "User SAF Import / Local Provisioning"
        )
    }

    override suspend fun importModelFromUri(
        sourceUri: Uri,
        expectedSha256: String,
        manifest: ModelManifest?
    ): AiResult<File> = withContext(Dispatchers.IO) {
        val targetFile = File(modelsDir, primaryModelName)
        val tempFile = File(modelsDir, "$primaryModelName.tmp")

        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(sourceUri)
                ?: return@withContext AiResult.Failure(AiError.ModelMissing)

            // Storage space pre-check
            val statFs = StatFs(modelsDir.absolutePath)
            val availableBytes = statFs.availableBytes
            val estimatedModelSize = 1024L * 1024L * 500L // 500MB safety threshold

            if (availableBytes < estimatedModelSize) {
                inputStream.close()
                AiLogger.w("Insufficient disk storage for model import (available=$availableBytes bytes)")
                return@withContext AiResult.Failure(AiError.LowStorage)
            }

            AiLogger.i("Starting streaming copy for model from URI into temporary file")
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65536) // 64 KB chunk

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            val computedChecksum = digest.digest().joinToString("") { "%02x".format(it) }
            AiLogger.i("Computed model SHA-256: $computedChecksum")

            // If expectedSha256 is provided and non-blank, enforce strict match
            if (expectedSha256.isNotBlank() && !computedChecksum.equals(expectedSha256.trim(), ignoreCase = true)) {
                tempFile.delete()
                AiLogger.w("Checksum mismatch: expected=$expectedSha256, actual=$computedChecksum")
                return@withContext AiResult.Failure(
                    AiError.ModelCorrupt("Checksum mismatch: expected $expectedSha256, got $computedChecksum")
                )
            }

            // Atomic install via file rename
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.delete()
                return@withContext AiResult.Failure(AiError.RuntimeError("Failed to rename temporary model file"))
            }

            AiLogger.i("Model successfully imported and verified: ${targetFile.length()} bytes")
            AiResult.Success(targetFile)
        } catch (e: Exception) {
            tempFile.delete()
            AiLogger.e(e, "Error importing model from URI")
            AiResult.Failure(AiError.RuntimeError(e.message ?: "Model import failed"))
        }
    }

    override suspend fun verifyChecksum(file: File, expectedSha256: String): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L || expectedSha256.isBlank()) return@withContext false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65536)
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val computed = digest.digest().joinToString("") { "%02x".format(it) }
            computed.equals(expectedSha256.trim(), ignoreCase = true)
        } catch (e: Exception) {
            AiLogger.e(e, "Checksum verification failed")
            false
        }
    }

    override suspend fun deleteInstalledModel(): AiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(modelsDir, primaryModelName)
            if (file.exists()) {
                file.delete()
            }
            val legacyFile = File(context.filesDir, primaryModelName)
            if (legacyFile.exists()) {
                legacyFile.delete()
            }
            AiLogger.i("Installed model deleted from storage")
            AiResult.Success(Unit)
        } catch (e: Exception) {
            AiLogger.e(e, "Error deleting installed model")
            AiResult.Failure(AiError.RuntimeError(e.message ?: "Failed to delete model"))
        }
    }
}
