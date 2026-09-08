package com.bingyin.materialyouprefs.data.repository

import android.content.Context
import com.bingyin.materialyouprefs.data.db.AvbDatabase
import com.bingyin.materialyouprefs.data.db.CommandEntity
import com.bingyin.materialyouprefs.data.model.CommandDefinition
import com.bingyin.materialyouprefs.data.model.CommandParam
import com.bingyin.materialyouprefs.data.model.ParamType
import com.bingyin.materialyouprefs.data.seed.CommandSeed
import com.bingyin.materialyouprefs.data.seed.CommandSeedEntry
import com.bingyin.materialyouprefs.data.seed.CommandSeedParam
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 命令定义仓储。策略（docs/tech/DATA_LAYER.md §2）：
 *  - 首次启动 → [seedFromAssets]：把 assets/commands_seed.json 灌进 Room
 *  - 读取单条时：本地 fetchedAt 距今 < 24h → 返回缓存；否则调 [AvbToolRunner.fetchHelp] 动态拉
 *  - 24h TTL 避免每次都 fork avbtool 进程
 */
@Singleton
class CommandRepository @Inject constructor(
    private val db: AvbDatabase,
    private val avbRunner: AvbToolRunner,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val dao = db.commandDao()

    companion object {
        const val CACHE_TTL_MS: Long = 24 * 3600 * 1000L
        const val ID_PREFIX = "avbtool."
    }

    fun observeAll(): Flow<List<CommandDefinition>> = dao.observeAll().map {
        it.map { e -> e.toModel() }
    }

    fun observeByGroup(group: String): Flow<List<CommandDefinition>> =
        dao.observeByGroup(group).map { it.map { e -> e.toModel() } }

    fun observeGroups(): Flow<List<String>> = dao.observeGroups()

    suspend fun getById(id: String): CommandDefinition? = dao.getById(id)?.toModel()

    /** 首次启动的种子装载：assets/commands_seed.json → Room。返回写入条数。 */
    suspend fun seedFromAssets(context: Context): Int {
        if (dao.count() > 0) return 0
        val text = runCatching { context.assets.open("commands_seed.json").bufferedReader().readText() }
            .getOrElse { return 0 }
        val seed = runCatching { json.decodeFromString(CommandSeed.serializer(), text) }
            .getOrElse { return 0 }
        val rows = seed.commands.map { entry -> entry.toEntity(aospHead = seed.aospHead) }
        dao.upsertAll(rows)
        return rows.size
    }

    /**
     * 读取单条命令；命中缓存直接返回，否则调 fetchHelp 刷新 paramsJson。
     */
    suspend fun getByIdOrFetch(id: String): CommandDefinition? {
        val existing = dao.getById(id)
        if (existing != null && existing.fetchedAt != 0L &&
            System.currentTimeMillis() - existing.fetchedAt < CACHE_TTL_MS
        ) return existing.toModel()

        val name = existing?.name ?: id.removePrefix(ID_PREFIX)
        val refreshedParams = runCatching { avbRunner.fetchHelp(name) }.getOrNull()
        if (refreshedParams == null && existing == null) return null

        val updated = (existing ?: CommandEntity(
            id = id,
            name = name,
            title = name,
            summary = "",
            group = guessGroup(name),
            iconKey = guessIconKey(name),
            argsJson = "[]",
            paramsJson = "[]",
            isBuiltin = false,
            fetchedAt = 0L,
            aospVersion = avbRunner.aospHead(),
        )).copy(
            paramsJson = refreshedParams?.let { json.encodeToString(it) } ?: existing?.paramsJson ?: "[]",
            fetchedAt = System.currentTimeMillis(),
            isBuiltin = false,
            aospVersion = avbRunner.aospHead(),
        )
        dao.upsert(updated)
        return updated.toModel()
    }

    /** 清缓存，测试 / 强制刷新用。 */
    suspend fun clearCache() = dao.clearAll()

    private fun CommandSeedEntry.toEntity(aospHead: String): CommandEntity {
        val params = params.map { p -> CommandParam(
            name = p.name,
            description = p.description,
            required = p.required,
            type = runCatching { ParamType.valueOf(p.type.uppercase()) }.getOrNull() ?: ParamType.STRING,
        ) }
        return CommandEntity(
            id = ID_PREFIX + name,
            name = name,
            title = title,
            summary = summary,
            group = group.uppercase(),
            iconKey = icon.uppercase(),
            argsJson = "[]",
            paramsJson = json.encodeToString(params),
            isBuiltin = true,
            fetchedAt = 0L,
            aospVersion = aospHead,
        )
    }

    private fun guessGroup(name: String): String = when {
        name.contains("key") || name.contains("rsa") -> "KEY"
        name.contains("hashtree") || name.contains("tree") -> "HASHTREE"
        name.contains("vbmeta") -> "VBMETA"
        name.contains("verify") || name.contains("check") -> "VERIFY"
        name.contains("meta") || name.contains("descriptor") -> "META"
        name.contains("hash") || name.contains("sha") -> "ALGO"
        else -> "GENERAL"
    }

    private fun guessIconKey(name: String): String = guessGroup(name)
}
