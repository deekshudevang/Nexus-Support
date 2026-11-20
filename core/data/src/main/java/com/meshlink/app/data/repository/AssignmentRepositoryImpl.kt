package com.meshlink.app.data.repository

import com.meshlink.app.data.local.dao.AssignmentDao
import com.meshlink.app.data.local.entity.AssignmentEntity
import com.meshlink.app.domain.model.AssignmentData
import com.meshlink.app.domain.repository.AssignmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssignmentRepositoryImpl @Inject constructor(
    private val assignmentDao: AssignmentDao
) : AssignmentRepository {

    override fun getAllAssignments(): Flow<List<AssignmentData>> {
        return assignmentDao.getAll().map { list -> list.map { it.toData() } }
    }

    override fun getActiveAssignments(): Flow<List<AssignmentData>> {
        return assignmentDao.getActiveAssignments().map { list -> list.map { it.toData() } }
    }

    override suspend fun getAssignmentById(id: String): AssignmentData? {
        return assignmentDao.getById(id)?.toData()
    }

    override suspend fun acceptAssignment(id: String) {
        assignmentDao.updateStatus(id, "ACCEPTED", System.currentTimeMillis(), synced = false)
    }

    override suspend fun updateAssignmentStatus(id: String, status: String) {
        assignmentDao.updateStatus(id, status, System.currentTimeMillis(), synced = false)
    }

    override suspend fun saveAssignment(assignment: AssignmentData) {
        assignmentDao.insertOrUpdate(assignment.toEntity())
    }
}

fun AssignmentEntity.toData(): AssignmentData = AssignmentData(
    id = id,
    incidentId = incidentId,
    responderId = responderId,
    role = role,
    status = status,
    notes = notes,
    description = description,
    latitude = latitude,
    longitude = longitude,
    priority = priority,
    severity = severity,
    offeredAt = offeredAt,
    respondedAt = respondedAt,
    synced = synced
)

fun AssignmentData.toEntity(): AssignmentEntity = AssignmentEntity(
    id = id,
    incidentId = incidentId,
    responderId = responderId,
    role = role,
    status = status,
    notes = notes,
    description = description,
    latitude = latitude,
    longitude = longitude,
    priority = priority,
    severity = severity,
    offeredAt = offeredAt,
    respondedAt = respondedAt,
    synced = synced
)
