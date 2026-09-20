package com.meshlink.app.ai

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionManager @Inject constructor() {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.DEFAULT_OPTIONS
    )

    /**
     * Analyzes a bitmap and returns a list of high-confidence labels.
     */
    suspend fun analyzeImage(bitmap: Bitmap): List<String> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val labels = labeler.process(image).await()
            labels.map { it.text }
        } catch (e: Exception) {
            Timber.e(e, "Error processing image with ML Kit Vision")
            emptyList()
        }
    }
}
