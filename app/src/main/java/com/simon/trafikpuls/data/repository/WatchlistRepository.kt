package com.simon.trafikpuls.data.repository

import com.simon.trafikpuls.data.demo.DemoNetwork
import com.simon.trafikpuls.data.local.WatchedStopDao
import com.simon.trafikpuls.data.local.WatchedStopEntity
import com.simon.trafikpuls.domain.model.TransportMode
import com.simon.trafikpuls.domain.model.WatchedStop
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Clock

enum class AddResult { ADDED, ALREADY_WATCHED, LIMIT_REACHED }

/**
 * The stops being monitored. The list is capped at [MAX_WATCHED] so that refreshing all of
 * them once a minute stays well inside the Trafiklab Bronze quota (25 calls/minute).
 */
class WatchlistRepository(
    private val dao: WatchedStopDao,
    demoModeFlow: Flow<Boolean>,
    private val isDemoMode: suspend () -> Boolean,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val watched: Flow<List<WatchedStop>> = demoModeFlow
        .distinctUntilChanged()
        .flatMapLatest { demo -> dao.observe(demo) }
        .map { entities -> entities.map { it.toDomain() } }

    fun isWatched(areaId: String): Flow<Boolean> =
        watched.map { stops -> stops.any { it.areaId == areaId } }.distinctUntilChanged()

    suspend fun current(): List<WatchedStop> = dao.getAll(isDemoMode()).map { it.toDomain() }

    suspend fun add(stop: WatchedStop): AddResult {
        val demo = isDemoMode()
        if (dao.count(demo) >= MAX_WATCHED) return AddResult.LIMIT_REACHED
        val rowId = dao.insert(
            WatchedStopEntity(
                areaId = stop.areaId,
                demo = demo,
                name = stop.name,
                transportModes = stop.transportModes.joinToString(",") { it.name },
                position = dao.maxPosition(demo) + 1,
                addedAtEpochMs = clock.millis(),
            ),
        )
        return if (rowId == -1L) AddResult.ALREADY_WATCHED else AddResult.ADDED
    }

    suspend fun remove(areaId: String) = dao.delete(areaId, isDemoMode())

    /** Big hubs as a starting point; the ids are the national (rikshållplats) ids. */
    suspend fun addSuggestions(): Int {
        val suggestions = if (isDemoMode()) DemoNetwork.defaultWatchlist else LIVE_SUGGESTIONS
        return suggestions.count { add(it) == AddResult.ADDED }
    }

    companion object {
        const val MAX_WATCHED = 8

        val LIVE_SUGGESTIONS = listOf(
            WatchedStop("740000001", "Stockholm Centralstation", setOf(TransportMode.TRAIN, TransportMode.BUS)),
            WatchedStop("740000002", "Göteborg Centralstation", setOf(TransportMode.TRAIN, TransportMode.BUS)),
            WatchedStop("740000003", "Malmö Centralstation", setOf(TransportMode.TRAIN, TransportMode.BUS)),
        )
    }
}

private fun WatchedStopEntity.toDomain() = WatchedStop(
    areaId = areaId,
    name = name,
    transportModes = transportModes.split(',')
        .filter { it.isNotBlank() }
        .map(TransportMode::parse)
        .toSet(),
)
