package com.meshlink.app.domain.model

data class AssignmentData(
    val id: String,
    val incidentId: String,
    val responderId: String = "",
    val role: String = "SAR",
    val status: String = "DISPATCHED",
    val notes: String = "",
    val description: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val priority: Int = 3,
    val severity: Int = 5,
    val offeredAt: Long = 0L,
    val respondedAt: Long? = null,
    val synced: Boolean = false
)
