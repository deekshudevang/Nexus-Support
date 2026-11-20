package com.meshlink.app.data.remote

import com.meshlink.app.data.local.entity.LocationEventEntity
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MeshBackendService {
    
    @POST("/api/v1/sync/location")
    suspend fun uploadLocationEvents(
        @Body events: List<LocationEventEntity>,
        @Header("X-Gateway-Key") gatewayKey: String? = null
    ): SyncResponse

    @GET("/api/v1/sync/location")
    suspend fun fetchNewLocationEvents(
        @Query("sinceTimestamp") timestamp: Long,
        @Header("X-Gateway-Key") gatewayKey: String? = null
    ): List<LocationEventEntity>

    @POST("/api/v1/sync/assignments/{assignment_id}/status")
    suspend fun updateAssignmentStatus(
        @Path("assignment_id") assignmentId: String,
        @Body req: AssignmentStatusUpdateRequest,
        @Header("X-Device-Id") deviceId: String? = null,
        @Header("X-Organization-Id") organizationId: String? = null,
        @Header("X-Gateway-Key") gatewayKey: String? = null
    ): AssignmentStatusUpdateResponse
}

data class SyncResponse(
    val success: Boolean,
    val uploadedCount: Int
)

data class AssignmentStatusUpdateRequest(
    val status: String,
    val device_id: String? = null
)

data class AssignmentStatusUpdateResponse(
    val status: String,
    val assignment_id: String,
    val assignment_status: String,
    val responder_availability: String? = null
)
