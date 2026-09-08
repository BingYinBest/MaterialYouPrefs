package com.bingyin.materialyouprefs.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for avbtool commands and their execution history.
 *
 * Schema version 1 is the initial M2 schema. Bump [version] and add
 * a [Migration] whenever a column/table changes.
 */
@Database(
    entities = [CommandEntity::class, ExecutionEntity::class],
    version = 1,
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
                .build()

        /** Test hook. */
        fun resetInstance() = synchronized(this) { INSTANCE = null }
    }
}
