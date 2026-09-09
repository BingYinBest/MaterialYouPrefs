package com.bingyin.materialyouprefs

import com.bingyin.materialyouprefs.data.db.AvbDatabase
import com.bingyin.materialyouprefs.data.repository.AvbToolRunner
import com.bingyin.materialyouprefs.data.repository.CommandRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manual singletons holder. See ADR-010: we keep `@Inject` / `@Singleton`
 * annotations as metadata-only (documentation of intent) and wire the graph
 * by hand from [MaterialYouPrefsApplication.onCreate].
 *
 * Using a DI framework (Hilt / Koin) would add significant overhead at M1/M2;
 * the graph is small enough that a static holder is clearer.
 *
 * Access is synchronized where possible to guard against a rare race between
 * Application.onCreate and the first screen composition.
 */
object AppState {

    @Volatile var database: AvbDatabase? = null
    @Volatile var avbToolRunner: AvbToolRunner? = null
    @Volatile var commandRepository: CommandRepository? = null

    /** Last seedFromAssets failure, if any. Null on success. */
    @Volatile var seedFailure: Throwable? = null

    /** Number of rows inserted during seedFromAssets; -1 if seed skipped. */
    @Volatile var seedRowCount: Int = -1

    // ---------------- Runtime / version info (M2.6+) ----------------

    /**
     * Hardcoded avbtool version string shown on the Home version card.
     * Mirrors the AOSP version in `avbtool.py --version`. Update when we
     * patch avbtool in M3.
     */
    @Volatile var avbToolVersion: String = "1.0.0"

    /** AOSP HEAD commit this runtime is built from. Empty until M3 wires the real value. */
    @Volatile var aospHead: String = "386fb90492db3bd6bc484a579bcde5b43a2a0292"

    /** Whether the FEC native library (libavbfec.so) is loaded. Always false until M3. */
    @Volatile var fecLoaded: Boolean = false

    /** Python runtime identifier; "Chaquopy" is the M3 target. */
    @Volatile var pythonRuntime: String = "Chaquopy 15.0.1"

    /** Python minor version. */
    @Volatile var pythonVersion: String = "3.12"

    // ---------------- Refresh state (M2.6+) ----------------

    /** Timestamp of the last successful refresh; 0L = never refreshed. */
    @Volatile var lastRefreshAtMs: Long = 0L

    /** Debounce window for [refresh]: calls within 30s are ignored. */
    const val REFRESH_DEBOUNCE_MS: Long = 30_000L

    private val _refreshState = MutableStateFlow(false)

    /** Whether a refresh is currently in flight. */
    val refreshState: StateFlow<Boolean> = _refreshState.asStateFlow()

    /** Snapshot of runtime info for UI consumption. */
    fun runtimeStatusMap(): Map<String, String> {
        val dbRows = seedRowCount
        return mapOf(
            "avbToolVersion" to avbToolVersion,
            "aospHead" to aospHead,
            "fecLoaded" to fecLoaded.toString(),
            "pythonRuntime" to pythonRuntime,
            "pythonVersion" to pythonVersion,
            "seedRowCount" to dbRows.toString(),
            "lastRefreshAtMs" to lastRefreshAtMs.toString(),
        )
    }

    fun build(db: AvbDatabase, runner: AvbToolRunner) {
        database = db
        avbToolRunner = runner
        commandRepository = CommandRepository(db, runner)
    }

    /**
     * Manual refresh entry point for the Home version card.
     *
     * Debounced: returns false if a previous refresh finished less than
     * [REFRESH_DEBOUNCE_MS] ago, so rapid taps are silently ignored.
     *
     * M2.6+ behaviour: bumps [lastRefreshAtMs] and briefly toggles
     * [refreshState] so the UI can show a spinner. Actual re-fetch happens
     * in M3 when [AvbToolRunner.fetchHelp] becomes real.
     */
    fun refresh(): Boolean {
        val now = System.currentTimeMillis()
        if (lastRefreshAtMs != 0L && now - lastRefreshAtMs < REFRESH_DEBOUNCE_MS) return false
        _refreshState.value = true
        lastRefreshAtMs = now
        _refreshState.value = false
        return true
    }
}
