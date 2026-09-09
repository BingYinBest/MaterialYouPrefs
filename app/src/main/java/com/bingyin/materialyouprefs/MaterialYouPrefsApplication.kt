package com.bingyin.materialyouprefs

import android.util.Log
import com.bingyin.materialyouprefs.data.db.AvbDatabase
import com.bingyin.materialyouprefs.data.repository.AvbToolRunnerImpl
import com.bingyin.materialyouprefs.data.repository.CommandRepository
import com.bingyin.materialyouprefs.data.repository.NoopAvbToolRunner
import com.chaquo.python.android.PyApplication
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
 * IMPORTANT (v1.0.0 hotfix):
 *   - Extends [PyApplication] from `com.chaquo.python.android` (NOT
 *     `com.chaquo.python.PyApplication` -- that path does not exist in
 *     Chaquopy 15; the class lives in the `android` subpackage).
 *   - [PyApplication.onCreate] runs `Python.start(new AndroidPlatform(this))`
 *     for us. Without it, the first avbtool command crashes with
 *     `RuntimeException: Cannot use GenericPlatform on Android` because
 *     the default platform on Android is the JVM GenericPlatform.
 *   - The Chaquopy PyApplication source (verified against 15.0.1):
 *     ```java
 *     package com.chaquo.python.android;
 *     public class PyApplication extends Application {
 *         @Override public void onCreate() {
 *             super.onCreate();
 *             Python.start(new AndroidPlatform(this));
 *         }
 *     }
 *     ```
 *   - Because it extends `android.app.Application`, `this` here is a valid
 *     [android.content.Context] for every call below.
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
                val count = repo.seedFromAssets(this)
                AppState.seedRowCount = count
                Log.i(TAG, "seedFromAssets inserted $count commands")
            } catch (t: Throwable) {
                Log.e(TAG, "seedFromAssets failed", t)
                AppState.seedFailure = t
            }
        }
    }
}
