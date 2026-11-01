package com.meshlink.app.ai.model

/**
 * Strict error taxonomy for Offline AI operations with user-facing recovery guidance.
 */
sealed class AiError(val userRecoveryText: String) {
    data object ModelMissing : AiError(
        "Local AI model is not installed. Please import a compatible model in Settings."
    )

    data class ModelCorrupt(val details: String = "") : AiError(
        "Model integrity verification failed (checksum mismatch or corrupt header). Please re-import the model."
    )

    data class Unsupported(val reason: String = "") : AiError(
        "Offline AI is unsupported on this hardware or device configuration."
    )

    data object LowMemory : AiError(
        "Device is low on memory. AI processing was suspended to preserve core SOS communications."
    )

    data object LowStorage : AiError(
        "Insufficient device storage to unpack or run the AI model."
    )

    data class Timeout(val timeoutMs: Long) : AiError(
        "AI inference timed out after ${timeoutMs / 1000} seconds. Please try a simpler request."
    )

    data object Cancelled : AiError(
        "AI processing was cancelled."
    )

    data class RuntimeError(val message: String) : AiError(
        "AI engine encountered an internal error. Please try again."
    )

    data class InvalidInput(val reason: String) : AiError(
        "Input was invalid or exceeded maximum token budget: $reason"
    )

    data object SosPriorityPreempted : AiError(
        "AI processing was immediately paused to prioritize active emergency SOS communications."
    )

    data object DisabledByKillSwitch : AiError(
        "AI capabilities are currently disabled by the emergency kill switch."
    )
}
