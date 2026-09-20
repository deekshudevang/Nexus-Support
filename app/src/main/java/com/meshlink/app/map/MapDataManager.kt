package com.meshlink.app.map

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MapRegion(
    val id: String,
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}

@Singleton
class MapDataManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mapsDir = File(context.filesDir, "offline_maps").apply {
        if (!exists()) mkdirs()
    }

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    // Sample regions for India. In a real app, these files must be generated using Mapsforge Map-Writer
    // and hosted on a backend server (e.g., maps.meshlink.app).
    val availableRegions = listOf(
        MapRegion("andhra_pradesh", "Andhra Pradesh", "https://maps.meshlink.app/india/andhra_pradesh.map", 45000000),
        MapRegion("arunachal_pradesh", "Arunachal Pradesh", "https://maps.meshlink.app/india/arunachal_pradesh.map", 15000000),
        MapRegion("assam", "Assam", "https://maps.meshlink.app/india/assam.map", 30000000),
        MapRegion("bihar", "Bihar", "https://maps.meshlink.app/india/bihar.map", 35000000),
        MapRegion("chhattisgarh", "Chhattisgarh", "https://maps.meshlink.app/india/chhattisgarh.map", 25000000),
        MapRegion("goa", "Goa", "https://maps.meshlink.app/india/goa.map", 5000000),
        MapRegion("gujarat", "Gujarat", "https://maps.meshlink.app/india/gujarat.map", 40000000),
        MapRegion("haryana", "Haryana", "https://maps.meshlink.app/india/haryana.map", 20000000),
        MapRegion("himachal_pradesh", "Himachal Pradesh", "https://maps.meshlink.app/india/himachal_pradesh.map", 18000000),
        MapRegion("jharkhand", "Jharkhand", "https://maps.meshlink.app/india/jharkhand.map", 22000000),
        MapRegion("karnataka", "Karnataka", "https://maps.meshlink.app/india/karnataka.map", 48000000),
        MapRegion("kerala", "Kerala", "https://maps.meshlink.app/india/kerala.map", 25000000),
        MapRegion("madhya_pradesh", "Madhya Pradesh", "https://maps.meshlink.app/india/madhya_pradesh.map", 50000000),
        MapRegion("maharashtra", "Maharashtra", "https://maps.meshlink.app/india/maharashtra.map", 60000000),
        MapRegion("manipur", "Manipur", "https://maps.meshlink.app/india/manipur.map", 8000000),
        MapRegion("meghalaya", "Meghalaya", "https://maps.meshlink.app/india/meghalaya.map", 7000000),
        MapRegion("mizoram", "Mizoram", "https://maps.meshlink.app/india/mizoram.map", 5000000),
        MapRegion("nagaland", "Nagaland", "https://maps.meshlink.app/india/nagaland.map", 6000000),
        MapRegion("odisha", "Odisha", "https://maps.meshlink.app/india/odisha.map", 32000000),
        MapRegion("punjab", "Punjab", "https://maps.meshlink.app/india/punjab.map", 24000000),
        MapRegion("rajasthan", "Rajasthan", "https://maps.meshlink.app/india/rajasthan.map", 45000000),
        MapRegion("sikkim", "Sikkim", "https://maps.meshlink.app/india/sikkim.map", 3000000),
        MapRegion("tamil_nadu", "Tamil Nadu", "https://maps.meshlink.app/india/tamil_nadu.map", 55000000),
        MapRegion("telangana", "Telangana", "https://maps.meshlink.app/india/telangana.map", 38000000),
        MapRegion("tripura", "Tripura", "https://maps.meshlink.app/india/tripura.map", 9000000),
        MapRegion("uttar_pradesh", "Uttar Pradesh", "https://maps.meshlink.app/india/uttar_pradesh.map", 70000000),
        MapRegion("uttarakhand", "Uttarakhand", "https://maps.meshlink.app/india/uttarakhand.map", 15000000),
        MapRegion("west_bengal", "West Bengal", "https://maps.meshlink.app/india/west_bengal.map", 42000000)
    )

    suspend fun downloadRegionalMap(regionId: String, downloadUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            val destFile = File(mapsDir, "$regionId.map")
            
            if (destFile.exists()) {
                updateState(regionId, DownloadState.Success)
                return@withContext true
            }

            try {
                updateState(regionId, DownloadState.Downloading(0f))
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    updateState(regionId, DownloadState.Error("HTTP ${connection.responseCode}"))
                    return@withContext false
                }

                val fileLength = connection.contentLength
                val input = connection.inputStream
                val output = FileOutputStream(destFile)

                val buffer = ByteArray(4096)
                var total: Long = 0
                var bytesRead: Int
                
                var lastProgressUpdate = System.currentTimeMillis()

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    total += bytesRead
                    output.write(buffer, 0, bytesRead)
                    
                    val now = System.currentTimeMillis()
                    if (fileLength > 0 && now - lastProgressUpdate > 100) {
                        val progress = (total * 100f / fileLength) / 100f
                        updateState(regionId, DownloadState.Downloading(progress))
                        lastProgressUpdate = now
                    }
                }

                output.close()
                input.close()
                updateState(regionId, DownloadState.Success)
                true
            } catch (e: Exception) {
                Timber.e(e, "MapDataManager: Failed to download map for $regionId")
                if (destFile.exists()) destFile.delete()
                updateState(regionId, DownloadState.Error(e.localizedMessage ?: "Unknown error"))
                false
            }
        }
    }

    private fun updateState(regionId: String, state: DownloadState) {
        val currentMap = _downloadStates.value.toMutableMap()
        currentMap[regionId] = state
        _downloadStates.value = currentMap
    }

    fun isDownloaded(regionId: String): Boolean {
        return File(mapsDir, "$regionId.map").exists()
    }

    fun getAvailableOfflineMaps(): List<File> {
        return mapsDir.listFiles { _, name -> name.endsWith(".map") }?.toList() ?: emptyList()
    }
}
