package com.meshlink.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meshlink.app.data.local.dao.SosPacketDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@HiltWorker
class GatewaySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sosDao: SosPacketDao
) : CoroutineWorker(context, workerParams) {

    // Target the locally running FastAPI backend
    private val BACKEND_URL = "http://10.0.2.2:8000/api/sync/sos"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Timber.i("GatewaySyncWorker started: Attempting to sync SOS packets")
            
            // Retrieve all SOS packets from the local Room database
            val packets = sosDao.getAllPacketsBlocking()
            if (packets.isEmpty()) {
                Timber.i("GatewaySyncWorker: No SOS packets to sync.")
                return@withContext Result.success()
            }

            // Create JSON Array payload
            val jsonArray = JSONArray()
            for (packet in packets) {
                val jsonObj = org.json.JSONObject()
                jsonObj.put("uuid", packet.uuid)
                jsonObj.put("senderId", packet.senderId)
                jsonObj.put("senderName", packet.senderName)
                jsonObj.put("emergencyType", packet.emergencyType)
                jsonObj.put("severity", packet.severity)
                jsonObj.put("latitude", packet.latitude)
                jsonObj.put("longitude", packet.longitude)
                jsonObj.put("message", packet.message)
                jsonObj.put("timestamp", packet.timestamp)
                jsonObj.put("status", packet.status)
                jsonObj.put("hopCount", packet.hopCount)
                jsonArray.put(jsonObj)
            }

            val payload = jsonArray.toString()
            
            // Sync with backend
            val url = URL(BACKEND_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                Timber.i("GatewaySyncWorker: Successfully synced ${packets.size} SOS packets")
                // Usually we might mark these as synced in the DB, but for now just returning success
                Result.success()
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
