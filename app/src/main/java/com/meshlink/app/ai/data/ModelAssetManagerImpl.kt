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

    private val supportedModelNames = listOf(
        "model.litertlm",
        "gemma.litertlm",
        "gemma.task",
        "model.task",
        "gemma.bin",
        "model.bin"
    )

    private val confirmationFile: File
        get() = File(modelsDir, "confirmed_hash.txt")

    fun findCandidateModelFile(): File? {
        val existingFile = modelsDir.listFiles()?.firstOrNull { file ->
            file.isFile && file.length() > 0L && (
                file.name.endsWith(".task") ||
                file.name.endsWith(".bin") ||
                file.name.endsWith(".litertlm")
            )
        }
        if (existingFile != null) return existingFile

        for (name in supportedModelNames) {
            val file = File(modelsDir, name)
            if (file.exists() && file.length() > 0L) return file
        }

        for (name in supportedModelNames) {
            val legacyFile = File(context.filesDir, name)
            if (legacyFile.exists() && legacyFile.length() > 0L) return legacyFile
        }

        return null
    }

    override suspend fun getVerifiedModelFile(): File? = withContext(Dispatchers.IO) {
        val candidate = findCandidateModelFile() ?: return@withContext null

        val manifestFile = File(modelsDir, "manifest.json")
        val pinnedSha256 = if (manifestFile.exists()) {
            try {
                val json = org.json.JSONObject(manifestFile.readText())
                val p = json.optString("pinnedSha256", "").ifBlank { json.optString("sha256", "") }
                p.trim()
            } catch (e: Exception) { "" }
        } else ""

        val computedHash = computeSha256(candidate)
        if (computedHash.isBlank()) {
            AiLogger.w("Unable to compute SHA-256 for model candidate at ${candidate.name}")
            return@withContext null
        }

        // Path 1: Pinned SHA-256 in manifest
        if (pinnedSha256.isNotBlank()) {
            if (computedHash.equals(pinnedSha256, ignoreCase = true)) {
                AiLogger.i("Model candidate successfully verified via pinned manifest SHA-256")
                return@withContext candidate
            } else {
                AiLogger.w("Model checksum mismatch against pinned manifest! Expected: $pinnedSha256, computed: $computedHash")
                return@withContext null
            }
        }

        // Path 2: Explicit user confirmation
        if (isHashConfirmed(computedHash)) {
            AiLogger.i("Model candidate verified via explicit user confirmation: $computedHash")
            return@withContext candidate
        }

        AiLogger.w("Model loading blocked: hash $computedHash is neither pinned in manifest nor confirmed by user.")
        null
    }

    override suspend fun getInstalledManifest(): ModelManifest? = withContext(Dispatchers.IO) {
        val file = getVerifiedModelFile() ?: return@withContext null
        val manifestFile = File(modelsDir, "manifest.json")
        val sha256 = if (manifestFile.exists()) {
            try {
                org.json.JSONObject(manifestFile.readText()).optString("sha256", "")
            } catch (e: Exception) { "" }
        } else ""

        val format = when {
            file.name.endsWith(".litertlm") -> "litertlm"
            file.name.endsWith(".task") -> "mediapipe_task"
            else -> "binary_flatbuffer"
        }

        ModelManifest(
            schemaVersion = 1,
            modelId = "gemma-3-1b-it",
            revision = "v1.0",
            format = format,
            runtime = if (format == "litertlm") "litert-lm" else "tasks-genai",
            files = listOf(
                ModelFileDescriptor(
                    fileName = file.name,
                    sizeBytes = file.length(),
                    sha256Checksum = sha256
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
        val tempFile = File(modelsDir, "import_staging.tmp")

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

            // Extract file extension and name from URI if queryable, else default to .litertlm or .task
            val queryName = context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            } ?: sourceUri.lastPathSegment ?: "model.litertlm"

            val targetExtension = when {
                queryName.endsWith(".litertlm") -> ".litertlm"
                queryName.endsWith(".task") -> ".task"
                queryName.endsWith(".bin") -> ".bin"
                else -> ".litertlm"
            }

            val targetFileName = "model$targetExtension"
            val targetFile = File(modelsDir, targetFileName)

            // Atomic install via file rename
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.delete()
                return@withContext AiResult.Failure(AiError.RuntimeError("Failed to rename temporary model file"))
            }

            // Write or update manifest.json with SHA-256 and metadata
            try {
                val manifestJson = org.json.JSONObject().apply {
                    put("modelId", "gemma-3-1b-it")
                    put("fileName", targetFileName)
                    put("sha256", computedChecksum)
                    if (expectedSha256.isNotBlank()) {
                        put("pinnedSha256", expectedSha256.trim())
                    }
                    put("sizeBytes", targetFile.length())
                    put("format", targetExtension.removePrefix("."))
                    put("timestamp", System.currentTimeMillis())
                }
                File(modelsDir, "manifest.json").writeText(manifestJson.toString(2))
            } catch (e: Exception) {
                AiLogger.w("Failed to persist manifest.json: ${e.message}")
            }

            AiLogger.i("Model successfully imported and verified: ${targetFile.length()} bytes (SHA-256: $computedChecksum)")
            AiResult.Success(targetFile)
        } catch (e: Exception) {
            tempFile.delete()
            AiLogger.e(e, "Error importing model from URI")
            AiResult.Failure(AiError.RuntimeError(e.message ?: "Model import failed"))
        }
    }

    override suspend fun computeSha256(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext ""
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65536)
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            AiLogger.e(e, "Error computing SHA-256 for ${file.name}")
            ""
        }
    }

    override suspend fun confirmModelHash(hash: String) = withContext(Dispatchers.IO) {
        if (hash.isNotBlank()) {
            confirmationFile.writeText(hash.trim().lowercase())
            AiLogger.i("User confirmed model hash: ${hash.trim().lowercase()}")
        }
    }

    override suspend fun isHashConfirmed(hash: String): Boolean = withContext(Dispatchers.IO) {
        if (hash.isBlank() || !confirmationFile.exists()) return@withContext false
        val saved = confirmationFile.readText().trim().lowercase()
        saved.equals(hash.trim().lowercase(), ignoreCase = true)
    }

    override suspend fun verifyChecksum(file: File, expectedSha256: String): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L || expectedSha256.isBlank()) return@withContext false
        val computed = computeSha256(file)
        computed.equals(expectedSha256.trim(), ignoreCase = true)
    }

    override suspend fun deleteInstalledModel(): AiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            if (confirmationFile.exists()) {
                confirmationFile.delete()
            }
            modelsDir.listFiles()?.forEach { file ->
                if (file.isFile && (
                    file.name.endsWith(".litertlm") ||
                    file.name.endsWith(".task") ||
                    file.name.endsWith(".bin") ||
                    file.name == "manifest.json" ||
                    file.name == "confirmed_hash.txt" ||
                    file.name.endsWith(".tmp")
                )) {
                    file.delete()
                }
            }
            for (name in supportedModelNames) {
                val legacyFile = File(context.filesDir, name)
                if (legacyFile.exists()) {
                    legacyFile.delete()
                }
            }
            AiLogger.i("Installed model, manifest, and confirmations deleted from storage")
            AiResult.Success(Unit)
        } catch (e: Exception) {
            AiLogger.e(e, "Error deleting installed model")
            AiResult.Failure(AiError.RuntimeError(e.message ?: "Failed to delete model"))
        }
    }
}
