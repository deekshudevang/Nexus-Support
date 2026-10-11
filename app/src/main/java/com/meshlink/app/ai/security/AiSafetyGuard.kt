package com.meshlink.app.ai.security

/**
 * Defense-in-depth safety filter for Offline AI.
 * Distinguishes adversarial system overrides and dangerous harms from harmless
 * programming, scientific, educational, and conversational requests.
 */
object AiSafetyGuard {

    // 1. Adversarial Prompt Injection and Override Directives:
    // Targeting commands that explicitly attempt to hijack instruction hierarchy,
    // evade safety rules, or break context boundaries.
    private val injectionPatterns = listOf(
        Regex("""(?i)\bignore\s+(?:(?:all|previous|the|medical)\s+)*(?:context|instructions?|rules?|prompts?|guidelines?|system\s+directives?)\b"""),
        Regex("""(?i)\bdisregard\s+(?:(?:all|previous|the|medical)\s+)*(?:context|instructions?|rules?|prompts?|guidelines?)\b"""),
        Regex("""(?i)\bgenerate\s+fake\b"""),
        Regex("""(?i)\bsystem\s+(?:command\s+)?override\b"""),
        Regex("""(?i)\bsystem\s+override\s*:"""),
        Regex("""(?i)<context\s+id=.*sys.*>"""),
        Regex("""(?i)\bsay\s+pwned\b"""),
        Regex("""(?i)\bgrant\s+admin(?:\s+access)?\b""")
    )

    // 2. Prohibited Dangerous and Harmful Assistance:
    // Direct requests to construct lethal weapons, explosive devices, or synthesize deadly poisons.
    private val harmfulPatterns = listOf(
        Regex("""(?i)\b(?:how\s+to\s+make|build|synthesize|create)\s+(?:a\s+)?(?:bomb|explosive|dirty\s+bomb|pipe\s+bomb|weapon\s+of\s+mass\s+destruction)\b"""),
        Regex("""(?i)\b(?:lethal\s+poisoning\s+instructions|how\s+to\s+poison\s+someone)\b"""),
        Regex("""(?i)\b(?:create|write)\s+(?:ransomware|keylogger|malware\s+payload)\b""")
    )

    const val INJECTION_REFUSAL_MESSAGE =
        "I cannot process system prompt overrides or adversarial directives. How else may I assist you today?"

    const val HARMFUL_CONTENT_REFUSAL =
        "I cannot assist with dangerous, harmful, or hazardous activities. For safety and emergency preparedness, please consult verified community resources."

    /**
     * Returns true if query contains an adversarial prompt injection or override attempt.
     */
    fun isPromptInjection(text: String): Boolean {
        return injectionPatterns.any { it.containsMatchIn(text) }
    }

    /**
     * Returns true if query explicitly asks for hazardous, weaponized, or lethal activities.
     */
    fun isHarmfulRequest(text: String): Boolean {
        return harmfulPatterns.any { it.containsMatchIn(text) }
    }

    /**
     * Returns true if the query is safe for normal offline processing.
     */
    fun isSafe(text: String): Boolean {
        return !isPromptInjection(text) && !isHarmfulRequest(text)
    }
}
