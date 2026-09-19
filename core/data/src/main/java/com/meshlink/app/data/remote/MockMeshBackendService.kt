package com.meshlink.app.data.remote

import com.meshlink.app.data.local.entity.LocationEventEntity
import kotlinx.coroutines.delay

class MockMeshBackendService : MeshBackendService {
    override suspend fun uploadLocationEvents(events: List<LocationEventEntity>): SyncResponse {
        delay(500) // simulate network delay
        return SyncResponse(success = true, uploadedCount = events.size)
    }

    override suspend fun fetchNewLocationEvents(timestamp: Long): List<LocationEventEntity> {
        delay(500) // simulate network delay
        return emptyList() // No new events from cloud for now
    }
}
