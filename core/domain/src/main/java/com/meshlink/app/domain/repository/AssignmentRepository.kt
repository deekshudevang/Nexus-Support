package com.meshlink.app.domain.repository

import com.meshlink.app.domain.model.AssignmentData
import kotlinx.coroutines.flow.Flow

interface AssignmentRepository {
    fun getAllAssignments(): Flow<List<AssignmentData>>
    fun getActiveAssignments(): Flow<List<AssignmentData>>
    suspend fun getAssignmentById(id: String): AssignmentData?
    suspend fun acceptAssignment(id: String)
    suspend fun updateAssignmentStatus(id: String, status: String)
    suspend fun saveAssignment(assignment: AssignmentData)
}
