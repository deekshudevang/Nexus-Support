package com.meshlink.app.ai.domain

import com.meshlink.app.ai.model.AiResult
import com.meshlink.app.ai.model.RagResponse

/**
 * Service providing grounded answers from the local emergency knowledge base
 * with explicit source citations and conservative abstention.
 */
interface EmergencyRagService {
    /**
     * Answers a user emergency query using local RAG.
     * Retained source citations are appended to the response.
     */
    suspend fun answerQuery(query: String): AiResult<RagResponse>
}
