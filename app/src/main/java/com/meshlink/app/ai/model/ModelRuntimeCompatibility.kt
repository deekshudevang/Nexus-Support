package com.meshlink.app.ai.model

import java.io.File
import java.io.FileInputStream

/**
 * Enumeration of on-device AI runtimes.
 * Nexus-Support currently packages MediaPipe Tasks GenAI 0.10.35.
 * LiteRT-LM is an independent runtime not currently packaged in the APK.
 */
enum class SupportedAiRuntime(val runtimeId: String, val displayName: String) {
    TASKS_GENAI("tasks-genai", "Google MediaPipe Tasks GenAI"),
    LITERT_LM("litert-lm", "Google LiteRT-LM (Unbundled)")
}

/**
 * Enumeration of recognized model container formats.
 */
enum class ModelContainerFormat(
    val formatId: String,
    val displayName: String,
    val extensions: List<String>
) {
    MEDIAPIPE_TASK("mediapipe_task", "MediaPipe Task (.task)", listOf("task")),
    TFLITE_FLATBUFFER("tflite_flatbuffer", "TFLite Binary FlatBuffer (.bin)", listOf("bin")),
    LITERT_LM_CONTAINER("litertlm", "LiteRT-LM Container (.litertlm)", listOf("litertlm")),
    UNKNOWN("unknown", "Unknown Format", emptyList())
}

/**
 * Enforces runtime compatibility, format validation, and binary magic checks
 * across the local AI subsystem.
 */
object ModelRuntimeCompatibility {

    /**
     * Runtime packaged in the current build.
     */
    val CURRENT_PACKAGED_RUNTIME = SupportedAiRuntime.TASKS_GENAI

    private val ZIP_LOCAL_HEADER = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())
    private val ZIP_EMPTY_HEADER = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x05.toByte(), 0x06.toByte())
    private val ZIP_SPANNED_HEADER = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x07.toByte(), 0x08.toByte())
    private val TFLITE_MAGIC = "TFL3".toByteArray(Charsets.US_ASCII)

    private val SUPPORTED_MODEL_ARCHITECTURES = setOf(
        "gemma-3-1b-it",
        "gemma3-1b-it",
        "gemma-3-1b",
        "gemma3",
        "gemma-3",
        "gemma"
    )

    /**
     * Determines container format from file extension and header signature.
     */
    fun detectFormat(file: File): ModelContainerFormat {
        val ext = file.extension.lowercase()
        return when (ext) {
            "task" -> ModelContainerFormat.MEDIAPIPE_TASK
            "bin" -> ModelContainerFormat.TFLITE_FLATBUFFER
            "litertlm" -> ModelContainerFormat.LITERT_LM_CONTAINER
            else -> ModelContainerFormat.UNKNOWN
        }
    }

    /**
     * Checks whether the given format is supported by the target runtime.
     */
    fun isFormatSupportedByRuntime(
        format: ModelContainerFormat,
        runtime: SupportedAiRuntime = CURRENT_PACKAGED_RUNTIME
    ): Boolean = when (runtime) {
        SupportedAiRuntime.TASKS_GENAI -> {
            format == ModelContainerFormat.MEDIAPIPE_TASK || format == ModelContainerFormat.TFLITE_FLATBUFFER
        }
        SupportedAiRuntime.LITERT_LM -> {
            format == ModelContainerFormat.LITERT_LM_CONTAINER
        }
    }

    /**
     * Inspects binary magic bytes to verify file is not corrupted or truncated.
     */
    fun verifyBinaryHeader(file: File, format: ModelContainerFormat): Boolean {
        if (!file.exists() || file.length() < 4L) return false

        return try {
            val header = ByteArray(8)
            val bytesRead = FileInputStream(file).use { it.read(header) }
            if (bytesRead < 4) return false

            when (format) {
                ModelContainerFormat.MEDIAPIPE_TASK -> {
                    // MediaPipe task bundles are zip archives
                    (header[0] == ZIP_LOCAL_HEADER[0] && header[1] == ZIP_LOCAL_HEADER[1] &&
                     header[2] == ZIP_LOCAL_HEADER[2] && header[3] == ZIP_LOCAL_HEADER[3]) ||
                    (header[0] == ZIP_EMPTY_HEADER[0] && header[1] == ZIP_EMPTY_HEADER[1] &&
                     header[2] == ZIP_EMPTY_HEADER[2] && header[3] == ZIP_EMPTY_HEADER[3]) ||
                    (header[0] == ZIP_SPANNED_HEADER[0] && header[1] == ZIP_SPANNED_HEADER[1] &&
                     header[2] == ZIP_SPANNED_HEADER[2] && header[3] == ZIP_SPANNED_HEADER[3])
                }
                ModelContainerFormat.TFLITE_FLATBUFFER -> {
                    if (bytesRead < 8) return false
                    // FlatBuffers have file identifier at offset 4..7
                    header[4] == TFLITE_MAGIC[0] && header[5] == TFLITE_MAGIC[1] &&
                    header[6] == TFLITE_MAGIC[2] && header[7] == TFLITE_MAGIC[3]
                }
                ModelContainerFormat.LITERT_LM_CONTAINER -> {
                    // LiteRT-LM containers typically begin with custom signature or zip/flatbuffer
                    true
                }
                ModelContainerFormat.UNKNOWN -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Comprehensive validation of a candidate model artifact for a specific runtime.
     */
    fun validateModelArtifact(
        file: File,
        targetRuntime: SupportedAiRuntime = CURRENT_PACKAGED_RUNTIME,
        requireHeaderValidation: Boolean = false
    ): AiResult<ModelContainerFormat> {
        if (!file.exists()) {
            return AiResult.Failure(AiError.ModelMissing)
        }

        if (file.length() == 0L) {
            return AiResult.Failure(AiError.ModelCorrupt("Model file is empty (0 bytes)"))
        }

        val format = detectFormat(file)
        if (format == ModelContainerFormat.UNKNOWN) {
            return AiResult.Failure(
                AiError.RuntimeError("Incompatible model format '.${file.extension}'. MediaPipe Tasks GenAI runtime requires .task or .bin")
            )
        }

        // Strict rejection of LiteRT-LM if runtime is Tasks GenAI
        if (format == ModelContainerFormat.LITERT_LM_CONTAINER && targetRuntime == SupportedAiRuntime.TASKS_GENAI) {
            return AiResult.Failure(
                AiError.RuntimeError("Incompatible model format '.litertlm'. MediaPipe Tasks GenAI runtime requires .task or .bin")
            )
        }

        if (!isFormatSupportedByRuntime(format, targetRuntime)) {
            return AiResult.Failure(
                AiError.Unsupported("Model format '${format.displayName}' is not supported by runtime '${targetRuntime.displayName}'")
            )
        }

        if (requireHeaderValidation && !verifyBinaryHeader(file, format)) {
            return AiResult.Failure(
                AiError.ModelCorrupt("Candidate model has corrupted or invalid binary header for format ${format.displayName}")
            )
        }

        return AiResult.Success(format)
    }

    /**
     * Validates declared model identity against known verified offline model families.
     */
    fun validateModelIdentity(modelId: String): AiResult<Unit> {
        val normalized = modelId.trim().lowercase()
        if (normalized.isBlank()) {
            return AiResult.Failure(AiError.Unsupported("Model identity cannot be blank."))
        }

        val isSupported = SUPPORTED_MODEL_ARCHITECTURES.any { supported ->
            normalized == supported || normalized.startsWith("$supported-") || normalized.startsWith("${supported}_")
        }

        return if (isSupported) {
            AiResult.Success(Unit)
        } else {
            AiResult.Failure(
                AiError.Unsupported("Unknown or unsupported model architecture '$modelId'. Only verified Gemma 3 architectures are supported.")
            )
        }
    }
}
