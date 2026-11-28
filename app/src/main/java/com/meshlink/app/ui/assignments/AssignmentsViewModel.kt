package com.meshlink.app.ui.assignments

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.meshlink.app.domain.model.AssignmentData
import com.meshlink.app.domain.repository.AssignmentRepository
import com.meshlink.app.sync.GatewaySyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssignmentsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assignmentRepository: AssignmentRepository
) : ViewModel() {

    val assignments: StateFlow<List<AssignmentData>> = assignmentRepository.getAllAssignments()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun acceptAssignment(id: String) {
        viewModelScope.launch {
            assignmentRepository.acceptAssignment(id)
            triggerSync()
        }
    }

    fun updateStatus(id: String, newStatus: String) {
        viewModelScope.launch {
            assignmentRepository.updateAssignmentStatus(id, newStatus)
            triggerSync()
        }
    }

    private fun triggerSync() {
        try {
            WorkManager.getInstance(context).enqueue(GatewaySyncWorker.buildWorkRequest(pullDownlinks = true))
        } catch (e: Exception) {
            // Ignore in testing environments without WorkManager initialization
        }
    }
}
