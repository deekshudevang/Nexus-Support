package com.meshlink.app.ai.domain

import com.meshlink.app.ai.model.AiResult

/**
 * Service computing local vector embeddings and cosine similarity.
 */
interface LocalEmbeddingService {
    suspend fun embedText(text: String): AiResult<FloatArray>
    fun calculateCosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float
}
