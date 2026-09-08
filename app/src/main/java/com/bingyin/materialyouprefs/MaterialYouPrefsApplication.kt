package com.bingyin.materialyouprefs

import android.app.Application
import android.util.Log
import com.bingyin.materialyouprefs.data.db.AvbDatabase
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
 * 2. Kick off [CommandRepository.seedFromAssets] in a background scope
 *    (it's a suspend function that reads assets + writes to Room).
 * 3. Never crash the app on data-layer errors: seed failure is logged
 *    and stashed on [AppState.seedFailure]; the UI falls back to
 *    `PrefData` until M4.
 */
class MaterialYouPrefsApplication : Application() {

    companion object {
        private const val TAG = "MaterialYouPrefsApp"
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

        // M2.5: Noop runner. M3 replaces this with the Chaquopy-backed
        // implementation once avbtool.py + libavbfec.so are bundled.
        val runner = NoopAvbToolRunner()
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
