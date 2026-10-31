package com.simon.trafikpuls.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedStopDao {
    @Query("SELECT * FROM watched_stops WHERE demo = :demo ORDER BY position, addedAtEpochMs")
    fun observe(demo: Boolean): Flow<List<WatchedStopEntity>>

    @Query("SELECT * FROM watched_stops WHERE demo = :demo ORDER BY position, addedAtEpochMs")
    suspend fun getAll(demo: Boolean): List<WatchedStopEntity>

    @Query("SELECT COUNT(*) FROM watched_stops WHERE demo = :demo")
    suspend fun count(demo: Boolean): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM watched_stops WHERE demo = :demo")
    suspend fun maxPosition(demo: Boolean): Int

    /** Returns -1 when the stop is already on the list. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: WatchedStopEntity): Long

    @Query("DELETE FROM watched_stops WHERE areaId = :areaId AND demo = :demo")
    suspend fun delete(areaId: String, demo: Boolean)
}

@Dao
interface BoardCacheDao {
    @Query("SELECT * FROM board_cache WHERE cacheKey = :key")
    suspend fun get(key: String): BoardCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: BoardCacheEntity)

    @Query("DELETE FROM board_cache WHERE fetchedAtEpochMs < :olderThanEpochMs")
    suspend fun deleteOlderThan(olderThanEpochMs: Long): Int
}

@Dao
abstract class LineFilterDao {
    @Query("SELECT * FROM line_filters WHERE demo = :demo")
    abstract fun observe(demo: Boolean): Flow<List<LineFilterEntity>>

    @Query("DELETE FROM line_filters WHERE areaId = :areaId AND demo = :demo")
    protected abstract suspend fun clear(areaId: String, demo: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(filters: List<LineFilterEntity>)

    /** Replaces the whole selection for a stop in one transaction. */
    @Transaction
    open suspend fun replace(areaId: String, demo: Boolean, lineKeys: Set<String>) {
        clear(areaId, demo)
        insertAll(lineKeys.map { LineFilterEntity(areaId, demo, it) })
    }
}
