package com.meshlink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meshlink.app.data.local.entity.AssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssignmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(assignment: AssignmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assignments: List<AssignmentEntity>)

    @Query("SELECT * FROM assignments ORDER BY offeredAt DESC")
    fun getAll(): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignments ORDER BY offeredAt DESC")
    suspend fun getAllBlocking(): List<AssignmentEntity>

    @Query("SELECT * FROM assignments WHERE status = :status ORDER BY offeredAt DESC")
    fun getByStatus(status: String): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AssignmentEntity?

    @Query("SELECT * FROM assignments WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<AssignmentEntity?>

    @Query("SELECT * FROM assignments WHERE status IN ('DISPATCHED', 'ACCEPTED', 'EN_ROUTE', 'ON_SCENE') ORDER BY priority DESC, offeredAt DESC")
    fun getActiveAssignments(): Flow<List<AssignmentEntity>>

    @Query("UPDATE assignments SET status = :status, respondedAt = :respondedAt, synced = :synced WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, respondedAt: Long, synced: Boolean = false)

    @Query("SELECT * FROM assignments WHERE synced = 0")
    suspend fun getUnsyncedAssignments(): List<AssignmentEntity>

    @Query("UPDATE assignments SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
