package com.meshlink.app.sync

import android.content.Context
import android.provider.Settings
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meshlink.app.data.local.dao.AssignmentDao
import com.meshlink.app.data.local.dao.SosPacketDao
import com.meshlink.app.data.local.entity.AssignmentEntity
import com.meshlink.app.data.remote.AssignmentStatusUpdateRequest
import com.meshlink.app.data.remote.MeshBackendService
import com.meshlink.app.data.remote.SosSyncPacketDto
import com.meshlink.app.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * WorkManager worker responsible for syncing locally buffered SOS packets,
 * pending assignment status updates, and server-to-device downlinks via unified Retrofit/OkHttp.
 */
@HiltWorker
class GatewaySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sosDao: SosPacketDao,
    private val assignmentDao: AssignmentDao,
    private val backendService: MeshBackendService,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_GATEWAY_URL = "gateway_url"
        const val KEY_GATEWAY_KEY = "gateway_key"
        const val KEY_PULL_DOWNLINKS = "pull_downlinks"
        const val KEY_DEVICE_ID = "device_id"
        const val WORK_NAME_IMMEDIATE = "sos_gateway_sync"
        const val WORK_NAME_PERIODIC = "gateway_periodic_sync"

        fun buildWorkRequest(
            gatewayUrl: String? = null,
            gatewayKey: String? = null,
            pullDownlinks: Boolean = false,
            deviceId: String? = null
        ): OneTimeWorkRequest {
            val dataBuilder = Data.Builder()
            if (!gatewayUrl.isNullOrBlank()) {
                dataBuilder.putString(KEY_GATEWAY_URL, gatewayUrl)
            }
            if (!gatewayKey.isNullOrBlank()) {
                dataBuilder.putString(KEY_GATEWAY_KEY, gatewayKey)
            }
            if (pullDownlinks) {
                dataBuilder.putBoolean(KEY_PULL_DOWNLINKS, true)
            }
            if (!deviceId.isNullOrBlank()) {
                dataBuilder.putString(KEY_DEVICE_ID, deviceId)
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return OneTimeWorkRequestBuilder<GatewaySyncWorker>()
                .setConstraints(constraints)
                .setInputData(dataBuilder.build())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15_000L,
                    TimeUnit.MILLISECONDS
                )
                .build()
        }

        fun buildPeriodicWorkRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val periodicData = Data.Builder()
                .putBoolean(KEY_PULL_DOWNLINKS, true)
                .build()
            return PeriodicWorkRequestBuilder<GatewaySyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(periodicData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30_000L,
                    TimeUnit.MILLISECONDS
                )
                .build()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val inputUrl = inputData.getString(KEY_GATEWAY_URL)?.trim()?.takeIf { it.isNotBlank() }
        val inputKey = inputData.getString(KEY_GATEWAY_KEY)?.trim()?.takeIf { it.isNotBlank() }

        if (inputUrl != null) {
            try {
                // Allow explicit WorkRequest URL override without touching scheme normalization on full sync endpoint URLs
                val rootUrl = inputUrl.substringBefore("api/").ifBlank { inputUrl }
                settingsRepository.setBackendBaseUrl(rootUrl)
            } catch (e: Exception) {
                val msg = "Invalid backend URL override: ${e.message}"
                Timber.e(e, "GatewaySyncWorker: $msg")
                settingsRepository.recordSyncResult(now, success = false, error = msg)
                return@withContext Result.failure()
            }
        }

        val effectiveKey = inputKey ?: settingsRepository.getEffectiveGatewayKey()
        val effectiveBase = settingsRepository.getEffectiveBackendBaseUrl()
        if (effectiveBase.contains(".invalid", ignoreCase = true)) {
            val msg = "Backend Base URL is not configured; set MESHLINK_PROD_URL or configure in Settings."
            Timber.e("GatewaySyncWorker: $msg")
            settingsRepository.recordSyncResult(now, success = false, error = msg)
            return@withContext Result.failure()
        }
        if (effectiveKey.isBlank()) {
            val msg = "Gateway API Key is not configured; set X-Gateway-Key in Settings or BuildConfig."
            Timber.e("GatewaySyncWorker: $msg")
            settingsRepository.recordSyncResult(now, success = false, error = msg)
            return@withContext Result.failure()
        }
        Timber.i("GatewaySyncWorker started: syncing against $effectiveBase")

        try {
            // 1. Sync any unsynced local assignment status updates
            val unsyncedAssignments = assignmentDao.getUnsyncedAssignments()
            for (asgn in unsyncedAssignments) {
                val resp = backendService.updateAssignmentStatus(
                    assignmentId = asgn.id,
                    req = AssignmentStatusUpdateRequest(
                        status = asgn.status,
                        device_id = asgn.responderId.takeIf { it.isNotBlank() }
                    ),
                    deviceId = asgn.responderId.takeIf { it.isNotBlank() },
                    gatewayKey = effectiveKey.takeIf { it.isNotBlank() }
                )
                if (resp.status == "success" || resp.assignment_status == asgn.status) {
                    assignmentDao.markSynced(asgn.id)
                    Timber.i("GatewaySyncWorker: Synced status ${asgn.status} for assignment ${asgn.id}")
                }
            }

            // 2. Gather SOS packets not yet confirmed by the cloud gateway (peer-ACKed SOS still syncs!)
            val allPackets = sosDao.getAllPacketsBlocking()
            val packetsToSync = allPackets.filter {
                !it.cloudSynced &&
                    it.status != "DELIVERED" &&
                    it.status != "CANCELLED" &&
                    it.status != "EXPIRED"
            }

            val pullDownlinks = inputData.getBoolean(KEY_PULL_DOWNLINKS, false)
            if (packetsToSync.isEmpty() && !pullDownlinks) {
                Timber.i("GatewaySyncWorker: No unsynced SOS packets remaining.")
                settingsRepository.recordSyncResult(
                    timestampMs = now,
                    success = true,
                    syncedCount = unsyncedAssignments.size,
                    httpStatus = 200
                )
                return@withContext Result.success()
            }

            val payload = packetsToSync.map { packet ->
                val hasFix = (packet.latitude != 0.0 || packet.longitude != 0.0)
                SosSyncPacketDto(
                    uuid = packet.uuid,
                    senderId = packet.senderId,
                    senderName = packet.senderName.ifBlank { packet.senderId.ifBlank { "Field Node" } },
                    emergencyType = packet.emergencyType,
                    severity = packet.severity,
                    latitude = if (hasFix) packet.latitude else null,
                    longitude = if (hasFix) packet.longitude else null,
                    gpsFixAvailable = hasFix,
                    message = packet.message,
                    timestamp = packet.timestamp,
                    status = packet.status,
                    hopCount = packet.hopCount
                )
            }

            val ackAssignmentIds = unsyncedAssignments.map { it.id }.joinToString(",").takeIf { it.isNotBlank() }
            val primaryDeviceId = packetsToSync.firstOrNull()?.senderId?.takeIf { it.isNotBlank() }
                ?: inputData.getString(KEY_DEVICE_ID)?.takeIf { it.isNotBlank() }
                ?: allPackets.firstOrNull { it.senderId.isNotBlank() }?.senderId
                ?: runCatching {
                    Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
                }.getOrNull()?.takeIf { it.isNotBlank() }

            val response = backendService.syncSos(
                packets = payload,
                deviceId = primaryDeviceId,
                ackAssignmentIds = ackAssignmentIds,
                gatewayKey = effectiveKey.takeIf { it.isNotBlank() }
            )

            // 3. Reconcile ONLY explicit receipts returned by backend
            var reconciledCount = 0
            for (receipt in response.receipts) {
                if (receipt.uuid.isNotBlank() && receipt.synced) {
                    sosDao.markCloudSynced(receipt.uuid)
                    val matching = packetsToSync.find { it.uuid == receipt.uuid }
                    if (matching != null && matching.status in setOf("PENDING", "SENT", "RELAYED")) {
                        sosDao.updateStatus(receipt.uuid, "DELIVERED")
                    }
                    reconciledCount++
                }
            }

            // 4. Persist any downlink assignments scoped for this device/responder
            for (dl in response.downlinks) {
                val asgnId = dl.id?.takeIf { it.isNotBlank() } ?: dl.assignment_id ?: continue
                val incidentId = dl.incident_id?.takeIf { it.isNotBlank() } ?: continue
                val existing = assignmentDao.getById(asgnId)
                val currentStatus = existing?.status ?: dl.status.orEmpty().ifBlank { "DISPATCHED" }
                val dlRole = dl.role.orEmpty().ifBlank { "SAR" }
                val dlNotes = dl.notes.orEmpty()
                val dlDesc = dl.description.orEmpty()
                val entity = AssignmentEntity(
                    id = asgnId,
                    incidentId = incidentId,
                    responderId = dl.responder_id.orEmpty(),
                    role = dlRole,
                    status = currentStatus,
                    notes = dlNotes,
                    description = dlDesc,
                    latitude = dl.latitude,
                    longitude = dl.longitude,
                    priority = dl.priority,
                    severity = dl.severity,
                    offeredAt = dl.offered_at,
                    respondedAt = existing?.respondedAt,
                    synced = existing?.synced ?: false
                )
                assignmentDao.insertOrUpdate(entity)
                if (existing == null && currentStatus == "DISPATCHED") {
                    try {
                        com.meshlink.app.notification.AssignmentNotificationHelper.showAssignmentHeadsUpNotification(
                            context = applicationContext,
                            assignmentId = asgnId,
                            title = dlNotes.ifBlank { "Incident #$incidentId" },
                            notes = dlDesc.ifBlank { "Role: $dlRole | Priority ${dl.priority}" }
                        )
                    } catch (t: Throwable) {
                        Timber.w(t, "GatewaySyncWorker: unable to display assignment notification")
                    }
                }
            }

            settingsRepository.recordSyncResult(
                timestampMs = now,
                success = true,
                syncedCount = reconciledCount,
                httpStatus = 200
            )
            Timber.i("GatewaySyncWorker: Reconciled $reconciledCount SOS receipts and ${response.downlinks.size} downlinks")
            Result.success()
        } catch (e: HttpException) {
            val code = e.code()
            val errorMsg = when (code) {
                401, 403 -> "Authentication failure (HTTP $code): check X-Gateway-Key"
                429 -> "Rate limited by gateway (HTTP 429); retrying with backoff"
                else -> "Gateway HTTP $code error: ${e.message()}"
            }
            Timber.e(e, "GatewaySyncWorker: $errorMsg")
            settingsRepository.recordSyncResult(now, success = false, httpStatus = code, error = errorMsg)
            if (code == 401 || code == 403) Result.failure() else Result.retry()
        } catch (e: SSLException) {
            val errorMsg = "TLS/Certificate error connecting to $effectiveBase: ${e.message}"
            Timber.e(e, "GatewaySyncWorker: $errorMsg")
            settingsRepository.recordSyncResult(now, success = false, error = errorMsg)
            Result.retry()
        } catch (e: UnknownHostException) {
            val errorMsg = "Host unreachable ($effectiveBase): check DNS or network"
            Timber.w(e, "GatewaySyncWorker: $errorMsg")
            settingsRepository.recordSyncResult(now, success = false, error = errorMsg)
            Result.retry()
        } catch (e: SocketTimeoutException) {
            val errorMsg = "Connection timed out to $effectiveBase"
            Timber.w(e, "GatewaySyncWorker: $errorMsg")
            settingsRepository.recordSyncResult(now, success = false, error = errorMsg)
            Result.retry()
        } catch (e: IOException) {
            val errorMsg = "Network I/O error: ${e.message ?: "connection failed"}"
            Timber.w(e, "GatewaySyncWorker: $errorMsg")
            settingsRepository.recordSyncResult(now, success = false, error = errorMsg)
            Result.retry()
        } catch (e: Exception) {
            val errorMsg = "Sync failed: ${e.message ?: e::class.java.simpleName}"
            Timber.e(e, "GatewaySyncWorker: $errorMsg")
            settingsRepository.recordSyncResult(now, success = false, error = errorMsg)
            Result.retry()
        }
    }
}
