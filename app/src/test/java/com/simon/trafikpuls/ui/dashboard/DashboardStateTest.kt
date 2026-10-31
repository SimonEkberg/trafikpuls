package com.simon.trafikpuls.ui.dashboard

import com.simon.trafikpuls.core.AppError
import com.simon.trafikpuls.core.DataOrigin
import com.simon.trafikpuls.core.FetchMeta
import com.simon.trafikpuls.data.live.StopSnapshot
import com.simon.trafikpuls.data.live.WatchlistSnapshot
import com.simon.trafikpuls.data.settings.AppSettings
import com.simon.trafikpuls.domain.DepartureStatus
import com.simon.trafikpuls.domain.HealthStatus
import com.simon.trafikpuls.domain.model.Alert
import com.simon.trafikpuls.domain.model.BoardKind
import com.simon.trafikpuls.domain.model.StopBoard
import com.simon.trafikpuls.domain.model.TimeWindow
import com.simon.trafikpuls.domain.model.TimetableEntry
import com.simon.trafikpuls.domain.model.WatchedStop
import com.simon.trafikpuls.testutil.BASE_TIME
import com.simon.trafikpuls.testutil.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DashboardStateTest {

    private val now = Instant.parse("2025-04-01T12:30:00Z")
    private val settings = AppSettings(demoMode = false, hasBuildApiKey = true)

    private fun snapshot(vararg stops: Pair<String, List<TimetableEntry>?>, error: AppError? = null) = WatchlistSnapshot(
        stops = stops.map { (name, entries) ->
            StopSnapshot(
                stop = WatchedStop(name, name, emptySet()),
                board = entries?.let { StopBoard(name, BoardKind.DEPARTURES, TimeWindow.UPCOMING, name, BASE_TIME, emptyList(), it) },
                meta = entries?.let { FetchMeta(DataOrigin.NETWORK, now) },
                error = if (entries == null) error else null,
            )
        },
        isInitialized = true,
    )

    @Test
    fun `aggregates KPIs over all stops and pins Nobina first`() {
        val state = DashboardViewModel.buildDashboardState(
            snapshot(
                "Kista" to listOf(entry(delay = 20, operator = "Keolis Sverige AB"), entry(delay = 400, operator = "Keolis Sverige AB")),
                "Malmö" to listOf(entry(delay = 10), entry(canceled = true)),
            ),
            settings,
        )
        assertEquals(4, state.overall.total)
        assertEquals(1, state.overall.canceled)
        assertEquals(HealthStatus.CRITICAL, state.overallHealth)
        assertEquals("Nobina Sverige AB", state.operatorOptions.first().name)
        assertEquals(now, state.lastUpdated)
    }

    @Test
    fun `operator filter scopes KPIs and the deviation list`() {
        val state = DashboardViewModel.buildDashboardState(
            snapshot(
                "Kista" to listOf(entry(delay = 900, operator = "Keolis Sverige AB"), entry(delay = 20)),
            ),
            settings.copy(operatorFilter = "Nobina"),
        )
        assertEquals(1, state.overall.total)
        assertTrue(state.issues.isEmpty())
        assertEquals(2, state.operatorOptions.sumOf { it.departures }) // options always show everything
    }

    @Test
    fun `deviations are ordered by severity`() {
        val state = DashboardViewModel.buildDashboardState(
            snapshot(
                "Malmö" to listOf(
                    entry(delay = -120, line = "1"),
                    entry(delay = 300, line = "2"),
                    entry(canceled = true, line = "3"),
                    entry(delay = 700, line = "4"),
                    entry(delay = 30, line = "5"),
                ),
            ),
            settings,
        )
        assertEquals(
            listOf(DepartureStatus.CANCELED, DepartureStatus.SEVERELY_LATE, DepartureStatus.LATE, DepartureStatus.EARLY),
            state.issues.map { it.status },
        )
    }

    @Test
    fun `a shared failure without data becomes one global error`() {
        val state = DashboardViewModel.buildDashboardState(
            snapshot("Kista" to null, "Malmö" to null, error = AppError.InvalidApiKey),
            settings,
        )
        assertEquals(AppError.InvalidApiKey, state.globalError)
    }

    @Test
    fun `alerts are counted once even when repeated on many departures`() {
        val alert = Alert("CONSTRUCTION", "Omledning", "Linjen körs om.")
        val state = DashboardViewModel.buildDashboardState(
            snapshot("Malmö" to List(4) { entry(alerts = listOf(alert)) }),
            settings,
        )
        assertEquals(1, state.overall.alertCount)
        assertNull(state.globalError)
    }

    @Test
    fun `no key and no demo mode asks for a key`() {
        val state = DashboardViewModel.buildDashboardState(snapshot(), AppSettings(demoMode = false, hasBuildApiKey = false))
        assertTrue(state.needsApiKey)
        assertTrue(state.watchlistEmpty)
    }
}
