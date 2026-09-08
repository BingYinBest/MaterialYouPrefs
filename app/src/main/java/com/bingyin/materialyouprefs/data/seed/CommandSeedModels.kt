package com.bingyin.materialyouprefs.data.seed

import kotlinx.serialization.Serializable

@Serializable
data class CommandSeed(
    val version: Int,
    val aospHead: String,
    val commands: List<CommandSeedEntry>,
)

@Serializable
data class CommandSeedEntry(
    val name: String,
    val title: String,
    val summary: String,
    val group: String,
    val icon: String,
    val tab: String,
    val sortOrder: Int = 0,
    val params: List<CommandSeedParam> = emptyList(),
)

@Serializable
data class CommandSeedParam(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String = "",
)
