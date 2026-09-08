package com.bingyin.materialyouprefs

import com.bingyin.materialyouprefs.data.db.AvbDatabase
import com.bingyin.materialyouprefs.data.repository.AvbToolRunner
import com.bingyin.materialyouprefs.data.repository.CommandRepository

/**
 * Manual singletons holder. See ADR-010: we keep `@Inject` / `@Singleton`
 * annotations as metadata-only (documentation of intent) and wire the
 * graph by hand from [MaterialYouPrefsApplication.onCreate].
 *
 * Using a DI framework (Hilt / Koin) would add significant overhead at
 * M1/M2; the graph is small enough that a static holder is clearer.
 *
 * Access is synchronized where possible to guard against a rare race
 * between Application.onCreate and the first screen composition.
 */
object AppState {

    @Volatile var database: AvbDatabase? = null
    @Volatile var avbToolRunner: AvbToolRunner? = null
    @Volatile var commandRepository: CommandRepository? = null

    /** Last seedFromAssets failure, if any. Null on success. */
    @Volatile var seedFailure: Throwable? = null

    /** Number of rows inserted during seedFromAssets; -1 if seed skipped. */
    @Volatile var seedRowCount: Int = -1

    fun build(db: AvbDatabase, runner: AvbToolRunner) {
        database = db
        avbToolRunner = runner
        commandRepository = CommandRepository(db, runner)
    }
}
