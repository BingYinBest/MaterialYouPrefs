package com.bingyin.materialyouprefs.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bingyin.materialyouprefs.data.model.CommandDefinition

/**
 * Room entity for avbtool subcommand definitions.
 *
 * Mirrors [CommandDefinition] but flattens nested JSON into String columns so
 * Room can store it without a converter. Convert at repository boundary.
 */
@Entity(tableName = "commands")
data class CommandEntity(
    @PrimaryKey val id: String,
    val name: String,
    val title: String,
    val summary: String,
    val `group`: String,
    val iconKey: String,
    val argsJson: String,
    val paramsJson: String,
    val isBuiltin: Boolean,
    val fetchedAt: Long,
    val aospVersion: String,
)
