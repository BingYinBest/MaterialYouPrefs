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
) {
    fun toModel(): CommandDefinition = CommandDefinition(
        id = id,
        name = name,
        title = title,
        summary = summary,
        `group` = `group`,
        iconKey = iconKey,
        argsJson = argsJson,
        paramsJson = paramsJson,
        isBuiltin = isBuiltin,
        fetchedAt = fetchedAt,
        aospVersion = aospVersion,
    )

    companion object {
        fun fromModel(d: CommandDefinition) = CommandEntity(
            id = d.id,
            name = d.name,
            title = d.title,
            summary = d.summary,
            `group` = d.`group`,
            iconKey = d.iconKey,
            argsJson = d.argsJson,
            paramsJson = d.paramsJson,
            isBuiltin = d.isBuiltin,
            fetchedAt = d.fetchedAt,
            aospVersion = d.aospVersion,
        )
    }
}
