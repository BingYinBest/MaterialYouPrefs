package com.bingyin.materialyouprefs.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for avbtool commands and their execution history.
 *
 * Version history:
 *   - v1 (M2): initial schema — commands, execution_history
 *   - v2 (M2.6+): added `commands.tab` column so Home/Feature/Settings can filter
 *     commands by tab. This is a dev-phase change; we let Room fall back to a
 *     destructive migration (drop + recreate) rather than hand-writing a
 *     Migration object. `seedFromAssets` repopulates on next launch.
 *
 * Once we ship v1.0 to users we will switch to explicit migrations and use
 * the Room SchemaExporter to publish them.
 */
@Database(
    entities = [CommandEntity::class, ExecutionEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AvbDatabase : RoomDatabase() {
    abstract fun commandDao(): CommandDao
    abstract fun executionDao(): ExecutionDao

    companion object {
        private const val DB_NAME = "avbtool.db"

        @Volatile
        private var INSTANCE: AvbDatabase? = null

        fun getInstance(context: Context): AvbDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }

        private fun build(context: Context) =
            Room.databaseBuilder(context, AvbDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration()
                .build()

        /** Test hook. */
        fun resetInstance() = synchronized(this) { INSTANCE = null }
    }
}
