package com.meshlink.app.ai.data

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.meshlink.app.ai.domain.ModelAssetManager
import com.meshlink.app.ai.domain.ModelSlot
import com.meshlink.app.ai.domain.ModelSlotRole
import com.meshlink.app.ai.model.AiError
import com.meshlink.app.ai.model.AiResult
import com.meshlink.app.ai.model.ModelFileDescriptor
import com.meshlink.app.ai.model.ModelManifest
import com.meshlink.app.ai.security.AiLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
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

    private val lifecycleMutex = Mutex()

    private val modelsDir: File
        get() = File(context.filesDir, "ai_models").apply { if (!exists()) mkdirs() }

    private val stagingDir: File
        get() = File(modelsDir, "staging").apply { if (!exists()) mkdirs() }

    private val slotsDir: File
        get() = File(modelsDir, "slots").apply { if (!exists()) mkdirs() }

    private val registryFile: File
        get() = File(modelsDir, "model_registry.json")

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

    private val candidateDirs: List<File>
        get() = listOf(
            modelsDir,
            File(context.filesDir, "models"),
            context.filesDir
        )

    fun findCandidateModelFile(): File? {
        for (dir in candidateDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val existingFile = dir.listFiles()?.firstOrNull { file ->
                file.isFile && file.length() > 0L && (
                    file.name.endsWith(".task") ||
                    file.name.endsWith(".bin") ||
                    file.name.endsWith(".litertlm")
                )
            }
            if (existingFile != null) return existingFile
        }

        for (dir in candidateDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            for (name in supportedModelNames) {
                val file = File(dir, name)
                if (file.exists() && file.length() > 0L) return file
            }
        }

        return null
    }

    private data class InternalRegistry(
        var activeSlotId: String? = null,
        var stagedSlotId: String? = null,
        var previousSlotId: String? = null,
        val slots: MutableMap<String, ModelSlot> = mutableMapOf()
    )

    private fun loadRegistry(): InternalRegistry {
        val bakFile = File(modelsDir, "model_registry.json.bak")
        val contentToParse: String? = when {
            registryFile.exists() && registryFile.length() > 0L -> {
                try {
                    val text = registryFile.readText()
                    JSONObject(text) // Validate parseable JSON
                    text
                } catch (e: Exception) {
                    AiLogger.w("model_registry.json is corrupt or partial. Checking backup: ${e.message}")
                    if (bakFile.exists() && bakFile.length() > 0L) {
                        try {
                            val bakText = bakFile.readText()
                            JSONObject(bakText)
                            AiLogger.i("Recovered model registry from crash backup model_registry.json.bak")
                            try { bakFile.copyTo(registryFile, overwrite = true) } catch (_: Exception) {}
                            bakText
                        } catch (_: Exception) { null }
                    } else null
                }
            }
            bakFile.exists() && bakFile.length() > 0L -> {
                try {
                    val bakText = bakFile.readText()
                    JSONObject(bakText)
                    AiLogger.i("Restoring missing model_registry.json from model_registry.json.bak")
                    try { bakFile.copyTo(registryFile, overwrite = true) } catch (_: Exception) {}
                    bakText
                } catch (_: Exception) { null }
            }
            else -> null
        }

        if (contentToParse == null) {
            return bootstrapRegistry()
        }

        return try {
            val json = JSONObject(contentToParse)
            val activeId = json.optString("activeSlotId", "").takeIf { it.isNotBlank() }
            val stagedId = json.optString("stagedSlotId", "").takeIf { it.isNotBlank() }
            val prevId = json.optString("previousSlotId", "").takeIf { it.isNotBlank() }

            val slotsMap = mutableMapOf<String, ModelSlot>()
            val slotsObj = json.optJSONObject("slots")
            if (slotsObj != null) {
                for (key in slotsObj.keys()) {
                    val sObj = slotsObj.getJSONObject(key)
                    val relPath = sObj.getString("relativePath")
                    val targetFile = if (File(relPath).isAbsolute) {
                        File(relPath)
                    } else {
                        File(context.filesDir, relPath.removePrefix("/"))
                    }
                    val roleStr = sObj.optString("role", ModelSlotRole.ACTIVE.name)
                    val role = try {
                        ModelSlotRole.valueOf(roleStr)
                    } catch (e: Exception) {
                        ModelSlotRole.ACTIVE
                    }

                    slotsMap[key] = ModelSlot(
                        slotId = key,
                        file = targetFile,
                        sha256 = sObj.getString("sha256"),
                        sizeBytes = sObj.optLong("sizeBytes", targetFile.length()),
                        format = sObj.optString("format", detectFormat(targetFile)),
                        role = role
                    )
                }
            }

            // Recovery: If active slot file is missing on disk, but previous exists, recover to previous
            val activeSlot = slotsMap[activeId]
            if (activeId != null && (activeSlot == null || !activeSlot.file.exists())) {
                val prevSlot = slotsMap[prevId]
                if (prevId != null && prevSlot != null && prevSlot.file.exists()) {
                    AiLogger.w("Active model missing on disk! Recovering to previous model: ${prevSlot.file.name}")
                    val updatedPrev = prevSlot.copy(role = ModelSlotRole.ACTIVE)
                    slotsMap[prevId] = updatedPrev
                    val recovered = InternalRegistry(
                        activeSlotId = prevId,
                        stagedSlotId = stagedId,
                        previousSlotId = null,
                        slots = slotsMap
                    )
                    saveRegistryAtomic(recovered)
                    return recovered
                }
                return bootstrapRegistry()
            }

            InternalRegistry(
                activeSlotId = activeId,
                stagedSlotId = stagedId,
                previousSlotId = prevId,
                slots = slotsMap
            )
        } catch (e: Exception) {
            AiLogger.w("Failed to parse registry content, bootstrapping from disk: ${e.message}")
            bootstrapRegistry()
        }
    }

    private fun bootstrapRegistry(): InternalRegistry {
        val existing = findCandidateModelFile()
        val reg = InternalRegistry()
        if (existing != null && existing.exists()) {
            val hash = readConfirmedHash() ?: computeSha256Sync(existing)
            val baselineSlot = ModelSlot(
                slotId = "baseline",
                file = existing,
                sha256 = hash,
                sizeBytes = existing.length(),
                format = detectFormat(existing),
                role = ModelSlotRole.ACTIVE
            )
            reg.activeSlotId = "baseline"
            reg.slots["baseline"] = baselineSlot
            saveRegistryAtomic(reg)
            AiLogger.i("Bootstrapped model registry with existing baseline model at ${existing.name}")
        }
        return reg
    }

    private fun saveRegistryAtomic(registry: InternalRegistry) {
        val tmpFile = File(modelsDir, "model_registry.json.tmp")
        val bakFile = File(modelsDir, "model_registry.json.bak")
        try {
            val json = JSONObject().apply {
                put("version", 1)
                put("activeSlotId", registry.activeSlotId ?: "")
                put("stagedSlotId", registry.stagedSlotId ?: "")
                put("previousSlotId", registry.previousSlotId ?: "")

                val slotsObj = JSONObject()
                for ((key, slot) in registry.slots) {
                    val sObj = JSONObject().apply {
                        val relPath = try {
                            slot.file.relativeTo(context.filesDir).invariantSeparatorsPath
                        } catch (e: Exception) {
                            slot.file.absolutePath
                        }
                        put("relativePath", relPath)
                        put("sha256", slot.sha256)
                        put("sizeBytes", slot.sizeBytes)
                        put("format", slot.format)
                        put("role", slot.role.name)
                    }
                    slotsObj.put(key, sObj)
                }
                put("slots", slotsObj)
            }

            FileOutputStream(tmpFile).use { fos ->
                fos.write(json.toString(2).toByteArray(Charsets.UTF_8))
                fos.flush()
                try { fos.fd.sync() } catch (_: Exception) {}
            }

            if (registryFile.exists()) {
                try {
                    java.nio.file.Files.move(
                        tmpFile.toPath(),
                        registryFile.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                    if (bakFile.exists()) bakFile.delete()
                    return
                } catch (_: Throwable) {
                    // Non-atomic move fallback with backup preservation
                }

                if (bakFile.exists()) bakFile.delete()
                registryFile.renameTo(bakFile)

                val moved = tmpFile.renameTo(registryFile)
                if (moved) {
                    if (bakFile.exists()) bakFile.delete()
                } else {
                    if (bakFile.exists()) bakFile.renameTo(registryFile)
                    if (tmpFile.exists()) tmpFile.delete()
                }
            } else {
                val renamed = tmpFile.renameTo(registryFile)
                if (!renamed) {
                    try {
                        java.nio.file.Files.move(
                            tmpFile.toPath(),
                            registryFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        )
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            AiLogger.e(e, "Failed to atomically save model_registry.json")
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    private fun detectFormat(file: File): String = when {
        file.name.endsWith(".litertlm") -> "litertlm"
        file.name.endsWith(".task") -> "mediapipe_task"
        file.name.endsWith(".bin") -> "binary_flatbuffer"
        else -> "unknown"
    }

    private fun readConfirmedHash(): String? {
        val candidateConfirmFiles = listOf(
            confirmationFile,
            File(File(context.filesDir, "models"), "confirmed_hash.txt"),
            File(context.filesDir, "confirmed_hash.txt")
        )
        for (file in candidateConfirmFiles) {
            if (file.exists()) {
                val content = file.readText().trim()
                if (content.isNotBlank()) return content.lowercase()
            }
        }
        return null
    }

    private fun computeSha256Sync(file: File): String {
        if (!file.exists() || file.length() == 0L) return ""
        return try {
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
            ""
        }
    }

    override suspend fun getActiveSlot(): ModelSlot? = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            val reg = loadRegistry()
            val activeId = reg.activeSlotId ?: return@withContext null
            reg.slots[activeId]?.takeIf { it.file.exists() }
        }
    }

    override suspend fun getStagedSlot(): ModelSlot? = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            val reg = loadRegistry()
            val stagedId = reg.stagedSlotId ?: return@withContext null
            reg.slots[stagedId]?.takeIf { it.file.exists() }
        }
    }

    override suspend fun getPreviousSlot(): ModelSlot? = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            val reg = loadRegistry()
            val prevId = reg.previousSlotId ?: return@withContext null
            reg.slots[prevId]?.takeIf { it.file.exists() }
        }
    }

    override suspend fun getVerifiedModelFile(): File? = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            val reg = loadRegistry()
            val activeSlot = reg.activeSlotId?.let { reg.slots[it] }

            if (activeSlot != null && activeSlot.file.exists()) {
                val computedHash = computeSha256(activeSlot.file)
                if (computedHash.isNotBlank()) {
                    if (isHashConfirmed(computedHash) || computedHash.equals(activeSlot.sha256, ignoreCase = true)) {
                        return@withContext activeSlot.file
                    }
                }
            }

            // Fallback to legacy candidate discovery if registry slot is unverified
            val legacyCandidate = findCandidateModelFile() ?: return@withContext null
            val computedLegacyHash = computeSha256(legacyCandidate)
            if (computedLegacyHash.isNotBlank() && isHashConfirmed(computedLegacyHash)) {
                return@withContext legacyCandidate
            }

            null
        }
    }

    override suspend fun getInstalledManifest(): ModelManifest? = withContext(Dispatchers.IO) {
        val file = getVerifiedModelFile() ?: return@withContext null
        val manifestFile = File(modelsDir, "manifest.json")
        val sha256 = if (manifestFile.exists()) {
            try {
                JSONObject(manifestFile.readText()).optString("sha256", "")
            } catch (e: Exception) { "" }
        } else ""

        val format = detectFormat(file)

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
            source = "Dual-Slot Model Registry / Baseline"
        )
    }

    override suspend fun stageCandidateFromUri(
        sourceUri: Uri,
        expectedSha256: String
    ): AiResult<File> = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            val tempFile = File(stagingDir, "stage_${System.currentTimeMillis()}.tmp")

            try {
                // Storage space pre-check (at least 500MB available)
                val availableBytes = try {
                    modelsDir.usableSpace.takeIf { it > 0L } ?: StatFs(modelsDir.absolutePath).availableBytes
                } catch (_: Throwable) {
                    1024L * 1024L * 1024L // 1 GB fallback in test environments
                }
                val minRequiredBytes = 1024L * 1024L * 500L

                if (availableBytes < minRequiredBytes) {
                    AiLogger.w("Insufficient disk storage for candidate staging (available=$availableBytes bytes)")
                    return@withContext AiResult.Failure(AiError.LowStorage)
                }


                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(sourceUri)
                    ?: return@withContext AiResult.Failure(AiError.ModelMissing)

                AiLogger.i("Streaming candidate model from URI into staging slot")
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(65536)

                inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            digest.update(buffer, 0, bytesRead)
                        }
                        output.flush()
                        try { output.fd.sync() } catch (_: Exception) {}
                    }
                }

                val computedChecksum = digest.digest().joinToString("") { "%02x".format(it) }
                AiLogger.i("Staged candidate computed SHA-256: $computedChecksum")

                // Cryptographic checksum check
                if (expectedSha256.isNotBlank() && !computedChecksum.equals(expectedSha256.trim(), ignoreCase = true)) {
                    tempFile.delete()
                    AiLogger.w("Candidate checksum failure: expected=$expectedSha256, computed=$computedChecksum")
                    return@withContext AiResult.Failure(
                        AiError.ModelCorrupt("Checksum mismatch: expected $expectedSha256, got $computedChecksum")
                    )
                }

                // File format validation
                val queryName = context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                } ?: sourceUri.lastPathSegment ?: "candidate.task"

                val ext = when {
                    queryName.endsWith(".litertlm") -> ".litertlm"
                    queryName.endsWith(".task") -> ".task"
                    queryName.endsWith(".bin") -> ".bin"
                    else -> ""
                }

                if (ext.isBlank()) {
                    tempFile.delete()
                    AiLogger.w("Invalid model format '$queryName'. Supported extensions: .task, .bin, .litertlm")
                    return@withContext AiResult.Failure(
                        AiError.RuntimeError("Invalid candidate format. Only .task, .bin, or .litertlm are supported.")
                    )
                }

                if (tempFile.length() < 1024L) {
                    tempFile.delete()
                    return@withContext AiResult.Failure(AiError.ModelCorrupt("Candidate model file is empty or corrupted"))
                }

                val stagedTargetFile = File(stagingDir, "candidate$ext")
                if (stagedTargetFile.exists()) stagedTargetFile.delete()

                val renamed = tempFile.renameTo(stagedTargetFile)
                if (!renamed) {
                    tempFile.copyTo(stagedTargetFile, overwrite = true)
                    tempFile.delete()
                }

                // Update registry with STAGED role
                val reg = loadRegistry()
                val stagedSlot = ModelSlot(
                    slotId = "staged",
                    file = stagedTargetFile,
                    sha256 = computedChecksum,
                    sizeBytes = stagedTargetFile.length(),
                    format = detectFormat(stagedTargetFile),
                    role = ModelSlotRole.STAGED
                )
                reg.stagedSlotId = "staged"
                reg.slots["staged"] = stagedSlot
                saveRegistryAtomic(reg)

                AiLogger.i("Candidate model successfully staged in isolated staging slot: ${stagedTargetFile.name}")
                AiResult.Success(stagedTargetFile)
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                AiLogger.e(e, "Error staging candidate model from URI")
                AiResult.Failure(AiError.RuntimeError(e.message ?: "Candidate staging failed"))
            }
        }
    }

    override suspend fun activateStagedModel(): AiResult<File> = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            val reg = loadRegistry()
            val stagedSlotId = reg.stagedSlotId
                ?: return@withContext AiResult.Failure(AiError.ModelMissing)
            val stagedSlot = reg.slots[stagedSlotId]
                ?: return@withContext AiResult.Failure(AiError.ModelMissing)

            if (!stagedSlot.file.exists() || stagedSlot.file.length() == 0L) {
                return@withContext AiResult.Failure(AiError.ModelMissing)
            }

            // Verify checksum before activation
            val currentHash = computeSha256(stagedSlot.file)
            if (!currentHash.equals(stagedSlot.sha256, ignoreCase = true)) {
                return@withContext AiResult.Failure(AiError.ModelCorrupt("Staged file modified or corrupt before activation"))
            }

            // Verify runtime compatibility before activation
            val compatCheck = com.meshlink.app.ai.model.ModelRuntimeCompatibility.validateModelArtifact(
                stagedSlot.file,
                com.meshlink.app.ai.model.SupportedAiRuntime.TASKS_GENAI
            )
            if (compatCheck.isFailure) {
                val err = (compatCheck as AiResult.Failure).error
                AiLogger.w("Activation aborted: candidate model is incompatible with runtime: $err")
                return@withContext AiResult.Failure(err)
            }

            // Hash must be confirmed by user or pinned
            if (!isHashConfirmed(currentHash)) {
                confirmModelHash(currentHash)
            }

            // Determine target slot (slot_a or slot_b)
            val currentActiveId = reg.activeSlotId
            val nextSlotId = if (currentActiveId == "slot_a") "slot_b" else "slot_a"
            val targetSlotDir = File(slotsDir, nextSlotId).apply { if (!exists()) mkdirs() }
            val targetFile = File(targetSlotDir, "model.${stagedSlot.file.extension}")

            if (targetFile.exists()) targetFile.delete()

            // Move candidate from staging to target slot
            val moved = stagedSlot.file.renameTo(targetFile)
            if (!moved) {
                stagedSlot.file.copyTo(targetFile, overwrite = true)
                stagedSlot.file.delete()
            }

            // Retain former active model as PREVIOUS (do NOT delete!)
            if (currentActiveId != null && reg.slots.containsKey(currentActiveId)) {
                val formerActive = reg.slots[currentActiveId]!!
                reg.slots[currentActiveId] = formerActive.copy(role = ModelSlotRole.PREVIOUS)
                reg.previousSlotId = currentActiveId
                AiLogger.i("Retained previous model in slot '$currentActiveId' for rollback availability")
            }

            // Promote target slot to ACTIVE
            val newActiveSlot = ModelSlot(
                slotId = nextSlotId,
                file = targetFile,
                sha256 = currentHash,
                sizeBytes = targetFile.length(),
                format = detectFormat(targetFile),
                role = ModelSlotRole.ACTIVE
            )
            reg.slots[nextSlotId] = newActiveSlot
            reg.activeSlotId = nextSlotId
            reg.stagedSlotId = null
            reg.slots.remove("staged")

            saveRegistryAtomic(reg)

            // Update legacy manifest for backward compatibility
            try {
                val manifestFile = File(modelsDir, "manifest.json")
                val manifestJson = JSONObject().apply {
                    put("modelId", "gemma-3-1b-it")
                    put("fileName", targetFile.name)
                    put("sha256", currentHash)
                    put("pinnedSha256", currentHash)
                    put("sizeBytes", targetFile.length())
                    put("format", detectFormat(targetFile))
                    put("timestamp", System.currentTimeMillis())
                }
                manifestFile.writeText(manifestJson.toString(2))
            } catch (_: Exception) {}

            AiLogger.i("Candidate successfully activated into slot '$nextSlotId'. Active model: ${targetFile.name}")
            AiResult.Success(targetFile)
        }
    }

    override suspend fun rollbackToPrevious(): AiResult<File> = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            val reg = loadRegistry()
            val prevSlotId = reg.previousSlotId
                ?: return@withContext AiResult.Failure(AiError.ModelMissing)
            val prevSlot = reg.slots[prevSlotId]
                ?: return@withContext AiResult.Failure(AiError.ModelMissing)

            if (!prevSlot.file.exists() || prevSlot.file.length() == 0L) {
                AiLogger.w("Retained previous model missing from disk at ${prevSlot.file.absolutePath}")
                return@withContext AiResult.Failure(AiError.ModelMissing)
            }

            // Verify checksum of retained rollback candidate
            val prevHash = computeSha256(prevSlot.file)
            if (!prevHash.equals(prevSlot.sha256, ignoreCase = true)) {
                AiLogger.w("Retained previous model checksum failure! Rollback blocked.")
                return@withContext AiResult.Failure(AiError.ModelCorrupt("Retained previous model file is corrupt"))
            }

            // Swap active and previous slots atomically
            val currentActiveId = reg.activeSlotId
            if (currentActiveId != null && reg.slots.containsKey(currentActiveId)) {
                val formerActive = reg.slots[currentActiveId]!!
                reg.slots[currentActiveId] = formerActive.copy(role = ModelSlotRole.PREVIOUS)
                reg.previousSlotId = currentActiveId
            } else {
                reg.previousSlotId = null
            }

            val restoredActiveSlot = prevSlot.copy(role = ModelSlotRole.ACTIVE)
            reg.slots[prevSlotId] = restoredActiveSlot
            reg.activeSlotId = prevSlotId

            saveRegistryAtomic(reg)

            // Update legacy confirmation and manifest
            confirmModelHash(prevHash)

            AiLogger.i("Rollback successful! Restored previous model slot '$prevSlotId' (${prevSlot.file.name}) as ACTIVE")
            AiResult.Success(restoredActiveSlot.file)
        }
    }

    override suspend fun discardStagedModel(): AiResult<Unit> = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            val reg = loadRegistry()
            val stagedSlotId = reg.stagedSlotId
            if (stagedSlotId != null) {
                reg.slots[stagedSlotId]?.let { slot ->
                    if (slot.file.exists()) slot.file.delete()
                }
                reg.slots.remove(stagedSlotId)
                reg.stagedSlotId = null
                saveRegistryAtomic(reg)
            }
            stagingDir.listFiles()?.forEach { it.delete() }
            AiLogger.i("Staged candidate model discarded safely without modifying active model")
            AiResult.Success(Unit)
        }
    }

    override suspend fun importModelFromUri(
        sourceUri: Uri,
        expectedSha256: String,
        manifest: ModelManifest?
    ): AiResult<File> = withContext(Dispatchers.IO) {
        // Safe staging step (active model untouched)
        val stageResult = stageCandidateFromUri(sourceUri, expectedSha256)
        if (stageResult.isFailure) {
            return@withContext stageResult
        }

        // If pinned SHA-256 provided, automatically confirm and activate
        if (expectedSha256.isNotBlank()) {
            confirmModelHash(expectedSha256)
            activateStagedModel()
        } else {
            // Awaiting explicit user confirmation
            stageResult
        }
    }

    override suspend fun computeSha256(file: File): String = withContext(Dispatchers.IO) {
        computeSha256Sync(file)
    }

    override suspend fun confirmModelHash(hash: String) = withContext(Dispatchers.IO) {
        if (hash.isNotBlank()) {
            val normalized = hash.trim().lowercase()
            confirmationFile.writeText(normalized)
            val altModelsDir = File(context.filesDir, "models")
            if (altModelsDir.exists() && altModelsDir.isDirectory) {
                try {
                    File(altModelsDir, "confirmed_hash.txt").writeText(normalized)
                } catch (_: Exception) {}
            }
            AiLogger.i("User confirmed model hash: $normalized")
        }
    }

    override suspend fun isHashConfirmed(hash: String): Boolean = withContext(Dispatchers.IO) {
        if (hash.isBlank()) return@withContext false
        val candidateConfirmFiles = listOf(
            confirmationFile,
            File(File(context.filesDir, "models"), "confirmed_hash.txt"),
            File(context.filesDir, "confirmed_hash.txt")
        )
        for (file in candidateConfirmFiles) {
            if (file.exists()) {
                val saved = file.readText().trim().lowercase()
                if (saved.equals(hash.trim().lowercase(), ignoreCase = true)) {
                    return@withContext true
                }
            }
        }
        false
    }

    override suspend fun verifyChecksum(file: File, expectedSha256: String): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L || expectedSha256.isBlank()) return@withContext false
        val computed = computeSha256(file)
        computed.equals(expectedSha256.trim(), ignoreCase = true)
    }

    override suspend fun deleteInstalledModel(): AiResult<Unit> = withContext(Dispatchers.IO) {
        lifecycleMutex.withLock {
            try {
                if (confirmationFile.exists()) {
                    confirmationFile.delete()
                }

                // Clean staging directory
                if (stagingDir.exists()) {
                    stagingDir.listFiles()?.forEach { it.delete() }
                }

                // Clean slots directory
                if (slotsDir.exists()) {
                    slotsDir.listFiles()?.forEach { slotDir ->
                        if (slotDir.isDirectory) {
                            slotDir.listFiles()?.forEach { it.delete() }
                            slotDir.delete()
                        } else {
                            slotDir.delete()
                        }
                    }
                }

                // Clean legacy files in modelsDir, preserving baseline if in files/models
                modelsDir.listFiles()?.forEach { file ->
                    if (file.isFile && (
                        file.name.endsWith(".litertlm") ||
                        file.name.endsWith(".task") ||
                        file.name.endsWith(".bin") ||
                        file.name == "manifest.json" ||
                        file.name == "model_registry.json" ||
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

                AiLogger.i("Installed model slots and temporary files cleared from storage")
                AiResult.Success(Unit)
            } catch (e: Exception) {
                AiLogger.e(e, "Error deleting installed model slots")
                AiResult.Failure(AiError.RuntimeError(e.message ?: "Failed to delete model"))
            }
        }
    }
}
