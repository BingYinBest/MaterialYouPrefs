package com.bingyin.materialyouprefs.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access for [ExecutionEntity].
 */
@Dao
interface ExecutionDao {

    /** Most recent N executions, newest first. */
    @Query("SELECT * FROM execution_history ORDER BY startedAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ExecutionEntity>>

    /** Executions of a specific command, newest first. */
    @Query("SELECT * FROM execution_history WHERE commandId = :commandId ORDER BY startedAtMs DESC")
    fun observeByCommand(commandId: String): Flow<List<ExecutionEntity>>

    @Insert
    suspend fun insert(execution: ExecutionEntity): Long

    @Update
    suspend fun update(execution: ExecutionEntity)

    @Query("DELETE FROM execution_history")
    suspend fun clearAll()

    @Query("DELETE FROM execution_history WHERE startedAtMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)
}
