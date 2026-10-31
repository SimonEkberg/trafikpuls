package com.simon.trafikpuls.data.repository

import com.simon.trafikpuls.core.AppError
import com.simon.trafikpuls.core.DataOrigin
import com.simon.trafikpuls.core.ErrorMapper
import com.simon.trafikpuls.core.FetchMeta
import com.simon.trafikpuls.core.Outcome
import com.simon.trafikpuls.core.RetryPolicy
import com.simon.trafikpuls.core.TtlCache
import com.simon.trafikpuls.core.nowInSweden
import com.simon.trafikpuls.core.retryTransient
import com.simon.trafikpuls.data.local.BoardCacheDao
import com.simon.trafikpuls.data.local.BoardCacheEntity
import com.simon.trafikpuls.data.mapper.toDomain
import com.simon.trafikpuls.data.mapper.toStopBoard
import com.simon.trafikpuls.data.mapper.toTripDetails
import com.simon.trafikpuls.data.remote.dto.BoardResponseDto
import com.simon.trafikpuls.data.source.TrafiklabDataSource
import com.simon.trafikpuls.domain.model.BoardKind
import com.simon.trafikpuls.domain.model.StopBoard
import com.simon.trafikpuls.domain.model.StopGroup
import com.simon.trafikpuls.domain.model.TimeWindow
import com.simon.trafikpuls.domain.model.TripDetails
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Single entry point for traffic data. Responsibilities, in order:
 * 1. Demo mode short-circuits to the offline source.
 * 2. Fresh cache (< [FRESH_TTL]) is served without a request - Trafiklab caches boards for 60 s
 *    anyway, so polling faster only burns quota.
 * 3. Transient failures are retried with backoff. Every attempt passes the remote source's
 *    client-side rate limiter, which ends the retries when the quota guard is exhausted.
 * 4. If everything fails, the last good response (< [STALE_MAX_AGE]) is served, flagged as stale.
 */
class TrafficRepository(
    private val remote: TrafiklabDataSource,
    private val demo: TrafiklabDataSource,
    private val isDemoMode: suspend () -> Boolean,
    private val boardCache: BoardCacheDao,
    private val json: Json,
    private val clock: Clock,
    private val retryPolicy: RetryPolicy = RetryPolicy.Default,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val tripCache = TtlCache<String, TripDetails>(maxEntries = 20, ttl = TRIP_TTL, clock = clock)
    private val searchCache = TtlCache<String, List<StopGroup>>(maxEntries = 30, ttl = SEARCH_TTL, clock = clock)

    suspend fun board(
        areaId: String,
        kind: BoardKind = BoardKind.DEPARTURES,
        window: TimeWindow = TimeWindow.UPCOMING,
        nameHint: String? = null,
    ): Outcome<StopBoard> {
        val toBoard: (BoardResponseDto) -> StopBoard = { it.toStopBoard(areaId, kind, window, nameHint) }
        if (isDemoMode()) return demoCall { toBoard(demo.board(kind, areaId, window.queryStart())) }

        val key = "${kind.name}:${window.name}:$areaId"
        // One request per board at a time: concurrent callers (dashboard + background monitor)
        // wait and then get the freshly cached result instead of firing duplicate requests.
        return locks.getOrPut(key) { Mutex() }.withLock {
            val cached = runCatching { boardCache.get(key) }.getOrNull()
            val cachedDto = cached?.let(::decode)
            if (cached != null && cachedDto != null && age(cached) < FRESH_TTL.toMillis()) {
                return@withLock Outcome.Success(
                    toBoard(cachedDto),
                    FetchMeta(DataOrigin.CACHE, Instant.ofEpochMilli(cached.fetchedAtEpochMs)),
                )
            }
            try {
                val dto = retryTransient(retryPolicy) { remote.board(kind, areaId, window.queryStart()) }
                val fetchedAt = clock.millis()
                runCatching { boardCache.put(BoardCacheEntity(key, json.encodeToString(BoardResponseDto.serializer(), dto), fetchedAt)) }
                Outcome.Success(toBoard(dto), FetchMeta(DataOrigin.NETWORK, Instant.ofEpochMilli(fetchedAt)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fallback(cached, cachedDto, ErrorMapper.map(e), toBoard)
            }
        }
    }

    suspend fun trip(tripId: String, startDate: LocalDate): Outcome<TripDetails> {
        if (isDemoMode()) return demoCall { demo.trip(tripId, startDate).toTripDetails(tripId, startDate) }

        val key = "$tripId|$startDate"
        tripCache.get(key)?.let { (details, storedAt) ->
            return Outcome.Success(details, FetchMeta(DataOrigin.CACHE, storedAt))
        }
        return networkCall {
            retryTransient(retryPolicy) { remote.trip(tripId, startDate) }
                .toTripDetails(tripId, startDate)
                .also { tripCache.put(key, it) }
        }
    }

    suspend fun searchStops(query: String): Outcome<List<StopGroup>> {
        val normalized = query.trim()
        if (isDemoMode()) return demoCall { demo.searchStops(normalized).stopGroups.map { it.toDomain() } }

        val key = normalized.lowercase()
        searchCache.get(key)?.let { (groups, storedAt) ->
            return Outcome.Success(groups, FetchMeta(DataOrigin.CACHE, storedAt))
        }
        return networkCall {
            retryTransient(retryPolicy) { remote.searchStops(normalized) }
                .stopGroups
                .map { it.toDomain() }
                .also { searchCache.put(key, it) }
        }
    }

    /** Housekeeping: drops cached boards too old to ever be served. */
    suspend fun pruneCache() {
        runCatching { boardCache.deleteOlderThan(clock.millis() - STALE_MAX_AGE.toMillis()) }
    }

    fun clearMemoryCaches() {
        tripCache.clear()
        searchCache.clear()
    }

    private fun TimeWindow.queryStart(): LocalDateTime? = when (this) {
        TimeWindow.UPCOMING -> null
        TimeWindow.PAST_HOUR -> clock.nowInSweden().minusMinutes(60).truncatedTo(ChronoUnit.MINUTES)
    }

    private fun age(entity: BoardCacheEntity): Long = clock.millis() - entity.fetchedAtEpochMs

    private fun decode(entity: BoardCacheEntity): BoardResponseDto? =
        runCatching { json.decodeFromString(BoardResponseDto.serializer(), entity.json) }.getOrNull()

    private fun fallback(
        cached: BoardCacheEntity?,
        cachedDto: BoardResponseDto?,
        error: AppError,
        toBoard: (BoardResponseDto) -> StopBoard,
    ): Outcome<StopBoard> {
        if (cached != null && cachedDto != null && age(cached) <= STALE_MAX_AGE.toMillis()) {
            return Outcome.Success(
                toBoard(cachedDto),
                FetchMeta(DataOrigin.CACHE, Instant.ofEpochMilli(cached.fetchedAtEpochMs), staleReason = error),
            )
        }
        return Outcome.Failure(error)
    }

    private suspend fun <T> networkCall(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block(), FetchMeta(DataOrigin.NETWORK, clock.instant()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(ErrorMapper.map(e))
    }

    private suspend fun <T> demoCall(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block(), FetchMeta(DataOrigin.DEMO, clock.instant()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(ErrorMapper.map(e))
    }

    companion object {
        val FRESH_TTL: Duration = Duration.ofSeconds(30)
        /** Boards only cover the next hour, so older fallback data would be misleading. */
        val STALE_MAX_AGE: Duration = Duration.ofMinutes(30)
        val TRIP_TTL: Duration = Duration.ofSeconds(30)
        val SEARCH_TTL: Duration = Duration.ofMinutes(10)
    }
}
