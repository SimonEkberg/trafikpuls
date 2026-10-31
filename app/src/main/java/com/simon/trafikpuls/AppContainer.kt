package com.simon.trafikpuls

import android.content.Context
import androidx.work.WorkManager
import com.simon.trafikpuls.core.RateLimiter
import com.simon.trafikpuls.core.SwedishZone
import com.simon.trafikpuls.data.demo.DemoTrafiklabDataSource
import com.simon.trafikpuls.data.live.WatchlistMonitor
import com.simon.trafikpuls.data.local.TrafikpulsDatabase
import com.simon.trafikpuls.data.remote.NetworkModule
import com.simon.trafikpuls.data.repository.LineFilterRepository
import com.simon.trafikpuls.data.repository.TrafficRepository
import com.simon.trafikpuls.data.repository.WatchlistRepository
import com.simon.trafikpuls.data.settings.SettingsRepository
import com.simon.trafikpuls.data.settings.settingsDataStore
import com.simon.trafikpuls.data.source.RemoteTrafiklabDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * Manual dependency injection: one place that wires the object graph. Small enough for a POC
 * to stay readable; the constructor-injected classes would move to Hilt unchanged.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: Clock = Clock.system(SwedishZone)

    private val database by lazy { TrafikpulsDatabase.build(appContext) }

    val settingsRepository by lazy {
        SettingsRepository(appContext.settingsDataStore, BuildConfig.TRAFIKLAB_API_KEY)
    }

    private val trafiklabApi by lazy {
        NetworkModule.trafiklabApi(
            NetworkModule.okHttpClient(
                debugLogging = BuildConfig.DEBUG,
                userAgent = "Trafikpuls/${BuildConfig.VERSION_NAME} (Android)",
            ),
        )
    }

    val trafficRepository by lazy {
        TrafficRepository(
            remote = RemoteTrafiklabDataSource(
                api = trafiklabApi,
                // Bronze allows 25 calls/minute; keep a margin for other apps sharing the key.
                rateLimiter = RateLimiter(maxRequests = 20, windowMillis = 60_000, clock = clock),
                apiKey = { settingsRepository.effectiveApiKey() },
            ),
            demo = DemoTrafiklabDataSource(clock),
            isDemoMode = { settingsRepository.isDemoMode() },
            boardCache = database.boardCacheDao(),
            json = NetworkModule.json,
            clock = clock,
        )
    }

    val watchlistRepository by lazy {
        WatchlistRepository(
            dao = database.watchedStopDao(),
            demoModeFlow = settingsRepository.settings.map { it.demoMode },
            isDemoMode = { settingsRepository.isDemoMode() },
            clock = clock,
        )
    }

    val lineFilterRepository by lazy {
        LineFilterRepository(
            dao = database.lineFilterDao(),
            demoModeFlow = settingsRepository.settings.map { it.demoMode },
            isDemoMode = { settingsRepository.isDemoMode() },
        )
    }

    val watchlistMonitor by lazy {
        WatchlistMonitor(
            watchlist = watchlistRepository,
            traffic = trafficRepository,
            settings = settingsRepository.settings,
            lineFilters = lineFilterRepository.filters,
            scope = appScope,
            clock = clock,
        )
    }

    /**
     * The app only fetches data while a screen is visible - nothing runs when it is closed.
     * Versions before 0.2 had a background job; cancel anything they may have scheduled.
     */
    fun start() {
        WorkManager.getInstance(appContext).cancelAllWork()
        appScope.launch { trafficRepository.pruneCache() }
    }
}
