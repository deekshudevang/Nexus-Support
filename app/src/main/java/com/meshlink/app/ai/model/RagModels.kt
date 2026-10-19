package com.meshlink.app.ai.model

/**
 * Verified source citation for grounded emergency RAG answers.
 */
data class EmergencySourceCitation(
    val guideId: String,
    val title: String,
    val section: String,
    val revisionOrDate: String,
    val relevanceScore: Float
)

/**
 * Text chunk with source attribution for RAG indexing and retrieval.
 */
data class GroundedChunk(
    val chunkId: String,
    val sourceId: String,
    val sourceTitle: String,
    val text: String,
    val section: String = "",
    val embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GroundedChunk
        return chunkId == other.chunkId
    }

    override fun hashCode(): Int = chunkId.hashCode()
}

/**
 * Full RAG response including generated text, citations, and abstention status.
 */
data class RagResponse(
    val answer: String,
    val citations: List<EmergencySourceCitation>,
    val hasSufficientEvidence: Boolean,
    val latencyMs: Long
)
