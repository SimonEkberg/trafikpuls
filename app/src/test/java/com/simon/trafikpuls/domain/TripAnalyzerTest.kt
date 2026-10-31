package com.simon.trafikpuls.domain

import com.simon.trafikpuls.data.mapper.toTripDetails
import com.simon.trafikpuls.data.remote.NetworkModule
import com.simon.trafikpuls.data.remote.dto.TripDetailsResponseDto
import com.simon.trafikpuls.testutil.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TripAnalyzerTest {

    private val trip = NetworkModule.json.decodeFromString<TripDetailsResponseDto>(fixture("trip.json"))
        .toTripDetails("x", LocalDate.of(2026, 1, 20))

    @Test
    fun `finds where on the route the delay grew the most`() {
        val insights = TripAnalyzer.analyze(trip, LocalDateTime.of(2026, 1, 20, 14, 45))
        val segment = insights.worstSegment!!
        assertEquals("Ljunghusen Storvägen", segment.fromStop)
        assertEquals("Höllviken Nyckelhålsparken", segment.toStop)
        assertEquals(199, segment.deltaSeconds)
    }

    @Test
    fun `current delay comes from the last passed stop and final delay from the terminus`() {
        val insights = TripAnalyzer.analyze(trip, LocalDateTime.of(2026, 1, 20, 14, 45))
        assertEquals(2, insights.lastPassedIndex)
        assertEquals(210, insights.currentDelaySeconds)
        assertEquals(260, insights.finalDelaySeconds)
        assertEquals(1, insights.canceledStops)
        assertEquals(260, insights.maxDelay!!.delaySeconds)
    }

    @Test
    fun `before departure nothing has been passed`() {
        val insights = TripAnalyzer.analyze(trip, LocalDateTime.of(2026, 1, 20, 14, 0))
        assertNull(insights.lastPassedIndex)
        assertNull(insights.currentDelaySeconds)
    }

    @Test
    fun `small increases are not reported as a worst segment`() {
        val calm = trip.copy(calls = trip.calls.take(2))
        assertNull(TripAnalyzer.analyze(calm, LocalDateTime.of(2026, 1, 20, 14, 45)).worstSegment)
    }
}
