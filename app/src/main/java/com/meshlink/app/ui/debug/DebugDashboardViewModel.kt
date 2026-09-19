package com.meshlink.app.ui.debug

import androidx.lifecycle.ViewModel
import com.meshlink.app.data.local.dao.LocationEventDao
import com.meshlink.app.data.local.dao.ProcessedEventDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class DebugDashboardViewModel @Inject constructor(
    private val locationEventDao: LocationEventDao,
    private val processedEventDao: ProcessedEventDao
) : ViewModel() {
    
    val vectorClocks = flow {
        // Simple polling for now
        while (true) {
            emit(locationEventDao.getVectorClock())
            kotlinx.coroutines.delay(2000)
        }
    }

    val processedCount: Flow<Int> = processedEventDao.getProcessedCountFlow()
}
