package com.bingyin.materialyouprefs.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access for [CommandEntity].
 *
 * Note: `group` and `tab` are quoted with backticks where needed.
 * `group` is a SQLite reserved word; `tab` is not, but we keep the style
 * consistent for readability.
 *
 * Return types:
 * - @Query methods return whatever the SQL projects (Int, entity, Flow<...>).
 * - @Insert/@Update methods return Unit (Kotlin's implicit void). Room 2.6.1
 *   rejects `suspend fun upsertAll(List<Entity>): Int` with
 *   'Not sure how to handle insert method's return type'; the caller can
 *   count locally from its input list.
 */
@Dao
interface CommandDao {

    /** All commands, most-recently-fetched first. */
    @Query("SELECT * FROM commands ORDER BY fetchedAt DESC")
    fun observeAll(): Flow<List<CommandEntity>>

    /** Commands in a single UI tab, alphabetically by title. */
    @Query("SELECT * FROM commands WHERE tab = :tab ORDER BY title ASC")
    fun observeByTab(tab: String): Flow<List<CommandEntity>>

    /** Commands in a single UI group, alphabetically by title. */
    @Query("SELECT * FROM commands WHERE `group` = :group ORDER BY title ASC")
    fun observeByGroup(group: String): Flow<List<CommandEntity>>

    /** Distinct group values, alphabetical. */
    @Query("SELECT DISTINCT `group` FROM commands ORDER BY `group` ASC")
    fun observeGroups(): Flow<List<String>>

    /** Single lookup by stable id. */
    @Query("SELECT * FROM commands WHERE id = :id")
    suspend fun getById(id: String): CommandEntity?

    /** Row count, used by the seeding path. */
    @Query("SELECT COUNT(*) FROM commands")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(commands: List<CommandEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(command: CommandEntity)

    @Update
    suspend fun update(command: CommandEntity)

    @Query("DELETE FROM commands")
    suspend fun clearAll()

    @Query("DELETE FROM commands WHERE id = :id")
    suspend fun delete(id: String)
}
