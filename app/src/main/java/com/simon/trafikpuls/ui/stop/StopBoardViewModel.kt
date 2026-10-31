package com.simon.trafikpuls.ui.stop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simon.trafikpuls.core.AppError
import com.simon.trafikpuls.core.FetchMeta
import com.simon.trafikpuls.core.Outcome
import com.simon.trafikpuls.data.repository.AddResult
import com.simon.trafikpuls.data.repository.LineFilterRepository
import com.simon.trafikpuls.data.repository.TrafficRepository
import com.simon.trafikpuls.data.repository.WatchlistRepository
import com.simon.trafikpuls.data.settings.SettingsRepository
import com.simon.trafikpuls.domain.BoardKpis
import com.simon.trafikpuls.domain.HealthStatus
import com.simon.trafikpuls.domain.LineOption
import com.simon.trafikpuls.domain.LineOptions
import com.simon.trafikpuls.domain.OperatorOption
import com.simon.trafikpuls.domain.OperatorOptions
import com.simon.trafikpuls.domain.PunctualityPolicy
import com.simon.trafikpuls.domain.filterLines
import com.simon.trafikpuls.domain.filterOperator
import com.simon.trafikpuls.domain.model.Alert
import com.simon.trafikpuls.domain.model.BoardKind
import com.simon.trafikpuls.domain.model.StopBoard
import com.simon.trafikpuls.domain.model.TimeWindow
import com.simon.trafikpuls.domain.model.TimetableEntry
import com.simon.trafikpuls.domain.model.WatchedStop
import com.simon.trafikpuls.ui.appViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration

data class BoardQuery(
    val kind: BoardKind = BoardKind.DEPARTURES,
    val window: TimeWindow = TimeWindow.UPCOMING,
)

data class StopBoardUiState(
    val areaId: String,
    val name: String,
    val query: BoardQuery = BoardQuery(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasBoard: Boolean = false,
    val meta: FetchMeta? = null,
    val error: AppError? = null,
    val entries: List<TimetableEntry> = emptyList(),
    val kpis: BoardKpis = BoardKpis.Empty,
    val health: HealthStatus = HealthStatus.UNKNOWN,
    val stopAlerts: List<Alert> = emptyList(),
    val operatorOptions: List<OperatorOption> = emptyList(),
    val selectedOperator: String? = null,
    /** All lines/directions at the stop, with the user's choice marked. */
    val lineOptions: List<LineOption> = emptyList(),
    /** Followed line keys; empty = all lines are shown. */
    val lineFilter: Set<String> = emptySet(),
    val isWatched: Boolean = false,
    val isDemo: Boolean = false,
    val policy: PunctualityPolicy = PunctualityPolicy(),
    val message: String? = null,
)

class StopBoardViewModel(
    private val areaId: String,
    private val name: String,
    private val traffic: TrafficRepository,
    private val watchlist: WatchlistRepository,
    private val settings: SettingsRepository,
    private val lineFilters: LineFilterRepository,
    private val clock: Clock,
) : ViewModel() {

    private data class LoadState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val board: StopBoard? = null,
        val meta: FetchMeta? = null,
        val error: AppError? = null,
    )

    private val query = MutableStateFlow(BoardQuery())
    private val load = MutableStateFlow(LoadState())
    private val message = MutableStateFlow<String?>(null)
    private var autoRefreshJob: Job? = null
    private var fetchJob: Job? = null
    private var fetchGeneration = 0

    val uiState: StateFlow<StopBoardUiState> = combine(
        load,
        combine(settings.settings, lineFilters.filterFor(areaId), ::Pair),
        watchlist.isWatched(areaId),
        query,
        message,
    ) { loadState, (appSettings, lineFilter), isWatched, currentQuery, currentMessage ->
        val policy = appSettings.policy
        val board = loadState.board
        val entries = board?.entries.orEmpty()
            .filterLines(lineFilter)
            .filterOperator(appSettings.operatorFilter)
        val stopAlerts = board?.stopAlerts.orEmpty()
        val kpis = BoardKpis.of(
            entries,
            policy,
            alertCount = (stopAlerts + entries.flatMap { it.alerts }).distinctBy { it.dedupeKey }.size,
        )
        StopBoardUiState(
            areaId = areaId,
            name = name,
            query = currentQuery,
            isLoading = loadState.isLoading,
            isRefreshing = loadState.isRefreshing,
            hasBoard = board != null,
            meta = loadState.meta,
            error = loadState.error,
            entries = entries,
            kpis = kpis,
            health = if (board == null) HealthStatus.UNKNOWN else policy.health(kpis),
            stopAlerts = stopAlerts,
            operatorOptions = OperatorOptions.from(board?.entries.orEmpty()),
            selectedOperator = appSettings.operatorFilter,
            lineOptions = LineOptions.from(board?.entries.orEmpty(), lineFilter),
            lineFilter = lineFilter,
            isWatched = isWatched,
            isDemo = appSettings.demoMode,
            policy = policy,
            message = currentMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StopBoardUiState(areaId, name))

    init {
        viewModelScope.launch {
            query.collect { launchFetch(showSpinner = true) }
        }
    }

    fun setKind(kind: BoardKind) = query.update { it.copy(kind = kind) }

    fun setWindow(window: TimeWindow) = query.update { it.copy(window = window) }

    fun refresh() = launchFetch(showSpinner = false)

    fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            // Back on the screen with old data (or an error, e.g. a key fixed in Settings): fetch now.
            if (fetchJob?.isActive != true && isOutdated()) launchFetch(showSpinner = false)
            while (isActive) {
                delay(AUTO_REFRESH_MILLIS)
                // The past hour does not change - only the upcoming window is worth polling.
                if (query.value.window == TimeWindow.UPCOMING) launchFetch(showSpinner = false)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    /** Saved per stop and applied everywhere the stop appears (overview, disruptions). */
    fun setLineFilter(lineKeys: Set<String>) {
        viewModelScope.launch { lineFilters.setFilter(areaId, lineKeys) }
    }

    fun selectOperator(operator: String?) {
        viewModelScope.launch { settings.setOperatorFilter(operator) }
    }

    fun toggleWatch() {
        viewModelScope.launch {
            if (uiState.value.isWatched) {
                watchlist.remove(areaId)
                message.value = "$name bevakas inte längre"
            } else {
                val modes = load.value.board?.entries?.map { it.line.mode }?.toSet().orEmpty()
                message.value = when (watchlist.add(WatchedStop(areaId, name, modes))) {
                    AddResult.ADDED -> "$name bevakas nu i översikten"
                    AddResult.ALREADY_WATCHED -> "$name bevakas redan"
                    AddResult.LIMIT_REACHED -> "Du kan bevaka högst ${WatchlistRepository.MAX_WATCHED} hållplatser"
                }
            }
        }
    }

    fun messageShown() {
        message.value = null
    }

    private fun isOutdated(): Boolean {
        val meta = load.value.meta ?: return true
        return Duration.between(meta.fetchedAt, clock.instant()).toMillis() >= AUTO_REFRESH_MILLIS
    }

    /**
     * Starts a fetch for the current query. Only the latest request may write state: a
     * superseded one (new tab, newer refresh) is cancelled and its late result ignored, so a slow
     * "next hour" response can never overwrite the "past hour" the user switched to.
     */
    private fun launchFetch(showSpinner: Boolean) {
        fetchJob?.cancel()
        val generation = ++fetchGeneration
        val requested = query.value
        fetchJob = viewModelScope.launch {
            load.update {
                if (showSpinner) LoadState(isLoading = true) else it.copy(isRefreshing = true)
            }
            try {
                val outcome = traffic.board(areaId, requested.kind, requested.window, nameHint = name)
                if (generation != fetchGeneration) return@launch
                load.update { previous ->
                    when (outcome) {
                        is Outcome.Success -> LoadState(
                            isLoading = false,
                            board = outcome.value,
                            meta = outcome.meta,
                            error = outcome.meta.staleReason,
                        )
                        // Keep what is on screen and say why it could not be updated.
                        is Outcome.Failure -> previous.copy(isLoading = false, isRefreshing = false, error = outcome.error)
                    }
                }
            } finally {
                // Also on cancellation: the request that owns the state never leaves a spinner behind.
                if (generation == fetchGeneration) load.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    companion object {
        private const val AUTO_REFRESH_MILLIS = 60_000L

        fun factory(areaId: String, name: String) = appViewModelFactory { container ->
            StopBoardViewModel(
                areaId = areaId,
                name = name,
                traffic = container.trafficRepository,
                watchlist = container.watchlistRepository,
                settings = container.settingsRepository,
                lineFilters = container.lineFilterRepository,
                clock = container.clock,
            )
        }
    }
}
