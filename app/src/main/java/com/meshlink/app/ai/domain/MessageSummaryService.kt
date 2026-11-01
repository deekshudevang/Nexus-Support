package com.meshlink.app.ai.domain

import com.meshlink.app.ai.model.AiResult
import com.meshlink.app.domain.model.Message

/**
 * Service for generating offline summaries of mesh conversations and incident reports.
 * Original messages are never altered.
 */
interface MessageSummaryService {
    /**
     * Generates a concise summary of a conversation history or incident channel.
     * Identifies key events, resource requests, timestamps, and open questions.
     */
    suspend fun summarizeMessages(messages: List<Message>): AiResult<String>
}
