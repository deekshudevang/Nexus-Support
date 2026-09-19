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

    // Sample regions. In a real app, this would be fetched from a server.
    val availableRegions = listOf(
        MapRegion("berlin", "Berlin, Germany", "https://raw.githubusercontent.com/mapsforge/mapsforge/master/mapsforge-map-reader/src/test/resources/berlin.map", 1500000),
        MapRegion("california", "California, USA", "https://example.com/california.map", 150000000),
        MapRegion("london", "London, UK", "https://example.com/london.map", 50000000)
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
