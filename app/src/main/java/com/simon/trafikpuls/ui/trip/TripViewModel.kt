package com.simon.trafikpuls.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simon.trafikpuls.core.AppError
import com.simon.trafikpuls.core.FetchMeta
import com.simon.trafikpuls.core.Outcome
import com.simon.trafikpuls.core.nowInSweden
import com.simon.trafikpuls.data.repository.TrafficRepository
import com.simon.trafikpuls.data.settings.SettingsRepository
import com.simon.trafikpuls.domain.PunctualityPolicy
import com.simon.trafikpuls.domain.TripAnalyzer
import com.simon.trafikpuls.domain.TripInsights
import com.simon.trafikpuls.domain.model.TripDetails
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
import java.time.LocalDate

data class TripUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val trip: TripDetails? = null,
    val insights: TripInsights? = null,
    val meta: FetchMeta? = null,
    val error: AppError? = null,
    /** The stop the user came from. */
    val highlightIndex: Int? = null,
    val selectedIndex: Int? = null,
    val policy: PunctualityPolicy = PunctualityPolicy(),
    val isDemo: Boolean = false,
)

class TripViewModel(
    private val tripId: String,
    private val startDate: LocalDate,
    private val highlightStopId: String?,
    private val traffic: TrafficRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val state = MutableStateFlow(TripUiState())
    private var autoRefreshJob: Job? = null
    private var fetchJob: Job? = null
    private var fetchGeneration = 0

    val uiState: StateFlow<TripUiState> = combine(state, settings.settings) { current, appSettings ->
        current.copy(policy = appSettings.policy, isDemo = appSettings.demoMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripUiState())

    init {
        launchFetch(showSpinner = true)
    }

    fun refresh() = launchFetch(showSpinner = false)

    fun select(index: Int?) = state.update { it.copy(selectedIndex = index) }

    fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            // Back on the screen with old data or an error: fetch now instead of in a minute.
            if (fetchJob?.isActive != true && isOutdated()) launchFetch(showSpinner = false)
            while (isActive) {
                delay(AUTO_REFRESH_MILLIS)
                launchFetch(showSpinner = false)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun isOutdated(): Boolean {
        val meta = state.value.meta ?: return true
        return Duration.between(meta.fetchedAt, clock.instant()).toMillis() >= AUTO_REFRESH_MILLIS
    }

    /** Only the latest request writes state; the spinner is always reset, also on cancellation. */
    private fun launchFetch(showSpinner: Boolean) {
        fetchJob?.cancel()
        val generation = ++fetchGeneration
        fetchJob = viewModelScope.launch {
            try {
                fetch(showSpinner) { generation == fetchGeneration }
            } finally {
                if (generation == fetchGeneration) state.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    private suspend fun fetch(showSpinner: Boolean, isCurrent: () -> Boolean) {
        state.update {
            if (showSpinner) it.copy(isLoading = true, error = null) else it.copy(isRefreshing = true)
        }
        val outcome = traffic.trip(tripId, startDate)
        if (!isCurrent()) return
        when (outcome) {
            is Outcome.Success -> {
                val trip = outcome.value
                val highlight = highlightStopId?.let { id ->
                    trip.calls.indexOfFirst { it.stopId == id || it.areaId == id }.takeIf { it >= 0 }
                }
                state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        trip = trip,
                        insights = TripAnalyzer.analyze(trip, clock.nowInSweden()),
                        meta = outcome.meta,
                        error = null,
                        highlightIndex = highlight,
                        selectedIndex = it.selectedIndex ?: highlight,
                    )
                }
            }
            is Outcome.Failure -> state.update {
                it.copy(isLoading = false, isRefreshing = false, error = outcome.error)
            }
        }
    }

    companion object {
        private const val AUTO_REFRESH_MILLIS = 60_000L

        fun factory(tripId: String, startDate: String, highlightStopId: String?) = appViewModelFactory { container ->
            TripViewModel(
                tripId = tripId,
                startDate = runCatching { LocalDate.parse(startDate) }.getOrElse { LocalDate.now(container.clock) },
                highlightStopId = highlightStopId,
                traffic = container.trafficRepository,
                settings = container.settingsRepository,
                clock = container.clock,
            )
        }
    }
}
