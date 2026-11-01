package com.meshlink.app.domain.repository

import com.meshlink.app.domain.model.EmergencyGuide
import kotlinx.coroutines.flow.Flow

/**
 * Repository for accessing the offline emergency knowledge base.
 */
interface EmergencyGuideRepository {
    fun getAllGuides(): Flow<List<EmergencyGuide>>
    fun searchGuides(query: String): Flow<List<EmergencyGuide>>
    suspend fun getGuideById(id: String): EmergencyGuide?
}
