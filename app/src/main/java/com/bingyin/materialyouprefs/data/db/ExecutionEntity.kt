package com.bingyin.materialyouprefs.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single execution record of an avbtool subcommand.
 *
 * @property commandId     points to [CommandEntity.id]
 * @property argsJson      snapshot of args at execution time
 * @property paramsJson    snapshot of params at execution time
 * @property stdout        captured stdout (may be truncated)
 * @property stderr        captured stderr
 * @property exitCode      process exit code, -1 if not finished
 * @property startedAtMs   wall-clock timestamp
 * @property durationMs    wall-clock duration in milliseconds
 * @property inputFiles    comma-separated input SAF URIs (may be empty)
 * @property outputFiles   comma-separated output SAF URIs (may be empty)
 */
@Entity(tableName = "execution_history")
data class ExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val commandId: String,
    val argsJson: String,
    val paramsJson: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val startedAtMs: Long,
    val durationMs: Long,
    val inputFiles: String = "",
    val outputFiles: String = "",
)
