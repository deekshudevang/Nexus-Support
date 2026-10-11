package com.meshlink.app.ai.rag

import com.meshlink.app.ai.domain.EmergencyRagService
import com.meshlink.app.ai.domain.LocalAiEngine
import com.meshlink.app.ai.domain.LocalEmbeddingService
import com.meshlink.app.ai.domain.OfflineAiCoordinator
import com.meshlink.app.ai.model.AiError
import com.meshlink.app.ai.model.AiResult
import com.meshlink.app.ai.model.EmergencySourceCitation
import com.meshlink.app.ai.model.RagResponse
import com.meshlink.app.ai.security.AiLogger
import com.meshlink.app.domain.model.EmergencyGuide
import com.meshlink.app.domain.repository.EmergencyGuideRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyRagServiceImpl @Inject constructor(
    private val guideRepository: EmergencyGuideRepository,
    private val embeddingService: LocalEmbeddingService,
    private val coordinator: OfflineAiCoordinator,
    private val localAiEngine: LocalAiEngine
) : EmergencyRagService {

    override suspend fun answerQuery(query: String): AiResult<RagResponse> = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        if (query.isBlank()) {
            return@withContext AiResult.Failure(AiError.InvalidInput("Query cannot be blank"))
        }

        // 1. Retrieve all guides from repository
        val allGuides = guideRepository.getAllGuides().first()
        if (allGuides.isEmpty()) {
            val latency = System.currentTimeMillis() - startTime
            return@withContext AiResult.Success(
                RagResponse(
                    answer = RagConstants.ABSTENTION_MESSAGE,
                    citations = emptyList(),
                    hasSufficientEvidence = false,
                    latencyMs = latency
                )
            )
        }

        // 2. Hybrid Keyword + Vector Retrieval Ranking
        val queryEmbedding = (embeddingService.embedText(query) as? AiResult.Success)?.data ?: FloatArray(0)
        val scoredGuides = rankGuides(query, queryEmbedding, allGuides)

        // 3. Abstention Check if top result falls below threshold
        val topGuides = scoredGuides.filter { it.score >= RagConstants.SIMILARITY_THRESHOLD }
            .take(RagConstants.MAX_RETRIEVED_DOCS)

        if (topGuides.isEmpty()) {
            AiLogger.i("Query did not match emergency corpus above threshold. Abstaining.")
            val latency = System.currentTimeMillis() - startTime
            return@withContext AiResult.Success(
                RagResponse(
                    answer = RagConstants.ABSTENTION_MESSAGE,
                    citations = emptyList(),
                    hasSufficientEvidence = false,
                    latencyMs = latency
                )
            )
        }

        // 4. Construct Bounded Grounded Prompt
        val contextPassages = topGuides.map { scored ->
            val guide = scored.guide
            "Guide: ${guide.title}\nWarnings: ${guide.warnings.joinToString("; ")}\nSteps: ${guide.steps.joinToString(" -> ")}"
        }

        val prompt = RagConstants.buildPrompt(query, contextPassages)
        val citations = topGuides.map { scored ->
            EmergencySourceCitation(
                guideId = scored.guide.id,
                title = scored.guide.title,
                section = "Emergency Steps & Warnings",
                revisionOrDate = "v1.0 (AOSP Bundled)",
                relevanceScore = scored.score
            )
        }

        // 5. Execute inference through coordinator (protected by kill switch & SOS preemption)
        val inferenceResult = coordinator.executeInference(timeoutMs = 25000L) {
            localAiEngine.generate(prompt)
        }

        val latency = System.currentTimeMillis() - startTime

        when (inferenceResult) {
            is AiResult.Success -> {
                val formattedAnswer = inferenceResult.data + RagConstants.MEDICAL_SAFETY_DISCLAIMER
                AiResult.Success(
                    RagResponse(
                        answer = formattedAnswer,
                        citations = citations,
                        hasSufficientEvidence = true,
                        latencyMs = latency
                    )
                )
            }
            is AiResult.Failure -> {
                // If model is missing or disabled, fall back to grounded verbatim steps from top guide
                if (inferenceResult.error is AiError.ModelMissing || inferenceResult.error is AiError.DisabledByKillSwitch) {
                    val fallbackAnswer = buildFallbackResponse(topGuides.first().guide)
                    AiResult.Success(
                        RagResponse(
                            answer = fallbackAnswer,
                            citations = citations,
                            hasSufficientEvidence = true,
                            latencyMs = latency
                        )
                    )
                } else {
                    AiResult.Failure(inferenceResult.error)
                }
            }
        }
    }

    private data class ScoredGuide(val guide: EmergencyGuide, val score: Float)

    private val stopWords = setOf(
        "the", "is", "at", "which", "on", "who", "what", "how", "do", "i", "of",
        "in", "and", "a", "an", "to", "for", "with", "as", "by", "from", "it", "my",
        "you", "should", "me", "there", "we", "can", "will", "are", "be"
    )

    private suspend fun rankGuides(
        query: String,
        queryEmbedding: FloatArray,
        guides: List<EmergencyGuide>
    ): List<ScoredGuide> {
        val queryLower = query.lowercase()
        val queryTokens = queryLower.split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.isNotBlank() && it !in stopWords }
            .toSet()

        if (queryTokens.isEmpty()) {
            return emptyList()
        }

        return guides.map { guide ->
            val titleTokens = guide.title.lowercase().split(Regex("[^a-zA-Z0-9]+"))
                .filter { it.isNotBlank() && it !in stopWords }
                .toSet()
            val guideKeywords = guide.keywords.map { it.lowercase() }.toSet()

            // Keyword match component
            val keywordMatches = guideKeywords.count { kw ->
                kw in queryTokens || queryTokens.any { token -> token.contains(kw) || kw.contains(token) }
            }
            val titleMatches = titleTokens.count { it in queryTokens }
            val keywordScore = (keywordMatches * 0.35f + titleMatches * 0.40f).coerceAtMost(1.0f)

            if (keywordScore == 0.0f) {
                // Zero keyword/title intersection: reject false positives
                return@map ScoredGuide(guide, 0.0f)
            }

            // Vector similarity component
            val guideText = "${guide.title} ${guide.keywords.joinToString(" ")}"
            val guideEmbedding = (embeddingService.embedText(guideText) as? AiResult.Success)?.data ?: FloatArray(0)
            val vectorScore = if (queryEmbedding.isNotEmpty() && guideEmbedding.isNotEmpty()) {
                embeddingService.calculateCosineSimilarity(queryEmbedding, guideEmbedding)
            } else 0.0f

            // Hybrid blended score (60% keyword/title, 40% vector)
            val blendedScore = (0.6f * keywordScore + 0.4f * vectorScore).coerceIn(0.0f, 1.0f)
            ScoredGuide(guide, blendedScore)
        }.sortedByDescending { it.score }
    }

    private fun buildFallbackResponse(guide: EmergencyGuide): String = buildString {
        append("Protocol: ").append(guide.title).append("\n\n")
        if (guide.warnings.isNotEmpty()) {
            append("WARNINGS:\n")
            guide.warnings.forEach { append("- ").append(it).append("\n") }
            append("\n")
        }
        append("STEPS:\n")
        guide.steps.forEachIndexed { i, step -> append("${i + 1}. ").append(step).append("\n") }
        append(RagConstants.MEDICAL_SAFETY_DISCLAIMER)
    }
}
