package com.simon.trafikpuls.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simon.trafikpuls.data.settings.AppSettings
import com.simon.trafikpuls.data.settings.SettingsRepository
import com.simon.trafikpuls.ui.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Null until DataStore has been read, so the screen never flashes default values. */
    val uiState: StateFlow<AppSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveApiKey(key: String) = launch { settings.setApiKey(key) }

    fun clearApiKey() = launch { settings.setApiKey("") }

    fun setDemoMode(enabled: Boolean) = launch { settings.setDemoMode(enabled) }

    fun setLateThreshold(minutes: Int) = launch { settings.setLateThresholdMinutes(minutes) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory = appViewModelFactory { container ->
            SettingsViewModel(container.settingsRepository)
        }
    }
}
