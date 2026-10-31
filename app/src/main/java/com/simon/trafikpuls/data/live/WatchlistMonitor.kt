package com.simon.trafikpuls.data.live

import com.simon.trafikpuls.core.AppError
import com.simon.trafikpuls.core.FetchMeta
import com.simon.trafikpuls.core.Outcome
import com.simon.trafikpuls.data.repository.TrafficRepository
import com.simon.trafikpuls.data.repository.WatchlistRepository
import com.simon.trafikpuls.data.settings.AppSettings
import com.simon.trafikpuls.domain.filterLines
import com.simon.trafikpuls.domain.model.StopBoard
import com.simon.trafikpuls.domain.model.WatchedStop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Clock
import java.time.Instant

data class StopSnapshot(
    val stop: WatchedStop,
    /** Latest board, already narrowed to [lineFilter] when the user has chosen lines. */
    val board: StopBoard?,
    val meta: FetchMeta?,
    /** Latest error, also set when [board] is a stale fallback. */
    val error: AppError?,
    /** Line keys the user follows at this stop; empty = all lines. */
    val lineFilter: Set<String> = emptySet(),
) {
    val isLoading: Boolean get() = board == null && error == null
}

data class WatchlistSnapshot(
    val stops: List<StopSnapshot> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastRefreshAt: Instant? = null,
    /** False until the watchlist has been read once - avoids flashing an "empty" state. */
    val isInitialized: Boolean = false,
)

/**
 * App-wide live state for the watched stops, shared by the overview and the alerts screen so
 * both show the same numbers from the same requests. Refreshes are serialized and fan out with
 * bounded parallelism.
 */
@OptIn(FlowPreview::class)
class WatchlistMonitor(
    private val watchlist: WatchlistRepository,
    private val traffic: TrafficRepository,
    settings: Flow<AppSettings>,
    lineFilters: Flow<Map<String, Set<String>>>,
    scope: CoroutineScope,
    private val clock: Clock,
) {
    private data class StopResult(val board: StopBoard?, val meta: FetchMeta?, val error: AppError?)

    private val results = MutableStateFlow<Map<String, StopResult>>(emptyMap())
    private val refreshing = MutableStateFlow(false)
    private val lastRefreshAt = MutableStateFlow<Instant?>(null)
    private val refreshMutex = Mutex()
    private val parallelism = Semaphore(MAX_PARALLEL_REQUESTS)

    val snapshot: StateFlow<WatchlistSnapshot> =
        combine(watchlist.watched, results, refreshing, lastRefreshAt, lineFilters) { stops, byStop, isRefreshing, last, filters ->
            WatchlistSnapshot(
                stops = stops.map { stop ->
                    val result = byStop[stop.areaId]
                    val lineFilter = filters[stop.areaId].orEmpty()
                    // Filtering here means overview KPIs and disruptions only count the chosen lines.
                    val board = result?.board?.let { it.copy(entries = it.entries.filterLines(lineFilter)) }
                    StopSnapshot(stop, board, result?.meta, result?.error, lineFilter)
                },
                isRefreshing = isRefreshing,
                lastRefreshAt = last,
                isInitialized = true,
            )
        }.stateIn(scope, SharingStarted.Eagerly, WatchlistSnapshot())

    init {
        // A stop was added or removed: fetch only the new stops. Debounced so that adding
        // several stops in a row costs one round of requests, not one per change.
        scope.launch {
            watchlist.watched
                .map { stops -> stops.map { it.areaId }.toSet() }
                .distinctUntilChanged()
                .drop(1)
                .debounce(WATCHLIST_DEBOUNCE_MILLIS)
                .collect { refresh(waitForRunning = true, onlyMissing = true) }
        }
        // The data source changed (demo <-> live, new key): everything shown so far is invalid.
        scope.launch {
            settings
                .map { it.demoMode to it.apiKeyOverride }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    traffic.clearMemoryCaches()
                    refresh(waitForRunning = true, reset = true)
                }
        }
    }

    /**
     * Refreshes all watched stops. Unless [waitForRunning], a call made while a refresh is
     * already in flight returns false immediately - pull-to-refresh and the auto-refresh
     * timer cannot pile up requests.
     */
    suspend fun refresh(
        waitForRunning: Boolean = false,
        reset: Boolean = false,
        onlyMissing: Boolean = false,
    ): Boolean {
        if (waitForRunning) refreshMutex.lock() else if (!refreshMutex.tryLock()) return false
        try {
            if (reset) results.value = emptyMap()
            refreshing.value = true
            val stops = watchlist.current()
            val toFetch = if (onlyMissing) stops.filter { it.areaId !in results.value } else stops
            val fetched = coroutineScope {
                toFetch.map { stop ->
                    async {
                        parallelism.withPermit {
                            stop.areaId to traffic.board(stop.areaId, nameHint = stop.name)
                        }
                    }
                }.awaitAll()
            }
            results.update { previous ->
                val watchedIds = stops.map { it.areaId }.toSet()
                val updated = previous.filterKeys { it in watchedIds }.toMutableMap()
                for ((areaId, outcome) in fetched) {
                    updated[areaId] = when (outcome) {
                        is Outcome.Success -> StopResult(outcome.value, outcome.meta, outcome.meta.staleReason)
                        // The repository already fell back to every board young enough to show
                        // (flagged as stale). A failure means nothing trustworthy is left, so an
                        // older in-memory board must not linger as if it were current.
                        is Outcome.Failure -> StopResult(null, null, outcome.error)
                    }
                }
                updated
            }
            if (fetched.any { (_, outcome) -> outcome is Outcome.Success }) {
                lastRefreshAt.value = clock.instant()
            }
            return true
        } finally {
            refreshing.value = false
            refreshMutex.unlock()
        }
    }

    private companion object {
        const val MAX_PARALLEL_REQUESTS = 4
        const val WATCHLIST_DEBOUNCE_MILLIS = 400L
    }
}
