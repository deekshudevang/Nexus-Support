package com.meshlink.app.data.remote

import com.meshlink.app.data.local.entity.LocationEventEntity
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MeshBackendService {

    @GET("health")
    suspend fun checkHealth(): HealthResponseDto

    @GET("health/ready")
    suspend fun checkReadiness(): Response<HealthReadyResponseDto>

    @POST("api/v1/sync/sos")
    suspend fun syncSos(
        @Body packets: List<SosSyncPacketDto>,
        @Header("X-Device-Id") deviceId: String? = null,
        @Header("X-Organization-Id") organizationId: String? = null,
        @Header("X-Ack-Assignment-Ids") ackAssignmentIds: String? = null,
        @Header("X-Gateway-Key") gatewayKey: String? = null
    ): SosSyncResponseDto

    @POST("api/v1/sync/location")
    suspend fun uploadLocationEvents(
        @Body events: List<LocationEventEntity>,
        @Header("X-Gateway-Key") gatewayKey: String? = null
    ): SyncResponse

    @GET("api/v1/sync/location")
    suspend fun fetchNewLocationEvents(
        @Query("sinceTimestamp") timestamp: Long,
        @Header("X-Gateway-Key") gatewayKey: String? = null
    ): List<LocationEventEntity>

    @POST("api/v1/sync/assignments/{assignment_id}/status")
    suspend fun updateAssignmentStatus(
        @Path("assignment_id") assignmentId: String,
        @Body req: AssignmentStatusUpdateRequest,
        @Header("X-Device-Id") deviceId: String? = null,
        @Header("X-Organization-Id") organizationId: String? = null,
        @Header("X-Gateway-Key") gatewayKey: String? = null
    ): AssignmentStatusUpdateResponse
}

data class HealthResponseDto(
    val status: String = "unknown",
    val version: String? = null,
    val environment: String? = null,
    val database_connected: Boolean = false,
    val migration_head: String? = null,
    val timestamp: Long? = null,
    val service: String? = null
)

data class HealthReadyResponseDto(
    val status: String = "unknown",
    val database: String? = null,
    val outbox_lag: Int? = null
)

data class SosSyncPacketDto(
    val uuid: String,
    val senderId: String,
    val senderName: String,
    val emergencyType: String,
    val severity: Int,
    val latitude: Double?,
    val longitude: Double?,
    val gpsFixAvailable: Boolean,
    val message: String,
    val timestamp: Long,
    val status: String,
    val hopCount: Int
)

data class SosSyncReceiptDto(
    val uuid: String,
    val status: String = "ACKNOWLEDGED",
    val synced: Boolean = true
)

data class SosDownlinkAssignmentDto(
    val id: String? = null,
    val assignment_id: String? = null,
    val incident_id: String? = null,
    val responder_id: String? = null,
    val status: String? = null,
    val role: String? = null,
    val notes: String? = null,
    val description: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val severity: Int = 5,
    val priority: Int = 3,
    val offered_at: Long = 0L
)

data class SosSyncResponseDto(
    val status: String = "success",
    val synced_count: Int = 0,
    val receipts: List<SosSyncReceiptDto> = emptyList(),
    val downlinks: List<SosDownlinkAssignmentDto> = emptyList()
)

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
