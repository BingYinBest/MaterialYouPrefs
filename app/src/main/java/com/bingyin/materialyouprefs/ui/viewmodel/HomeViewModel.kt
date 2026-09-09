package com.bingyin.materialyouprefs.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bingyin.materialyouprefs.AppState
import com.bingyin.materialyouprefs.data.db.ExecutionDao
import com.bingyin.materialyouprefs.data.db.ExecutionEntity
import com.bingyin.materialyouprefs.data.model.CommandDefinition
import com.bingyin.materialyouprefs.data.repository.CommandRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home tab.
 *
 * M2.6+ layout: 4 cards
 *   1. Version card     — avbtool version, AOSP HEAD, FEC status, refresh button
 *   2. Terminal entry   — big card that navigates to the Terminal route
 *   3. Recommended cmds — M4.3+: sourced from execution_history top N
 *                          (falls back to a static curated list until the
 *                          user has run any command)
 *   4. Runtime status   — Python runtime / Chaquopy / FEC / seed count
 */
class HomeViewModel(
    private val repository: CommandRepository,
    private val executionDao: ExecutionDao?,
) : ViewModel() {

    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ---- Version card -------------------------------------------------
    private val _version = MutableStateFlow(Snapshot.fromAppState())
    val version: StateFlow<Snapshot> = _version.asStateFlow()

    // ---- Recommended commands card ------------------------------------
    // M4.3: seed with the curated list, then stream from execution_history.
    // If the user has never run anything, the curated list stays visible;
    // once executions exist, the top N most recent show up live.
    private val _recommended = MutableStateFlow(emptyList<CommandDefinition>())
    val recommended: StateFlow<List<CommandDefinition>> = _recommended.asStateFlow()

    // ---- Recent executions (M4.3) -------------------------------------
    // Newest first. Empty list means "user has not run anything yet" — the
    // Home Card 3 falls back to the static [recommended] list.
    private val _recent = MutableStateFlow(emptyList<RecentEntry>())
    val recent: StateFlow<List<RecentEntry>> = _recent.asStateFlow()

    // ---- Runtime status card ------------------------------------------
    private val _runtimeStatus = MutableStateFlow(runtimeStatus())
    val runtimeStatus: StateFlow<RuntimeStatus> = _runtimeStatus.asStateFlow()

    // ---- Refresh state -------------------------------------------------
    val refreshState: StateFlow<Boolean> = AppState.refreshState

    init {
        // Populate recommended list on first emission.
        vmScope.launch {
            _recommended.value = RECOMMENDED_IDS.mapNotNull { repository.getById(it) }
        }
        // Stream recent executions. mapLatest restarts on each new emission
        // so the UI shows the latest snapshot even when getById() is slow.
        executionDao?.observeRecent(RECENT_LIMIT)?.let { flow ->
            vmScope.launch {
                flow.mapLatest { rows ->
                    rows.map { entity -> entity.toRow(repository, fallbackName) }
                }.collect { _recent.value = it }
            }
        }
    }

    /**
     * Called by the Home version-card refresh button. Debounced by
     * [AppState.REFRESH_DEBOUNCE_MS]; returns whether the refresh was
     * actually accepted (false = caller can show a "just refreshed" hint).
     */
    fun refresh(): Boolean {
        val accepted = AppState.refresh()
        _version.value = Snapshot.fromAppState()
        _runtimeStatus.value = runtimeStatus()
        return accepted
    }

    override fun onCleared() {
        super.onCleared()
        vmScope.cancel()
    }

    // ---------- Snapshot / status data classes -------------------------

    data class Snapshot(
        val avbToolVersion: String,
        val aospHead: String,
        val fecLoaded: Boolean,
        val lastRefreshAtMs: Long,
    ) {
        fun shortAospHead(): String =
            aospHead.takeIf { it.length >= 8 }?.take(8) ?: "unknown"

        companion object {
            fun fromAppState(): Snapshot = Snapshot(
                avbToolVersion = AppState.avbToolVersion,
                aospHead = AppState.aospHead,
                fecLoaded = AppState.fecLoaded,
                lastRefreshAtMs = AppState.lastRefreshAtMs,
            )
        }
    }

    data class RuntimeStatus(
        val pythonRuntime: String,
        val pythonVersion: String,
        val fecLoaded: Boolean,
        val seedRowCount: Int,
    )

    /**
     * A single row in the Home "常用命令" card sourced from execution_history.
     * M4.3: replaces the static RECOMMENDED list once the user has run
     * at least one command.
     */
    data class RecentEntry(
        val commandId: String,
        val title: String,
        val name: String,
        val summary: String,
        val iconKey: String,
        val exitCode: Int,
        val startedAtMs: Long,
        val durationMs: Long,
    ) {
        val succeeded: Boolean get() = exitCode == 0
    }

    private fun runtimeStatus(): RuntimeStatus = RuntimeStatus(
        pythonRuntime = AppState.pythonRuntime,
        pythonVersion = AppState.pythonVersion,
        fecLoaded = AppState.fecLoaded,
        seedRowCount = AppState.seedRowCount,
    )

    companion object {
        // The 5 most useful commands for the Home "常用命令" card.
        // IDs must match `CommandRepository.ID_PREFIX + name`.
        val RECOMMENDED_IDS = listOf(
            "avbtool.gen_key_pair",
            "avbtool.make_vbmeta_image",
            "avbtool.add_hashtree_footer",
            "avbtool.verify_image",
            "avbtool.info_image",
        )

        // How many of the most recent execution_history rows to surface.
        const val RECENT_LIMIT = 5

        // Fallback name when the repository hasn't loaded the command
        // definition yet. Keeps the row visible rather than dropping it.
        private val fallbackName: Map<String, CommandDefinition> = emptyMap()

        /**
         * Map an [ExecutionEntity] to a UI-facing [RecentEntry]. Called from
         * a coroutine on [Dispatchers.Main.immediate] — the `getById` call is
         * wrapped in runCatching to survive transient DB failures.
         */
        internal suspend fun ExecutionEntity.toRow(
            repository: CommandRepository,
            fallbacks: Map<String, CommandDefinition> = emptyMap(),
        ): RecentEntry {
            val def = runCatching { repository.getById(commandId) }.getOrNull()
                ?: fallbacks[commandId]
            return RecentEntry(
                commandId = commandId,
                title = def?.title ?: commandId.removePrefix("avbtool."),
                name = def?.name ?: commandId.removePrefix("avbtool."),
                summary = def?.summary.orEmpty(),
                iconKey = def?.iconKey ?: "GENERAL",
                exitCode = exitCode,
                startedAtMs = startedAtMs,
                durationMs = durationMs,
            )
        }

        /**
         * Human-readable relative time for the Home recent-executions rows.
         * Extracted as a pure function so it can be unit-tested without
         * touching Android APIs.
         */
        fun formatRelative(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
            val delta = nowMs - timestampMs
            return when {
                delta < 0L -> "刚刚"
                delta < 1_000L -> "刚刚"
                delta < 60_000L -> "${delta / 1_000L} 秒前"
                delta < 3_600_000L -> "${delta / 60_000L} 分钟前"
                delta < 86_400_000L -> "${delta / 3_600_000L} 小时前"
                else -> "${delta / 86_400_000L} 天前"
            }
        }

        /**
         * Human-readable duration. Small values stay in ms; larger ones
         * show seconds (with one decimal) or minutes.
         */
        fun formatDurationMs(durationMs: Long): String = when {
            durationMs < 1_000L -> "${durationMs}ms"
            durationMs < 60_000L -> {
                val s = durationMs / 1_000L
                val frac = (durationMs % 1_000L) / 100L
                "$s.$fracs"
            }
            else -> "${durationMs / 60_000L}分${durationMs / 1_000L % 60L}秒"
        }

        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = AppState.commandRepository
                    ?: throw IllegalStateException("AppState.commandRepository not built yet")
                val dao = AppState.database?.executionDao()
                return HomeViewModel(repo, dao) as T
            }
        }
    }
}
