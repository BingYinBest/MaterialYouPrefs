package com.bingyin.materialyouprefs

import android.util.Log
import com.bingyin.materialyouprefs.data.db.AvbDatabase
import com.bingyin.materialyouprefs.data.repository.AvbToolRunnerImpl
import com.bingyin.materialyouprefs.data.repository.CommandRepository
import com.bingyin.materialyouprefs.data.repository.NoopAvbToolRunner
import com.chaquo.python.PyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application bootstrap. Registered in AndroidManifest via
 * `android:name=".MaterialYouPrefsApplication"`.
 *
 * Responsibilities:
 * 1. Build the manual DI graph into [AppState].
 * 2. Kick off [CommandRepository.seedFromAssets] in a background scope.
 * 3. Never crash the app on data-layer errors: seed failure is logged
 *    and stashed on [AppState.seedFailure].
 *
 * IMPORTANT (v1.0.0 hotfix): extends [PyApplication], NOT android.app.Application.
 * Chaquopy 15 requires the Python runtime to be initialised with an
 * `AndroidPlatform` on Android (which needs a Context). [PyApplication] does
 * that automatically in its own `onCreate` before our override runs -- so by
 * the time [AvbToolRunnerImpl.ensureInitialized] calls `Python.getInstance()`
 * (which happens on a worker thread via `withContext(Dispatchers.IO)`), the
 * runtime is already live. Extending plain `Application` used to crash with
 * "RuntimeException: Cannot use GenericPlatform on Android" on the first
 * avbtool command.
 */
class MaterialYouPrefsApplication : PyApplication() {

    companion object {
        private const val TAG = "MaterialYouPrefsApp"

        /**
         * Flip to `false` if the Chaquopy build fails to load on some
         * devices and we need to fall back to the Noop runner.
         *
         * M3.1+ default: true (Chaquopy is wired).
         */
        const val USE_CHAQUOPY_RUNNER: Boolean = true
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        // PyApplication.onCreate() initialises Python with AndroidPlatform(this)
        // before returning to us. Any failure there is fatal for the Chaquopy
        // runner -- we wrap it in a try and fall back to Noop so the rest of
        // the UI can still come up.
        try {
            super.onCreate()
        } catch (t: Throwable) {
            Log.e(TAG, "PyApplication.onCreate failed -- Chaquopy unavailable", t)
            AppState.seedFailure = t
        }

        val db = try {
            AvbDatabase.getInstance(this)
        } catch (t: Throwable) {
            Log.e(TAG, "AvbDatabase.getInstance failed", t)
            AppState.seedFailure = t
            return
        }

        val runner = if (USE_CHAQUOPY_RUNNER) {
            try {
                AvbToolRunnerImpl(this)
            } catch (t: Throwable) {
                Log.w(TAG, "AvbToolRunnerImpl init failed, falling back to Noop", t)
                NoopAvbToolRunner()
            }
        } else {
            NoopAvbToolRunner()
        }
        AppState.build(db, runner)

        appScope.launch {
            val repo = AppState.commandRepository ?: return@launch
            try {
                val count = repo.seedFromAssets(this@MaterialYouPrefsApplication)
                AppState.seedRowCount = count
                Log.i(TAG, "seedFromAssets inserted $count commands")
            } catch (t: Throwable) {
                Log.e(TAG, "seedFromAssets failed", t)
                AppState.seedFailure = t
            }
        }
    }
}
