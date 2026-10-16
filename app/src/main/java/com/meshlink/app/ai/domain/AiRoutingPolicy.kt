package com.meshlink.app.ai.domain

import com.meshlink.app.ai.ChatMessage
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class AiRoute {
    GENERAL_CONVERSATION,
    EMERGENCY_GUIDANCE,
    MIXED
}

data class RoutingDecision(
    val route: AiRoute,
    val isSafetyCritical: Boolean = false,
    val matchedEmergencyKeywords: Set<String> = emptySet(),
    val isFollowUp: Boolean = false
)

/**
 * Deterministic intent router for Offline AI.
 * Distinguishes general-purpose conversation from grounded emergency response
 * without rejecting ordinary programming, science, educational, or casual queries.
 */
@Singleton
class AiRoutingPolicy @Inject constructor() {

    // 1. Core Emergency, Disaster, and First-Aid Vocabulary
    private val emergencyKeywords = setOf(
        "cpr", "resuscitation", "cardiac", "chest compression", "chest compressions", "heart attack", "pulse",
        "bleeding", "blood", "wound", "laceration", "tourniquet", "hemorrhage", "artery", "stab",
        "burn", "burns", "scald", "blister", "chemical burn", "thermal burn",
        "choking", "heimlich", "airway", "obstruction", "abdominal thrust",
        "fracture", "broken bone", "splint", "sprain", "dislocation", "immobilize",
        "hypothermia", "frostbite", "cold exposure", "shivering",
        "heat stroke", "heat exhaustion", "dehydration",
        "poison", "poisoning", "poisoned", "bleach", "corrosive", "venom", "snake bite", "spider bite", "antidote",
        "shock", "unconscious", "fainting",
        "earthquake", "seismic", "aftershock", "drop cover hold", "tremor", "tsunami",
        "flood", "flooding", "flash flood", "rising water", "drown",
        "fire", "smoke inhalation", "flames", "wildfire", "stop drop and roll", "stop drop roll",
        "gas leak", "smell gas", "carbon monoxide",
        "water safe", "water purification", "purify water", "boil water",
        "evacuation", "evacuate", "shelter in place", "disaster preparedness", "emergency kit", "first aid kit",
        "first aid", "emergency protocol", "how to treat", "treat a burn", "treat bleeding", "treat fracture",
        "severe bleeding", "serious bleeding", "medical emergency"
    )

    // 2. Explicit Programming, Technical, and Computer Science Vocabulary
    private val technicalKeywords = setOf(
        "python", "kotlin", "java", "c++", "c#", "rust", "javascript", "typescript", "html", "css",
        "sql", "git", "bash", "linux", "code", "coding", "algorithm", "binary search", "sorting",
        "data structure", "tree", "linked list", "array", "function", "method", "class", "oop",
        "object-oriented", "polymorphism", "inheritance", "encapsulation", "normalization", "database",
        "relational", "compiler", "debug", "debugging", "bug", "syntax", "api", "rest", "endpoint",
        "programming", "software", "variable", "recursion", "stack", "queue", "heap"
    )

    // 3. Conversational Follow-up Markers
    private val followUpMarkers = listOf(
        "explain that", "explain previous", "previous answer", "simpler words", "simple terms",
        "in simpler", "now write", "can you write", "give an example", "what about", "tell me more",
        "continue", "step 2", "second step", "next step", "what next", "why is that", "explain why"
    )

    /**
     * Classifies a query into GENERAL_CONVERSATION, EMERGENCY_GUIDANCE, or MIXED.
     */
    fun route(query: String, history: List<ChatMessage> = emptyList()): RoutingDecision {
        val normalized = query.lowercase(Locale.ROOT).trim()

        // Check if query is a follow-up referring to previous turns
        val isFollowUp = followUpMarkers.any { normalized.contains(it) }
        val lastAssistantMessage = history.lastOrNull { !it.isUser && !it.isWarning && !it.text.startsWith("Welcome to Nexus") }
        val lastUserMessage = history.takeLast(3).firstOrNull { it.isUser }

        // Detect emergency keyword matches
        val matchedEmergency = emergencyKeywords.filter { kw ->
            if (kw.contains(" ")) {
                normalized.contains(kw)
            } else {
                normalized.split(Regex("[^a-zA-Z0-9]+")).any { it == kw || it == "${kw}s" || it == "${kw}ing" || it == "${kw}ed" }
            }
        }.toSet()

        // Detect technical keywords
        val matchedTechnical = technicalKeywords.filter { kw ->
            if (kw.contains(" ")) {
                normalized.contains(kw)
            } else {
                normalized.split(Regex("[^a-zA-Z0-9]+")).any { it == kw }
            }
        }.toSet()

        // Contextual follow-up routing:
        // If query is an explicit follow-up and does not introduce new emergency conditions,
        // inherit the conversational domain of the preceding dialogue.
        if (isFollowUp && matchedEmergency.isEmpty() && lastAssistantMessage != null) {
            val wasPreviousEmergency = lastAssistantMessage.citations.isNotEmpty() ||
                lastAssistantMessage.text.contains("Emergency Notice", ignoreCase = true)
            return if (wasPreviousEmergency) {
                RoutingDecision(
                    route = AiRoute.EMERGENCY_GUIDANCE,
                    isSafetyCritical = true,
                    isFollowUp = true
                )
            } else {
                RoutingDecision(
                    route = AiRoute.GENERAL_CONVERSATION,
                    isSafetyCritical = false,
                    isFollowUp = true
                )
            }
        }

        // Programming / Computer Science precedence:
        // If user asks for code, programming, algorithms, or technical concepts (e.g. "flood fill in Python", "burn down chart in Agile"),
        // route to GENERAL_CONVERSATION unless explicitly asking for bodily medical first aid or life-safety survival steps.
        if (matchedTechnical.isNotEmpty() && !normalized.contains("first aid") && !normalized.contains("how to treat")) {
            return RoutingDecision(
                route = AiRoute.GENERAL_CONVERSATION,
                isSafetyCritical = false,
                matchedEmergencyKeywords = emptySet()
            )
        }

        // Direct First Aid / Emergency Protocol requests take strict precedence for emergency verification
        val isDirectEmergencyRequest = normalized.contains("first aid") ||
            normalized.contains("emergency protocol") ||
            normalized.contains("how to treat") ||
            normalized.contains("how do i treat") ||
            normalized.contains("what should i do")

        if (isDirectEmergencyRequest && matchedEmergency.isNotEmpty()) {
            return RoutingDecision(
                route = AiRoute.EMERGENCY_GUIDANCE,
                isSafetyCritical = true,
                matchedEmergencyKeywords = matchedEmergency
            )
        }

        // Mixed intent detection:
        // Question combines a general non-emergency inquiry (science/definition)
        // AND an actionable emergency preparedness request.
        val hasGeneralIntent = normalized.contains("explain the science") ||
            normalized.contains("history of") ||
            normalized.contains("difference between")
        val hasActionableEmergency = normalized.contains("how to prepare") || normalized.contains("steps for")

        if (hasGeneralIntent && hasActionableEmergency && matchedEmergency.isNotEmpty()) {
            return RoutingDecision(
                route = AiRoute.MIXED,
                isSafetyCritical = true,
                matchedEmergencyKeywords = matchedEmergency
            )
        }

        // Emergency route detection
        if (matchedEmergency.isNotEmpty()) {
            return RoutingDecision(
                route = AiRoute.EMERGENCY_GUIDANCE,
                isSafetyCritical = true,
                matchedEmergencyKeywords = matchedEmergency
            )
        }

        // Default to General Conversation
        return RoutingDecision(
            route = AiRoute.GENERAL_CONVERSATION,
            isSafetyCritical = false,
            matchedEmergencyKeywords = emptySet()
        )
    }
}
