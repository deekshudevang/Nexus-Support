package com.meshlink.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meshlink.app.data.local.dao.LocationEventDao
import com.meshlink.app.data.remote.MeshBackendService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.UnknownHostException
import javax.inject.Provider

@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val locationEventDao: LocationEventDao,
    private val backendServiceProvider: Provider<MeshBackendService>
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Timber.i("CloudSyncWorker: Starting internet sync.")

        try {
            val backendService = backendServiceProvider.get()
            
            // 1. Upload Pending Events
            val pendingEvents = locationEventDao.getPendingCloudUploads()
            if (pendingEvents.isNotEmpty()) {
                Timber.i("CloudSyncWorker: Found ${pendingEvents.size} pending location events. Uploading...")
                val response = backendService.uploadLocationEvents(pendingEvents)
                if (response.success) {
                    val uploadedIds = pendingEvents.map { it.eventId }
                    locationEventDao.markAsUploaded(uploadedIds)
                    Timber.i("CloudSyncWorker: Successfully uploaded and marked ${uploadedIds.size} events as UPLOADED.")
                } else {
                    Timber.w("CloudSyncWorker: Backend rejected upload.")
                    return@withContext Result.retry()
                }
            } else {
                Timber.i("CloudSyncWorker: No pending events to upload.")
            }

            // 2. Fetch New Events
            // In a real app we'd track the last sync timestamp in DataStore.
            // For MVP, we just fetch events from the last 24 hours.
            val yesterday = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            val newEvents = backendService.fetchNewLocationEvents(yesterday)
            
            if (newEvents.isNotEmpty()) {
                Timber.i("CloudSyncWorker: Fetched ${newEvents.size} new events from backend.")
                // Set syncStatus to 1 (UPLOADED) since they came from the cloud and don't need re-uploading
                val eventsToInsert = newEvents.map { it.copy(syncStatus = 1) }
                locationEventDao.insertEvents(eventsToInsert)
            }

            Timber.i("CloudSyncWorker: Sync complete.")
            Result.success()
            
        } catch (e: UnknownHostException) {
            Timber.w(e, "CloudSyncWorker: No internet connection.")
            Result.retry()
        } catch (e: Exception) {
            Timber.e(e, "CloudSyncWorker: Sync failed due to error.")
            Result.retry()
        }
    }
}
