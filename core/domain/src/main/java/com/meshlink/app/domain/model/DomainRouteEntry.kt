package com.meshlink.app.domain.model

data class DomainRouteEntry(
    val nextHopEndpointId: String,
    val batteryLevel: Int = 100,
    val hopCount: Int = 1,
    val addedAt: Long = System.currentTimeMillis()
)
