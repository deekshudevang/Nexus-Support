package com.meshlink.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
     * Emits a continuous stream of location updates.
     * Uses battery optimization (dynamically checks battery level).
     */
    @SuppressLint("MissingPermission")
    fun getLocationFlow(): Flow<Location> = callbackFlow {
        // Battery Tuning check
        val batteryStatus = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (scale > 0) level * 100 / scale.toFloat() else 100f
        
        val isLowBattery = batteryPct < 20f
        
        // Duty cycle adjustments based on battery
        val priority = if (isLowBattery) Priority.PRIORITY_LOW_POWER else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val intervalMs = if (isLowBattery) 15 * 60 * 1000L else 5 * 60 * 1000L
        val minIntervalMs = if (isLowBattery) 5 * 60 * 1000L else 60 * 1000L
        val minDistanceM = if (isLowBattery) 200f else 50f

        val locationRequest = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(minIntervalMs)
            .setMinUpdateDistanceMeters(minDistanceM)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            android.os.Looper.getMainLooper()
        ).addOnFailureListener { e ->
            close(e)
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
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
