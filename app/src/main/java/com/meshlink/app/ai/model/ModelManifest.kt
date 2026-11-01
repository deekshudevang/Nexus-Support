package com.meshlink.app.ai.model

/**
 * Model file descriptor with checksum and metadata.
 */
data class ModelFileDescriptor(
    val fileName: String,
    val sizeBytes: Long,
    val sha256Checksum: String
)

/**
 * Manifest defining model provenance, licensing, and hardware requirements.
 */
data class ModelManifest(
    val schemaVersion: Int = 1,
    val modelId: String,
    val revision: String,
    val format: String, // e.g. "mediapipe_task", "tflite", "gguf"
    val runtime: String, // e.g. "tasks-genai"
    val files: List<ModelFileDescriptor>,
    val licence: String,
    val capabilities: List<String>, // e.g. ["text_generation", "rag", "summarization", "multimodal"]
    val minRamMb: Long = 2048,
    val source: String
)
