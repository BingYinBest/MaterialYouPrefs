package com.bingyin.materialyouprefs

import android.app.Application
import android.util.Log
import com.bingyin.materialyouprefs.data.db.AvbDatabase
import com.bingyin.materialyouprefs.data.repository.AvbToolRunnerImpl
import com.bingyin.materialyouprefs.data.repository.CommandRepository
import com.bingyin.materialyouprefs.data.repository.NoopAvbToolRunner
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
 */
class MaterialYouPrefsApplication : Application() {

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
        super.onCreate()

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
