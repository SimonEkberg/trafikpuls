package com.simon.trafikpuls.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simon.trafikpuls.data.live.WatchlistMonitor
import com.simon.trafikpuls.data.live.WatchlistSnapshot
import com.simon.trafikpuls.data.settings.AppSettings
import com.simon.trafikpuls.data.settings.SettingsRepository
import com.simon.trafikpuls.domain.OperatorOption
import com.simon.trafikpuls.domain.OperatorOptions
import com.simon.trafikpuls.domain.filterOperator
import com.simon.trafikpuls.domain.model.Alert
import com.simon.trafikpuls.domain.model.Line
import com.simon.trafikpuls.domain.model.WatchedStop
import com.simon.trafikpuls.ui.appViewModelFactory
import com.simon.trafikpuls.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/** One alert, merged across every departure and stop that reports it. */
data class AlertGroupUi(
    val alert: Alert,
    val stops: List<WatchedStop>,
    val lines: List<Line>,
    val affectedDepartures: Int,
    val isStopLevel: Boolean,
)

data class AlertsUiState(
    val isInitialized: Boolean = false,
    val isRefreshing: Boolean = false,
    val isDemo: Boolean = false,
    val watchlistEmpty: Boolean = false,
    val groups: List<AlertGroupUi> = emptyList(),
    val operatorOptions: List<OperatorOption> = emptyList(),
    val selectedOperator: String? = null,
    val lastUpdated: Instant? = null,
)

class AlertsViewModel(
    private val monitor: WatchlistMonitor,
    private val settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<AlertsUiState> = combine(monitor.snapshot, settings.settings) { snapshot, appSettings ->
        buildAlertsState(snapshot, appSettings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsUiState())

    private var autoRefreshJob: Job? = null

    fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                monitor.refresh()
                delay(DashboardViewModel.AUTO_REFRESH_MILLIS)
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

    companion object {
        val Factory = appViewModelFactory { container ->
            AlertsViewModel(container.watchlistMonitor, container.settingsRepository)
        }

        private class GroupBuilder(val alert: Alert) {
            val stops = LinkedHashMap<String, WatchedStop>()
            val lines = LinkedHashMap<String, Line>()
            var departures = 0
            var stopLevel = false

            fun build() = AlertGroupUi(alert, stops.values.toList(), lines.values.toList(), departures, stopLevel)
        }

        fun buildAlertsState(snapshot: WatchlistSnapshot, settings: AppSettings): AlertsUiState {
            val filter = settings.operatorFilter
            val groups = LinkedHashMap<String, GroupBuilder>()
            for (stopSnapshot in snapshot.stops) {
                val board = stopSnapshot.board ?: continue
                board.stopAlerts.forEach { alert ->
                    groups.getOrPut(alert.dedupeKey) { GroupBuilder(alert) }.apply {
                        stops[stopSnapshot.stop.areaId] = stopSnapshot.stop
                        stopLevel = true
                    }
                }
                board.entries.filterOperator(filter).forEach { entry ->
                    entry.alerts.forEach { alert ->
                        groups.getOrPut(alert.dedupeKey) { GroupBuilder(alert) }.apply {
                            stops[stopSnapshot.stop.areaId] = stopSnapshot.stop
                            lines["${entry.line.mode}-${entry.line.designation}"] = entry.line
                            departures++
                        }
                    }
                }
            }
            return AlertsUiState(
                isInitialized = snapshot.isInitialized,
                isRefreshing = snapshot.isRefreshing,
                isDemo = settings.demoMode,
                watchlistEmpty = snapshot.isInitialized && snapshot.stops.isEmpty(),
                groups = groups.values
                    .map { it.build() }
                    .sortedWith(compareByDescending<AlertGroupUi> { it.affectedDepartures }.thenByDescending { it.isStopLevel }),
                operatorOptions = OperatorOptions.from(snapshot.stops.flatMap { it.board?.entries.orEmpty() }),
                selectedOperator = filter,
                lastUpdated = snapshot.lastRefreshAt,
            )
        }
    }
}
