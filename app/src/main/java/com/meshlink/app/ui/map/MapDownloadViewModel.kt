package com.meshlink.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.map.DownloadState
import com.meshlink.app.map.MapDataManager
import com.meshlink.app.map.MapRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapDownloadViewModel @Inject constructor(
    private val mapDataManager: MapDataManager
) : ViewModel() {

    val availableRegions: List<MapRegion> = mapDataManager.availableRegions

    val downloadStates: StateFlow<Map<String, DownloadState>> = mapDataManager.downloadStates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    fun isDownloaded(regionId: String): Boolean {
        return mapDataManager.isDownloaded(regionId)
    }

    fun startDownload(region: MapRegion) {
        viewModelScope.launch {
            mapDataManager.downloadRegionalMap(region.id, region.downloadUrl)
        }
    }
}
