package com.simon.trafikpuls.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simon.trafikpuls.core.AppError
import com.simon.trafikpuls.core.Outcome
import com.simon.trafikpuls.data.repository.AddResult
import com.simon.trafikpuls.data.repository.TrafficRepository
import com.simon.trafikpuls.data.repository.WatchlistRepository
import com.simon.trafikpuls.data.settings.SettingsRepository
import com.simon.trafikpuls.domain.model.StopGroup
import com.simon.trafikpuls.domain.model.WatchedStop
import com.simon.trafikpuls.ui.appViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SearchResult {
    data object Idle : SearchResult
    data object TooShort : SearchResult
    data object Loading : SearchResult
    data class Results(val groups: List<StopGroup>) : SearchResult
    data object NoMatches : SearchResult
    data class Failed(val error: AppError) : SearchResult
}

data class SearchUiState(
    val query: String = "",
    val result: SearchResult = SearchResult.Idle,
    val watchedIds: Set<String> = emptySet(),
    val isDemo: Boolean = false,
    val message: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val traffic: TrafficRepository,
    private val watchlist: WatchlistRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private data class Request(val text: String, val attempt: Int = 0)

    private val query = MutableStateFlow("")
    private val request = MutableStateFlow(Request(""))
    private val message = MutableStateFlow<String?>(null)

    // Debounced so that typing "Kista" costs one API call, not five.
    private val results: Flow<SearchResult> = request
        .debounce(SEARCH_DEBOUNCE_MILLIS)
        .distinctUntilChanged()
        .flatMapLatest { (text) ->
            when {
                text.isEmpty() -> flowOf(SearchResult.Idle)
                text.length < MIN_QUERY_LENGTH -> flowOf(SearchResult.TooShort)
                else -> flow {
                    emit(SearchResult.Loading)
                    emit(
                        when (val outcome = traffic.searchStops(text)) {
                            is Outcome.Success ->
                                if (outcome.value.isEmpty()) SearchResult.NoMatches else SearchResult.Results(outcome.value)
                            is Outcome.Failure -> SearchResult.Failed(outcome.error)
                        },
                    )
                }
            }
        }
        .onStart { emit(SearchResult.Idle) }

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        results,
        watchlist.watched.map { stops -> stops.map { it.areaId }.toSet() },
        settings.settings,
        message,
    ) { text, result, watchedIds, appSettings, currentMessage ->
        SearchUiState(text, result, watchedIds, appSettings.demoMode, currentMessage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(text: String) {
        query.value = text
        request.value = Request(text.trim())
    }

    fun retry() = request.update { it.copy(attempt = it.attempt + 1) }

    fun toggleWatch(group: StopGroup) {
        viewModelScope.launch {
            if (group.id in uiState.value.watchedIds) {
                watchlist.remove(group.id)
                message.value = "${group.name} bevakas inte längre"
            } else {
                message.value = when (watchlist.add(WatchedStop(group.id, group.name, group.transportModes))) {
                    AddResult.ADDED -> "${group.name} bevakas nu i översikten"
                    AddResult.ALREADY_WATCHED -> "${group.name} bevakas redan"
                    AddResult.LIMIT_REACHED -> "Du kan bevaka högst ${WatchlistRepository.MAX_WATCHED} hållplatser"
                }
            }
        }
    }

    fun messageShown() {
        message.value = null
    }

    companion object {
        const val MIN_QUERY_LENGTH = 3
        private const val SEARCH_DEBOUNCE_MILLIS = 350L

        val Factory = appViewModelFactory { container ->
            SearchViewModel(container.trafficRepository, container.watchlistRepository, container.settingsRepository)
        }
    }
}
