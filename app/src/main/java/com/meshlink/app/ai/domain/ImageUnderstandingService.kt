package com.meshlink.app.ai.domain

import android.graphics.Bitmap
import com.meshlink.app.ai.model.AiResult

/**
 * Service for offline multimodal image understanding and disaster scene labeling.
 */
interface ImageUnderstandingService {
    /**
     * Identifies visible objects, hazards, and features in the provided bitmap.
     */
    suspend fun analyzeScene(bitmap: Bitmap): AiResult<List<String>>

    /**
     * Answers questions regarding an image if multimodal model is active.
     */
    suspend fun queryImage(bitmap: Bitmap, prompt: String): AiResult<String>
}
