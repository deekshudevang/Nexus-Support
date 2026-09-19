package com.meshlink.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Tries to get the current location. If it takes longer than [timeoutMs],
     * falls back to the last known location, or returns null if unavailable.
     */
    @SuppressLint("MissingPermission") // Caller MUST ensure permissions are granted
    suspend fun getCurrentLocation(timeoutMs: Long = 3000): Location? {
        return try {
            withTimeoutOrNull(timeoutMs) {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).await()
            } ?: fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Formats the location as a Maps link, e.g., "https://maps.google.com/?q=lat,lng"
     */
    fun formatLocationUrl(location: Location?): String {
        return if (location != null) {
            "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            "Location unavailable"
        }
    }
}
