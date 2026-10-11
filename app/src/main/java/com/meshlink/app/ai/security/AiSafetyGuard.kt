package com.meshlink.app.ai.security

/**
 * Defense-in-depth safety filter for Offline AI.
 * Distinguishes adversarial system overrides and dangerous harms from harmless
 * programming, scientific, educational, and conversational requests.
 */
object AiSafetyGuard {

    // 1. Adversarial Prompt Injection, Role Hijacking, and Override Directives:
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
        Regex("""(?i)\bgrant\s+admin(?:\s+access)?\b"""),
        Regex("""(?i)\b(?:you\s+are\s+now|act\s+as|pretend\s+to\s+be)\s+(?:DAN|EvilGPT|developer\s+mode|unfiltered|jailbroken)\b"""),
        Regex("""(?i)\b(?:jailbreak|jailbroken\s+mode|do\s+anything\s+now)\b"""),
        Regex("""(?i)\b(?:print|reveal|show|display|leak)\s+(?:the\s+)?(?:system\s+prompt|system\s+instructions?|hidden\s+prompt)\b"""),
        Regex("""(?i)\bwhat\s+(?:are|were)\s+your\s+(?:exact\s+)?(?:system\s+instructions?|hidden\s+prompts?)\b"""),
        Regex("""(?i)\b(?:bypass|disable)\s+(?:safety|guardrails?|filters?|rules?)\b""")
    )

    // 2. Prohibited Dangerous and Harmful Assistance:
    // Direct requests to construct lethal weapons, explosive devices, synthesize deadly poisons,
    // execute severe medical malpractice, or build cyber weapons.
    private val harmfulPatterns = listOf(
        Regex("""(?i)\b(?:how\s+to\s+make|build|synthesize|create)\s+(?:a\s+)?(?:bomb|explosive|dirty\s+bomb|pipe\s+bomb|weapon\s+of\s+mass\s+destruction)\b"""),
        Regex("""(?i)\b(?:lethal\s+poisoning\s+instructions|how\s+to\s+poison\s+someone)\b"""),
        Regex("""(?i)\b(?:synthesize|produce|make)\s+(?:ricin|sarin|cyanide|nerve\s+agent|mustard\s+gas)\b"""),
        Regex("""(?i)\b(?:create|write|generate)\s+(?:a\s+)?(?:ransomware|keylogger|malware\s+payload|ddos\s+bot|trojan|zero-day\s+exploit)\b"""),
        Regex("""(?i)\b(?:perform\s+surgery\s+at\s+home|kitchen\s+knife\s+surgery|amputate\s+(?:limb|leg|arm)\s+at\s+home)\b"""),
        Regex("""(?i)\b(?:inject|drink|consume)\s+(?:bleach|disinfectant|ammonia|drain\s+cleaner)\b"""),
        Regex("""(?i)\b(?:how\s+to\s+overdose|lethal\s+dose\s+of)\b""")
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
