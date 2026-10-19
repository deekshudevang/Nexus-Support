package com.meshlink.app.ai.rag

import com.meshlink.app.ai.domain.LocalEmbeddingService
import com.meshlink.app.ai.model.AiResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Deterministic local feature-hashed vector service for keyword and subword retrieval.
 *
 * IMPORTANT / ARCHITECTURE NOTE:
 * This is NOT a neural semantic embedding model (such as BERT, Gecko, or sentence-transformers).
 * It computes a 128-dimensional L2-normalized bag-of-words and character tri-gram frequency vector
 * using polynomial hashing (`String.hashCode()`). It provides fast, zero-dependency subword and
 * keyword retrieval for offline emergency guides without requiring a multi-megabyte neural embedding model file.
 * True semantic relationships (e.g. synonym matching not covered by guide keywords) are handled
 * downstream by the Local LLM inference stage with retrieved context.
 */
@Singleton
class LocalEmbeddingServiceImpl @Inject constructor() : LocalEmbeddingService {

    companion object {
        const val VECTOR_DIMENSIONS = 128
    }

    override suspend fun embedText(text: String): AiResult<FloatArray> {
        val vector = FloatArray(VECTOR_DIMENSIONS)
        if (text.isBlank()) {
            return AiResult.Success(vector)
        }

        val tokens = text.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) {
            return AiResult.Success(vector)
        }

        // Hashed token & bi-gram feature mapping
        tokens.forEach { token ->
            val hash = (token.hashCode() and 0x7FFFFFFF) % VECTOR_DIMENSIONS
            vector[hash] += 1.0f
        }

        // Add character tri-grams for subword matching
        tokens.forEach { token ->
            if (token.length >= 3) {
                for (i in 0..token.length - 3) {
                    val tri = token.substring(i, i + 3)
                    val hash = (tri.hashCode() and 0x7FFFFFFF) % VECTOR_DIMENSIONS
                    vector[hash] += 0.5f
                }
            }
        }

        // L2 Unit Normalization
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares)
        if (norm > 0f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return AiResult.Success(vector)
    }

    override fun calculateCosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size || vec1.isEmpty()) return 0.0f
        var dot = 0.0f
        for (i in vec1.indices) {
            dot += vec1[i] * vec2[i]
        }
        return dot.coerceIn(0.0f, 1.0f)
    }
}
