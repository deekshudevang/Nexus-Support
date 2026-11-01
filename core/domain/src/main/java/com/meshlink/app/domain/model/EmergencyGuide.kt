package com.meshlink.app.domain.model

/**
 * Domain model for an offline emergency guide.
 */
data class EmergencyGuide(
    val id: String,
    val title: String,
    val icon: String,
    val steps: List<String>,
    val warnings: List<String>,
    val keywords: List<String>
)
