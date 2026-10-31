package com.simon.trafikpuls.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simon.trafikpuls.domain.PunctualityPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val apiKeyOverride: String = "",
    val hasBuildApiKey: Boolean = false,
    val demoMode: Boolean = true,
    val lateThresholdMinutes: Int = DEFAULT_LATE_THRESHOLD_MINUTES,
    /** Case-insensitive "contains" filter on operator name; null shows all operators. */
    val operatorFilter: String? = null,
) {
    val hasApiKey: Boolean get() = apiKeyOverride.isNotBlank() || hasBuildApiKey

    val policy: PunctualityPolicy get() = PunctualityPolicy(lateThresholdSeconds = lateThresholdMinutes * 60)

    companion object {
        const val DEFAULT_LATE_THRESHOLD_MINUTES = 3
    }
}

/**
 * User settings in Preferences DataStore. The API key from the build (local.properties) is the
 * default; a key entered in the app overrides it without a rebuild.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val buildApiKey: String,
) {
    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val override = prefs[API_KEY].orEmpty()
            AppSettings(
                apiKeyOverride = override,
                hasBuildApiKey = buildApiKey.isNotBlank(),
                // Without any key the app starts in demo mode, so it is usable straight after install.
                demoMode = prefs[DEMO_MODE] ?: (buildApiKey.isBlank() && override.isBlank()),
                lateThresholdMinutes = prefs[LATE_THRESHOLD] ?: AppSettings.DEFAULT_LATE_THRESHOLD_MINUTES,
                operatorFilter = prefs[OPERATOR_FILTER]?.takeIf { it.isNotBlank() },
            )
        }
        .distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun effectiveApiKey(): String = current().apiKeyOverride.ifBlank { buildApiKey }

    suspend fun isDemoMode(): Boolean = current().demoMode

    /** Saving a key switches to live data - that is why the user entered it. */
    suspend fun setApiKey(key: String) {
        dataStore.edit { prefs ->
            val trimmed = key.trim()
            if (trimmed.isEmpty()) {
                prefs.remove(API_KEY)
            } else {
                prefs[API_KEY] = trimmed
                prefs[DEMO_MODE] = false
            }
        }
    }

    suspend fun setDemoMode(enabled: Boolean) {
        dataStore.edit { it[DEMO_MODE] = enabled }
    }

    suspend fun setLateThresholdMinutes(minutes: Int) {
        dataStore.edit { it[LATE_THRESHOLD] = minutes.coerceIn(1, 10) }
    }

    suspend fun setOperatorFilter(operator: String?) {
        dataStore.edit { prefs ->
            if (operator.isNullOrBlank()) prefs.remove(OPERATOR_FILTER) else prefs[OPERATOR_FILTER] = operator.trim()
        }
    }


    private companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val LATE_THRESHOLD = intPreferencesKey("late_threshold_minutes")
        val OPERATOR_FILTER = stringPreferencesKey("operator_filter")
    }
}
