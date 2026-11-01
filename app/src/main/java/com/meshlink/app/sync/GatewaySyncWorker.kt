package com.meshlink.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.meshlink.app.data.local.dao.SosPacketDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

@HiltWorker
class GatewaySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sosDao: SosPacketDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_GATEWAY_URL = "key_gateway_url"
        const val KEY_GATEWAY_KEY = "key_gateway_key"
        const val DEFAULT_GATEWAY_URL = "http://10.0.2.2:8000/api/v1/sync/sos"
        const val DEFAULT_GATEWAY_KEY = "meshlink_gateway_dev_secret"

        fun buildWorkRequest(
            gatewayUrl: String? = null,
            gatewayKey: String? = null
        ): OneTimeWorkRequest {
            val dataBuilder = Data.Builder()
            gatewayUrl?.let { dataBuilder.putString(KEY_GATEWAY_URL, it) }
            gatewayKey?.let { dataBuilder.putString(KEY_GATEWAY_KEY, it) }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return OneTimeWorkRequestBuilder<GatewaySyncWorker>()
                .setInputData(dataBuilder.build())
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val gatewayUrl = inputData.getString(KEY_GATEWAY_URL)?.takeIf { it.isNotBlank() } ?: DEFAULT_GATEWAY_URL
        val gatewayKey = inputData.getString(KEY_GATEWAY_KEY)?.takeIf { it.isNotBlank() } ?: DEFAULT_GATEWAY_KEY

        try {
            Timber.i("GatewaySyncWorker started: Attempting to sync SOS packets with gateway at $gatewayUrl")

            // Retrieve all SOS packets from the local Room database that need sync
            val allPackets = sosDao.getAllPacketsBlocking()
            val packetsToSync = allPackets.filter {
                it.status != "DELIVERED" && it.status != "ACKNOWLEDGED"
            }

            if (packetsToSync.isEmpty()) {
                Timber.i("GatewaySyncWorker: No unsynced SOS packets to sync.")
                return@withContext Result.success()
            }

            // Create JSON Array payload
            val jsonArray = JSONArray()
            for (packet in packetsToSync) {
                val jsonObj = JSONObject()
                jsonObj.put("uuid", packet.uuid)
                jsonObj.put("senderId", packet.senderId)
                jsonObj.put("senderName", packet.senderName)
                jsonObj.put("emergencyType", packet.emergencyType)
                jsonObj.put("severity", packet.severity)

                val hasFix = (packet.latitude != 0.0 || packet.longitude != 0.0)
                if (hasFix) {
                    jsonObj.put("latitude", packet.latitude)
                    jsonObj.put("longitude", packet.longitude)
                } else {
                    jsonObj.put("latitude", JSONObject.NULL)
                    jsonObj.put("longitude", JSONObject.NULL)
                }
                jsonObj.put("gpsFixAvailable", hasFix)

                jsonObj.put("message", packet.message)
                jsonObj.put("timestamp", packet.timestamp)
                jsonObj.put("status", packet.status)
                jsonObj.put("hopCount", packet.hopCount)
                jsonArray.put(jsonObj)
            }

            val payload = jsonArray.toString()

            // Sync with backend
            val url = URL(gatewayUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Gateway-Key", gatewayKey)
            connection.setRequestProperty("Authorization", "Bearer $gatewayKey")
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseBody = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                Timber.i("GatewaySyncWorker: Gateway returned $responseCode: $responseBody")

                // Per-event receipt reconciliation
                val acknowledgedUuids = mutableSetOf<String>()
                try {
                    val respObj = JSONObject(responseBody)
                    if (respObj.has("receipts")) {
                        val receipts = respObj.getJSONArray("receipts")
                        for (i in 0 until receipts.length()) {
                            val r = receipts.getJSONObject(i)
                            val uuid = r.optString("uuid")
                            if (uuid.isNotEmpty()) {
                                acknowledgedUuids.add(uuid)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse receipt array, falling back to batch acknowledgement")
                }

                // If no specific receipts parsed, acknowledge all sent in batch
                if (acknowledgedUuids.isEmpty()) {
                    for (p in packetsToSync) {
                        acknowledgedUuids.add(p.uuid)
                    }
                }

                for (uuid in acknowledgedUuids) {
                    sosDao.updateStatus(uuid, "ACKNOWLEDGED")
                }

                Timber.i("GatewaySyncWorker: Successfully reconciled ${acknowledgedUuids.size} SOS packets")
                Result.success()
            } else if (responseCode == 401 || responseCode == 403) {
                Timber.e("GatewaySyncWorker: Authentication failure with gateway (HTTP $responseCode). Check X-Gateway-Key.")
                Result.failure()
            } else {
                Timber.e("GatewaySyncWorker: Backend returned error code $responseCode")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "GatewaySyncWorker: Exception during sync")
            Result.retry()
        }
    }
}
