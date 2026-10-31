package com.simon.trafikpuls.data.mapper

import com.simon.trafikpuls.data.remote.NetworkModule
import com.simon.trafikpuls.data.remote.dto.BoardResponseDto
import com.simon.trafikpuls.data.remote.dto.StopLookupResponseDto
import com.simon.trafikpuls.data.remote.dto.TripDetailsResponseDto
import com.simon.trafikpuls.domain.BoardKpis
import com.simon.trafikpuls.domain.PunctualityPolicy
import com.simon.trafikpuls.domain.filterOperator
import com.simon.trafikpuls.domain.model.AreaType
import com.simon.trafikpuls.domain.model.BoardKind
import com.simon.trafikpuls.domain.model.TimeWindow
import com.simon.trafikpuls.domain.model.TransportMode
import com.simon.trafikpuls.testutil.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Parses the documented Trafiklab response shapes (with real-world quirks) end to end. */
class TrafiklabParsingTest {

    private val json = NetworkModule.json

    private fun board() = json.decodeFromString<BoardResponseDto>(fixture("departures.json"))
        .toStopBoard("740000002", BoardKind.DEPARTURES, TimeWindow.UPCOMING)

    @Test
    fun `entries without a trip are dropped, the rest are kept and sorted by expected time`() {
        val board = board()
        assertEquals(6, board.entries.size)
        assertEquals(board.entries.sortedBy { it.expected }, board.entries)
        assertEquals("Göteborg C", board.name)
        assertEquals(LocalDateTime.of(2025, 4, 1, 14, 22), board.queryTime)
    }

    @Test
    fun `operator, platforms and realtime fields are mapped`() {
        val bus16 = board().entries.single { it.line.designation == "16" }
        assertEquals("Nobina Sverige AB", bus16.operatorName)
        assertEquals("Västtrafik", bus16.agencyName)
        assertEquals(245, bus16.delaySeconds)
        assertEquals(LocalDateTime.of(2025, 4, 1, 14, 36, 5), bus16.expected)
        assertTrue(bus16.isRealtime)
        assertTrue(bus16.platformChanged)
        assertEquals("G", bus16.platform)
        assertEquals(TransportMode.BUS, bus16.line.mode)
    }

    @Test
    fun `missing designation falls back to route name and nulls fall back to defaults`() {
        val entries = board().entries
        val mtrx = entries.single { it.tripId == "680000000000000109" }
        assertEquals("MTRX", mtrx.line.designation)
        assertNull(mtrx.platform)

        val tram = entries.single { it.line.mode == TransportMode.TRAM }
        assertEquals(0, tram.delaySeconds) // "delay": null
        assertEquals(LocalDate.of(2025, 4, 1), tram.startDate) // compact yyyyMMdd date
        assertEquals("Västtrafik", tram.operatorLabel) // operator null -> agency
    }

    @Test
    fun `alerts and advance order messages are mapped`() {
        val board = board()
        assertEquals(listOf("Hållplatsläge B flyttat"), board.stopAlerts.map { it.title })
        val canceled = board.entries.single { it.isCanceled }
        assertEquals("Trafikinformation", canceled.alerts.single().typeLabel)
        val train = board.entries.single { it.line.designation == "3258" }
        assertTrue(train.advanceOrderMessage!!.startsWith("Resan måste beställas"))
        assertFalse(train.isRealtime)
    }

    @Test
    fun `fixture KPIs - all operators and Nobina only`() {
        val policy = PunctualityPolicy()
        val entries = board().entries

        val all = BoardKpis.of(entries, policy)
        assertEquals(6, all.total)
        assertEquals(1, all.canceled)
        assertEquals(3, all.tracked) // bus 16 late, MTRX on time, bus 19 early
        assertEquals(1, all.onTime)
        assertEquals(1, all.late)
        assertEquals(1, all.early)
        assertEquals(1.0 / 3, all.punctuality!!, 1e-9)
        assertEquals(0.6, all.realtimeCoverage!!, 1e-9)

        val nobina = BoardKpis.of(entries.filterOperator("nobina"), policy)
        assertEquals(3, nobina.total)
        assertEquals(1, nobina.canceled)
        assertEquals(0.0, nobina.punctuality!!, 1e-9)
    }

    @Test
    fun `trip details are mapped including canceled and terminal calls`() {
        val trip = json.decodeFromString<TripDetailsResponseDto>(fixture("trip.json"))
            .toTripDetails("fallback", LocalDate.of(2000, 1, 1))
        assertEquals("121120000372401393", trip.tripId)
        assertEquals(LocalDate.of(2026, 1, 20), trip.startDate)
        assertEquals("Bergkvarabuss AB", trip.operatorLabel)
        assertEquals(6, trip.calls.size)
        assertTrue(trip.calls[4].isCanceled)
        assertFalse(trip.calls[3].isCanceled)
        val terminus = trip.calls.last()
        assertNull(terminus.scheduledDeparture)
        assertEquals(260, terminus.delaySeconds)
        assertEquals("740000003", terminus.areaId)
    }

    @Test
    fun `stop lookup accepts both area_type and group_type`() {
        val groups = json.decodeFromString<StopLookupResponseDto>(fixture("stops.json")).stopGroups.map { it.toDomain() }
        assertEquals(AreaType.META_STOP, groups[0].areaType)
        assertEquals(AreaType.RIKSHALLPLATS, groups[1].areaType)
        assertEquals(3, groups[0].stopCount)
        assertEquals(setOf(TransportMode.BUS), groups[1].transportModes) // unknown modes are ignored
    }
}
