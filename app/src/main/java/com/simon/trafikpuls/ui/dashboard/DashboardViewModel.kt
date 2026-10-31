package com.simon.trafikpuls.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simon.trafikpuls.core.AppError
import com.simon.trafikpuls.data.live.WatchlistMonitor
import com.simon.trafikpuls.data.live.WatchlistSnapshot
import com.simon.trafikpuls.data.repository.WatchlistRepository
import com.simon.trafikpuls.data.settings.AppSettings
import com.simon.trafikpuls.data.settings.SettingsRepository
import com.simon.trafikpuls.domain.BoardKpis
import com.simon.trafikpuls.domain.DepartureStatus
import com.simon.trafikpuls.domain.HealthStatus
import com.simon.trafikpuls.domain.OperatorOption
import com.simon.trafikpuls.domain.OperatorOptions
import com.simon.trafikpuls.domain.PunctualityPolicy
import com.simon.trafikpuls.domain.filterOperator
import com.simon.trafikpuls.domain.model.TimetableEntry
import com.simon.trafikpuls.domain.model.TransportMode
import com.simon.trafikpuls.ui.appViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.abs

data class StopCardUi(
    val areaId: String,
    val name: String,
    val modes: Set<TransportMode>,
    val kpis: BoardKpis,
    val health: HealthStatus,
    val alertCount: Int,
    val hasData: Boolean,
    val isLoading: Boolean,
    val error: AppError?,
    val staleSince: Instant?,
    /** Number of followed lines; 0 = all lines. */
    val followedLines: Int = 0,
)

data class IssueUi(
    val entry: TimetableEntry,
    val status: DepartureStatus,
    val stopName: String,
)

data class DashboardUiState(
    val isInitialized: Boolean = false,
    /** Stops are being fetched for the first time - nothing meaningful to aggregate yet. */
    val isFirstLoad: Boolean = false,
    val isRefreshing: Boolean = false,
    val isDemo: Boolean = false,
    val needsApiKey: Boolean = false,
    val watchlistEmpty: Boolean = false,
    val watchedCount: Int = 0,
    val operatorOptions: List<OperatorOption> = emptyList(),
    val selectedOperator: String? = null,
    val overall: BoardKpis = BoardKpis.Empty,
    val overallHealth: HealthStatus = HealthStatus.UNKNOWN,
    val stops: List<StopCardUi> = emptyList(),
    val issues: List<IssueUi> = emptyList(),
    val lastUpdated: Instant? = null,
    /** Set when every stop failed with the same error - shown instead of empty cards. */
    val globalError: AppError? = null,
    val staleCount: Int = 0,
    val policy: PunctualityPolicy = PunctualityPolicy(),
)

class DashboardViewModel(
    private val monitor: WatchlistMonitor,
    private val settings: SettingsRepository,
    private val watchlist: WatchlistRepository,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(monitor.snapshot, settings.settings) { snapshot, appSettings ->
        buildDashboardState(snapshot, appSettings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private var autoRefreshJob: Job? = null

    /** Polls while the screen is visible; Trafiklab caches boards for 60 s, so faster is pointless. */
    fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                monitor.refresh()
                delay(AUTO_REFRESH_MILLIS)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun refresh() {
        viewModelScope.launch { monitor.refresh() }
    }

    fun selectOperator(operator: String?) {
        viewModelScope.launch { settings.setOperatorFilter(operator) }
    }

    fun addSuggestions() {
        viewModelScope.launch { watchlist.addSuggestions() }
    }

    fun removeStop(areaId: String) {
        viewModelScope.launch { watchlist.remove(areaId) }
    }

    fun enableDemoMode() {
        viewModelScope.launch { settings.setDemoMode(true) }
    }

    companion object {
        const val AUTO_REFRESH_MILLIS = 60_000L
        private const val MAX_ISSUES = 8

        val Factory = appViewModelFactory { container ->
            DashboardViewModel(container.watchlistMonitor, container.settingsRepository, container.watchlistRepository)
        }

        /** Pure state reduction - unit-tested without Android. */
        fun buildDashboardState(snapshot: WatchlistSnapshot, settings: AppSettings): DashboardUiState {
            val policy = settings.policy
            val filter = settings.operatorFilter
            val allEntries = snapshot.stops.flatMap { it.board?.entries.orEmpty() }

            val cards = snapshot.stops.map { stopSnapshot ->
                val board = stopSnapshot.board
                val entries = board?.entries.orEmpty().filterOperator(filter)
                val alertCount = (board?.stopAlerts.orEmpty() + entries.flatMap { it.alerts })
                    .distinctBy { it.dedupeKey }
                    .size
                val kpis = BoardKpis.of(entries, policy, alertCount)
                StopCardUi(
                    areaId = stopSnapshot.stop.areaId,
                    name = stopSnapshot.stop.name,
                    modes = board?.entries?.map { it.line.mode }?.toSet()?.takeIf { it.isNotEmpty() }
                        ?: stopSnapshot.stop.transportModes,
                    kpis = kpis,
                    health = if (board == null) HealthStatus.UNKNOWN else policy.health(kpis),
                    alertCount = alertCount,
                    hasData = board != null,
                    isLoading = stopSnapshot.isLoading,
                    error = stopSnapshot.error,
                    staleSince = stopSnapshot.meta?.takeIf { it.isStale }?.fetchedAt,
                    followedLines = stopSnapshot.lineFilter.size,
                )
            }

            val distinctAlerts = snapshot.stops.flatMap { stopSnapshot ->
                val board = stopSnapshot.board
                board?.stopAlerts.orEmpty() + board?.entries.orEmpty().filterOperator(filter).flatMap { it.alerts }
            }.distinctBy { it.dedupeKey }.size
            val overall = cards.fold(BoardKpis.Empty) { sum, card -> sum + card.kpis }.copy(alertCount = distinctAlerts)

            val issues = snapshot.stops.flatMap { stopSnapshot ->
                stopSnapshot.board?.entries.orEmpty()
                    .filterOperator(filter)
                    .mapNotNull { entry ->
                        val status = policy.classify(entry)
                        if (status.isDeviation) IssueUi(entry, status, stopSnapshot.stop.name) else null
                    }
            }.sortedWith(
                compareByDescending<IssueUi> { it.status.severity }
                    .thenByDescending { abs(it.entry.delaySeconds) }
                    .thenBy { it.entry.scheduled },
            ).take(MAX_ISSUES)

            val failedWithoutData = snapshot.stops.filter { it.board == null && it.error != null }
            val globalError = failedWithoutData.firstOrNull()?.error
                ?.takeIf { snapshot.stops.isNotEmpty() && failedWithoutData.size == snapshot.stops.size }

            return DashboardUiState(
                isInitialized = snapshot.isInitialized,
                isFirstLoad = cards.isNotEmpty() && cards.none { it.hasData } && cards.any { it.isLoading },
                isRefreshing = snapshot.isRefreshing,
                isDemo = settings.demoMode,
                needsApiKey = !settings.demoMode && !settings.hasApiKey,
                watchlistEmpty = snapshot.isInitialized && snapshot.stops.isEmpty(),
                watchedCount = snapshot.stops.size,
                operatorOptions = OperatorOptions.from(allEntries),
                selectedOperator = filter,
                overall = overall,
                overallHealth = if (cards.none { it.hasData }) HealthStatus.UNKNOWN else policy.health(overall),
                stops = cards,
                issues = issues,
                lastUpdated = snapshot.stops.mapNotNull { it.meta }.filterNot { it.isStale }.maxOfOrNull { it.fetchedAt },
                globalError = globalError,
                staleCount = snapshot.stops.count { it.meta?.isStale == true },
                policy = policy,
            )
        }
    }
}
