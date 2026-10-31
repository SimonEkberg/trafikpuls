package com.simon.trafikpuls.data.demo

import com.simon.trafikpuls.core.nowInSweden
import com.simon.trafikpuls.data.mapper.toDomain
import com.simon.trafikpuls.data.mapper.toStopBoard
import com.simon.trafikpuls.data.mapper.toTripDetails
import com.simon.trafikpuls.domain.OperatorOptions
import com.simon.trafikpuls.domain.model.BoardKind
import com.simon.trafikpuls.domain.model.TimeWindow
import com.simon.trafikpuls.testutil.MutableClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class DemoDataSourceTest {

    private val clock = MutableClock(Instant.parse("2026-10-28T13:07:00Z"))
    private val source = DemoTrafiklabDataSource(clock, latencyMillis = 0)

    private suspend fun board(areaId: String) =
        source.board(BoardKind.DEPARTURES, areaId, null).toStopBoard(areaId, BoardKind.DEPARTURES, TimeWindow.UPCOMING)

    @Test
    fun `every demo stop has a full hour of departures`() = runTest {
        val start = clock.nowInSweden().truncatedTo(ChronoUnit.MINUTES)
        DemoNetwork.defaultWatchlist.forEach { stop ->
            val board = board(stop.areaId)
            assertTrue("${stop.name} has too few departures", board.entries.size >= 20)
            assertTrue(board.entries.all { !it.scheduled.isBefore(start) && it.scheduled.isBefore(start.plusMinutes(60)) })
        }
    }

    @Test
    fun `boards are deterministic between refreshes`() = runTest {
        assertEquals(board("740000003"), board("740000003"))
    }

    @Test
    fun `the trip view agrees with the departure board`() = runTest {
        val board = board("740000003")
        board.entries.filter { it.isRealtime && !it.isCanceled }.take(10).forEach { entry ->
            val trip = source.trip(entry.tripId, entry.startDate).toTripDetails(entry.tripId, entry.startDate)
            val call = trip.calls.single { it.areaId == entry.stopId }
            assertEquals(entry.scheduled, call.scheduledDeparture)
            assertEquals(entry.delaySeconds, call.departureDelaySeconds)
            assertEquals(entry.line.designation, trip.line.designation)
        }
    }

    @Test
    fun `demo data contains Nobina traffic and realistic deviations`() = runTest {
        val entries = DemoNetwork.defaultWatchlist.flatMap { board(it.areaId).entries }
        assertTrue(OperatorOptions.from(entries).first().isHighlighted)
        assertTrue(entries.any { it.delaySeconds > 180 })
        assertTrue(entries.any { !it.isRealtime })
    }

    @Test
    fun `search finds demo stops by name`() = runTest {
        val groups = source.searchStops("malmö").stopGroups.map { it.toDomain() }
        assertEquals(listOf("740000003"), groups.map { it.id })
    }
}
