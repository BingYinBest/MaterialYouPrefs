package com.bingyin.materialyouprefs.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bingyin.materialyouprefs.AppState
import com.bingyin.materialyouprefs.data.model.CommandDefinition
import com.bingyin.materialyouprefs.data.repository.CommandRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home tab.
 *
 * M2.6+ layout: 4 cards
 *   1. Version card     — avbtool version, AOSP HEAD, FEC status, refresh button
 *   2. Terminal entry   — big card that navigates to the Terminal route
 *   3. Recommended cmds — M3+: from execution_history; M2.6+ uses a static
 *                          list of the 5 most useful commands
 *   4. Runtime status   — Python runtime / Chaquopy / FEC / seed count
 */
class HomeViewModel(
    private val repository: CommandRepository,
) : ViewModel() {

    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ---- Version card -------------------------------------------------
    private val _version = MutableStateFlow(Snapshot.fromAppState())
    val version: StateFlow<Snapshot> = _version.asStateFlow()

    // ---- Recommended commands card ------------------------------------
    // M2.6+: static. M3+: replaced with `executionDao.observeRecent(5)`.
    private val _recommended = MutableStateFlow(emptyList<CommandDefinition>())
    val recommended: StateFlow<List<CommandDefinition>> = _recommended.asStateFlow()

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

        // Factory so HomeScreen can construct with `viewModel()` plumbing
        // without dragging AndroidViewModel into the test classpath.
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = AppState.commandRepository
                    ?: throw IllegalStateException("AppState.commandRepository not built yet")
                return HomeViewModel(repo) as T
            }
        }
    }
}
