package com.simon.trafikpuls.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simon.trafikpuls.data.demo.DemoNetwork

@Database(
    entities = [WatchedStopEntity::class, BoardCacheEntity::class, LineFilterEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = TrafikpulsDatabase.Migration1To2::class)],
)
abstract class TrafikpulsDatabase : RoomDatabase() {
    abstract fun watchedStopDao(): WatchedStopDao
    abstract fun boardCacheDao(): BoardCacheDao
    abstract fun lineFilterDao(): LineFilterDao

    /** v2: background notifications removed (their table goes), per-stop line filters added. */
    @DeleteTable(tableName = "notified_events")
    class Migration1To2 : AutoMigrationSpec

    companion object {
        fun build(context: Context): TrafikpulsDatabase =
            Room.databaseBuilder(context, TrafikpulsDatabase::class.java, "trafikpuls.db")
                .addCallback(SeedDemoWatchlist)
                .build()
    }

    /** Demo mode starts with a ready-made watchlist; seeded once, so the user can still clear it. */
    private object SeedDemoWatchlist : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            DemoNetwork.defaultWatchlist.forEachIndexed { index, stop ->
                val values = ContentValues().apply {
                    put("areaId", stop.areaId)
                    put("demo", 1)
                    put("name", stop.name)
                    put("transportModes", stop.transportModes.joinToString(",") { it.name })
                    put("position", index)
                    put("addedAtEpochMs", now)
                }
                db.insert("watched_stops", SQLiteDatabase.CONFLICT_IGNORE, values)
            }
        }
    }
}
