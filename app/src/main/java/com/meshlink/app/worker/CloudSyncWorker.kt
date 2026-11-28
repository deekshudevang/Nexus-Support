package com.meshlink.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meshlink.app.data.local.dao.LocationEventDao
import com.meshlink.app.data.remote.MeshBackendService
import com.meshlink.app.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber
import java.net.UnknownHostException
import javax.inject.Provider
import javax.net.ssl.SSLException

@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val locationEventDao: LocationEventDao,
    private val backendServiceProvider: Provider<MeshBackendService>,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        Timber.i("CloudSyncWorker: Starting internet sync.")

        try {
            val backendService = backendServiceProvider.get()
            var uploadedCount = 0

            // 1. Upload Pending Location Events
            val pendingEvents = locationEventDao.getPendingCloudUploads()
            if (pendingEvents.isNotEmpty()) {
                Timber.i("CloudSyncWorker: Found ${pendingEvents.size} pending location events. Uploading...")
                val response = backendService.uploadLocationEvents(pendingEvents)
                if (response.success) {
                    val uploadedIds = pendingEvents.map { it.eventId }
                    locationEventDao.markAsUploaded(uploadedIds)
                    uploadedCount = uploadedIds.size
                    Timber.i("CloudSyncWorker: Marked $uploadedCount events as UPLOADED.")
                } else {
                    Timber.w("CloudSyncWorker: Backend rejected location upload.")
                    settingsRepository.recordSyncResult(now, success = false, error = "Location upload rejected by backend")
                    return@withContext Result.retry()
                }
            }

            // 2. Fetch New Events from last 24h
            val yesterday = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            val newEvents = backendService.fetchNewLocationEvents(yesterday)
            if (newEvents.isNotEmpty()) {
                val eventsToInsert = newEvents.map { it.copy(syncStatus = 1) }
                locationEventDao.insertEvents(eventsToInsert)
            }

            settingsRepository.recordSyncResult(now, success = true, syncedCount = uploadedCount, httpStatus = 200)
            Timber.i("CloudSyncWorker: Sync complete.")
            Result.success()
        } catch (e: HttpException) {
            val code = e.code()
            val errorMsg = "Location sync HTTP $code: ${e.message()}"
            Timber.e(e, "CloudSyncWorker: $errorMsg")
            settingsRepository.recordSyncResult(now, success = false, httpStatus = code, error = errorMsg)
            if (code == 401 || code == 403) Result.failure() else Result.retry()
        } catch (e: SSLException) {
            val errorMsg = "Location sync TLS error: ${e.message}"
            Timber.e(e, "CloudSyncWorker: $errorMsg")
            settingsRepository.recordSyncResult(now, success = false, error = errorMsg)
            Result.retry()
        } catch (e: UnknownHostException) {
            val errorMsg = "No internet or DNS unreachable during location sync"
            Timber.w(e, "CloudSyncWorker: $errorMsg")
            settingsRepository.recordSyncResult(now, success = false, error = errorMsg)
            Result.retry()
        } catch (e: Exception) {
            val errorMsg = "Location sync error: ${e.message ?: e::class.java.simpleName}"
            Timber.e(e, "CloudSyncWorker: $errorMsg")
            settingsRepository.recordSyncResult(now, success = false, error = errorMsg)
            Result.retry()
        }
    }
}
