package com.bingyin.materialyouprefs.data.model

import kotlinx.serialization.Serializable

/**
 * A single CLI parameter for an avbtool subcommand.
 *
 * @property name       argparse option name (e.g. `--key`, `--input`) or positional flag
 * @property description human-readable help text from argparse
 * @property type       inferred type: `string` / `path` / `choice` / `boolean` / `file`
 * @property required   whether argparse marks it required
 * @property choices    possible enum values (if type == choice)
 * @property default    default value as string (nullable)
 */
@Serializable
data class CommandParam(
    val name: String,
    val description: String = "",
    val type: ParamType = ParamType.STRING,
    val required: Boolean = false,
    val choices: List<String> = emptyList(),
    val default: String? = null,
)

@Serializable
enum class ParamType {
    STRING,
    PATH,
    FILE,
    CHOICE,
    BOOLEAN,
    INT,
}

/**
 * A single avbtool subcommand definition cached in Room.
 *
 * @property id          stable unique id, e.g. `avbtool.gen_key_pair`
 * @property name        subcommand name as it appears in CLI (e.g. `gen_key_pair`)
 * @property title       human-readable title for UI
 * @property summary     short one-line summary
 * @property group       UI group id: `KEY` / `HASHTREE` / `VBMETA` / `VERIFY` / `META` / `ALGO` / `CONFIG` / `ABOUT`
 * @property iconKey     key for UI icon lookup (e.g. `KEY`, `VBMETA`, `VERIFY`)
 * @property argsJson    serialized list of positional arguments as JSON string
 * @property paramsJson  serialized list of parameters as JSON string (see [CommandParam])
 * @property isBuiltin   true if definition came from seed JSON, false if fetched from --help
 * @property fetchedAt   timestamp of last --help fetch (ms), used for 24h cache policy
 * @property aospVersion AOSP commit / release this definition targets
 */
data class CommandDefinition(
    val id: String,
    val name: String,
    val title: String,
    val summary: String = "",
    val group: String,
    val iconKey: String = "GENERAL",
    val argsJson: String = "[]",
    val paramsJson: String = "[]",
    val isBuiltin: Boolean = true,
    val fetchedAt: Long = 0L,
    val aospVersion: String = "",
)
