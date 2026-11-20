package com.meshlink.app.data.remote

import com.meshlink.app.data.local.entity.LocationEventEntity
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface MeshBackendService {
    
    @POST("/api/v1/sync/location")
    suspend fun uploadLocationEvents(
        @Body events: List<LocationEventEntity>,
        @Header("X-Gateway-Key") gatewayKey: String = "meshlink_gw_dev_secret"
    ): SyncResponse

    @GET("/api/v1/sync/location")
    suspend fun fetchNewLocationEvents(
        @Query("sinceTimestamp") timestamp: Long,
        @Header("X-Gateway-Key") gatewayKey: String = "meshlink_gw_dev_secret"
    ): List<LocationEventEntity>
}

data class SyncResponse(
    val success: Boolean,
    val uploadedCount: Int
)
