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
    // or execute severe medical malpractice.
    private val harmfulPatterns = listOf(
        Regex("""(?i)\b(?:how\s+to\s+make|build|synthesize|create)\s+(?:a\s+)?(?:bomb|explosive|dirty\s+bomb|pipe\s+bomb|weapon\s+of\s+mass\s+destruction)\b"""),
        Regex("""(?i)\b(?:lethal\s+poisoning\s+instructions|how\s+to\s+poison\s+someone)\b"""),
        Regex("""(?i)\b(?:synthesize|produce|make)\s+(?:ricin|sarin|cyanide|nerve\s+agent|mustard\s+gas)\b"""),
        Regex("""(?i)\b(?:perform\s+surgery\s+at\s+home|kitchen\s+knife\s+surgery|amputate\s+(?:limb|leg|arm)\s+at\s+home)\b"""),
        Regex("""(?i)\b(?:inject|drink|consume)\s+(?:bleach|disinfectant|ammonia|drain\s+cleaner)\b"""),
        Regex("""(?i)\b(?:how\s+to\s+overdose|lethal\s+dose\s+of)\b""")
    )

    // 3. Prohibited Offensive Cyberattacks, Exploitation, and Hacking Assistance:
    // Targets requests to attack or hack systems, exploit SQL/databases, generate exploit payloads,
    // crack credentials, or produce malware.
    private val cyberAttackPatterns = listOf(
        Regex("""(?i)\b(?:how\s+to\s+)?(?:hack|exploit|penetrate|breach)\s+(?:into\s+)?(?:a\s+)?(?:sql|databases?|db|websites?|servers?|networks?|wifi|routers?|passwords?|accounts?|systems?)\b"""),
        Regex("""(?i)\b(?:write|create|generate|provide|run)\s+(?:a\s+)?(?:python\s+|bash\s+)?(?:code|script|payload|tool)?\s*(?:to|for)?\s*(?:hack|exploit|attack|crack)\s+(?:a\s+)?(?:sql|databases?|db|websites?|servers?|networks?|passwords?)\b"""),
        Regex("""(?i)\b(?:sql\s+injection|sqli)\s+(?:exploit|attack|payload|script|tool)\b"""),
        Regex("""(?i)\b(?:create|write|generate|provide)\s+(?:a\s+)?(?:ransomware|keylogger|malware\s+payload|ddos\s+bot|trojan|zero-day\s+exploit|reverse\s+shell|rootkit|credential\s+harvester|password\s+cracker|brute\s+force\s+tool)\b"""),
        Regex("""(?i)\b(?:how\s+to\s+exploit)\s+(?:a\s+)?(?:sql|database|vulnerability|endpoint|cve)\b""")
    )

    // Defensive and remediation keywords that should NOT be blocked (dual-use defense)
    private val defensiveIndicators = listOf(
        Regex("""(?i)\b(?:prevent|preventing|prevention|protect(?:ion)?(?:\s+against)?|defend(?:\s+against)?|defense|mitigate|mitigating|mitigation|fix(?:ing)?|stop(?:ping)?|remediat(?:e|ion)|secur(?:e|ing)|sanitize|sanitization|parameteriz(?:ed|ation)|prepared\s+statements?)\b""")
    )

    const val INJECTION_REFUSAL_MESSAGE =
        "I cannot process system prompt overrides or adversarial directives. How else may I assist you today?"

    const val HARMFUL_CONTENT_REFUSAL =
        "I cannot assist with dangerous, harmful, or hazardous activities. For safety and emergency preparedness, please consult verified community resources."

    const val CYBER_ATTACK_REFUSAL =
        "I cannot assist with hacking, exploiting vulnerabilities, or generating offensive attack code. I can, however, explain defensive security best practices such as parameterized queries, prepared statements, and input validation to secure SQL databases against injection attacks."

    // 4. Output hallucination checks:
    // Models must never hallucinate external dispatching of rescue teams, 911 calls, or confirmed real-world intervention
    private val emergencyDispatchHallucinationPatterns = listOf(
        Regex("""(?i)\b(?:i\s+have|i've)\s+(?:called|dispatched|notified|contacted)\s+(?:the\s+)?(?:911|emergency\s+services?|paramedics?|police|first\s+responders?|rescue\s+teams?)\b"""),
        Regex("""(?i)\b(?:help|rescue|paramedics?|first\s+responders?|emergency\s+services?|rescue\s+teams?)\s+(?:is|are)\s+(?:on\s+the\s+way|en\s+route|dispatched\s+to\s+your\s+location)\b"""),
        Regex("""(?i)\b(?:your\s+sos\s+(?:has\s+been|was|is)\s+(?:delivered|sent|transmitted|received\s+by\s+authorities|acknowledged\s+by\s+rescue))\b"""),
        Regex("""(?i)\b(?:your\s+sos\s+has\s+been\s+sent\s+to|transmitted\s+to)\s+(?:the\s+)?(?:911|local\s+authorities|the\s+coast\s+guard)\b""")
    )

    const val RESCUE_DISPATCH_DISCLAIMER =
        "\n\n[Safety Advisory: Nexus-Support offline AI operates entirely locally on-device and cannot dispatch emergency services or verify external rescue operations. Use the SOS beacon to broadcast emergency beacons to nearby mesh peers.]"

    /**
     * Inspects generated text and sanitizes or annotates hallucinated real-world rescue actions.
     */
    fun validateOutput(output: String): String {
        if (output.isBlank()) return output

        var validated = output
        if (emergencyDispatchHallucinationPatterns.any { it.containsMatchIn(validated) }) {
            validated += RESCUE_DISPATCH_DISCLAIMER
        }
        return validated
    }

    /**
     * Returns true if query contains an adversarial prompt injection or override attempt.
     */
    fun isPromptInjection(text: String): Boolean {
        return injectionPatterns.any { it.containsMatchIn(text) }
    }

    /**
     * Returns true if query asks for offensive cyberattacks, exploits, or malicious hacking payloads,
     * while allowing benign defensive and educational queries.
     */
    fun isCyberAttackRequest(text: String): Boolean {
        if (defensiveIndicators.any { it.containsMatchIn(text) }) {
            val overtExploitScript = Regex("""(?i)\b(?:write|generate|give\s+me)\s+(?:a\s+)?(?:exploit|attack|hack(?:ing)?)\s+(?:script|payload|code)\b""")
            if (!overtExploitScript.containsMatchIn(text)) {
                return false
            }
        }
        return cyberAttackPatterns.any { it.containsMatchIn(text) }
    }

    /**
     * Returns true if query explicitly asks for hazardous, weaponized, lethal, or cyberattack activities.
     */
    fun isHarmfulRequest(text: String): Boolean {
        return harmfulPatterns.any { it.containsMatchIn(text) } || isCyberAttackRequest(text)
    }

    /**
     * Returns true if the query is safe for normal offline processing.
     */
    fun isSafe(text: String): Boolean {
        return !isPromptInjection(text) && !isHarmfulRequest(text)
    }
}
