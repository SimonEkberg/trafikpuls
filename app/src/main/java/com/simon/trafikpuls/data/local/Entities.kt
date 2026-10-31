package com.simon.trafikpuls.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A stop on the user's watchlist. Demo mode keeps its own list ([demo] = true) so switching
 * between demo and live data never mixes fictional stops into real monitoring.
 */
@Entity(tableName = "watched_stops", primaryKeys = ["areaId", "demo"])
data class WatchedStopEntity(
    val areaId: String,
    val demo: Boolean,
    val name: String,
    /** Comma-separated [com.simon.trafikpuls.domain.model.TransportMode] names. */
    val transportModes: String,
    val position: Int,
    val addedAtEpochMs: Long,
)

/** Last successful board response per stop/kind/window, as raw JSON, for offline fallback. */
@Entity(tableName = "board_cache")
data class BoardCacheEntity(
    @PrimaryKey val cacheKey: String,
    val json: String,
    val fetchedAtEpochMs: Long,
)

/**
 * A line (in one direction) the user wants to see at a stop. No rows for a stop = show all lines.
 * Kept per stop and per demo/live like the watchlist; see [com.simon.trafikpuls.domain.lineKey].
 */
@Entity(tableName = "line_filters", primaryKeys = ["areaId", "demo", "lineKey"])
data class LineFilterEntity(
    val areaId: String,
    val demo: Boolean,
    val lineKey: String,
)
