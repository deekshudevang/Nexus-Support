package com.meshlink.app.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpeechManagerTest {

    private val mockContext: Context = mockk(relaxed = true)
    private lateinit var speechManager: SpeechManager

    @Before
    fun setUp() {
        speechManager = SpeechManager(mockContext)
    }

    @Test
    fun defaultSpeechRate_isCalibratedCalmRate() {
        assertEquals(0.85f, speechManager.getSpeechRate(), 0.001f)
    }

    @Test
    fun setSpeechRate_clampsWithinBounds() {
        speechManager.setSpeechRate(0.2f)
        assertEquals(0.5f, speechManager.getSpeechRate(), 0.001f)

        speechManager.setSpeechRate(3.0f)
        assertEquals(2.0f, speechManager.getSpeechRate(), 0.001f)

        speechManager.setSpeechRate(0.9f)
        assertEquals(0.9f, speechManager.getSpeechRate(), 0.001f)
    }

    @Test
    fun sanitizeForTts_stripsCodeBlocksAndMarkdownFormatting() {
        val markdownText = """
            **What Is Photosynthesis?**
            * **Sunlight Absorption:** Chlorophyll pigments capture light.
            
            ```python
            def sort_list(arr):
                return sorted(arr)
            ```
            
            [Emergency Notice: AI-generated guidance.]
            Verified Emergency Sources:
            Source ID: bleeding
        """.trimIndent()

        val sanitized = speechManager.sanitizeForTts(markdownText)

        assertFalse(sanitized.contains("**"))
        assertFalse(sanitized.contains("```python"))
        assertFalse(sanitized.contains("def sort_list"))
        assertTrue(sanitized.contains("Code block omitted for voice."))
        assertTrue(sanitized.contains("What Is Photosynthesis?"))
        assertTrue(sanitized.contains("Sunlight Absorption: Chlorophyll pigments capture light."))
        assertFalse(sanitized.contains("[Emergency Notice:"))
        assertFalse(sanitized.contains("Verified Emergency Sources:"))
    }
}
