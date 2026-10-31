package com.simon.trafikpuls.domain

import com.simon.trafikpuls.domain.model.Line
import com.simon.trafikpuls.domain.model.TransportMode
import com.simon.trafikpuls.testutil.entry
import com.simon.trafikpuls.ui.format.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinesTest {

    private val towardsTown = entry(line = "311").let { it.copy(line = it.line.copy(direction = "Brommaplan")) }
    private val awayFromTown = entry(line = "311").let { it.copy(line = it.line.copy(direction = "Sjöängen")) }
    private val otherLine = entry(line = "312")

    @Test
    fun `a line key separates directions`() {
        assertEquals("BUS|311|Brommaplan", towardsTown.lineKey)
        assertEquals(
            listOf(towardsTown),
            listOf(towardsTown, awayFromTown, otherLine).filterLines(setOf("BUS|311|Brommaplan")),
        )
    }

    @Test
    fun `no selection shows every line`() {
        assertEquals(3, listOf(towardsTown, awayFromTown, otherLine).filterLines(emptySet()).size)
    }

    @Test
    fun `options list each direction once, keep selected lines that have no departures now`() {
        val options = LineOptions.from(listOf(towardsTown, towardsTown, otherLine), setOf("BUS|311|Brommaplan", "BUS|99|Ingenstans"))
        assertEquals(listOf("99", "311", "312"), options.map { it.line.designation }) // numeric order
        assertEquals(listOf(0, 2, 1), options.map { it.departures })
        assertEquals(listOf(true, true, false), options.map { it.isSelected })
    }

    @Test
    fun `line keys round-trip`() {
        assertEquals(Line("311", null, TransportMode.BUS, "Brommaplan", null, null), LineOptions.parse("BUS|311|Brommaplan"))
        assertNull(LineOptions.parse("broken"))
    }

    @Test
    fun `origin shows where the trip starts, or that it starts here`() {
        fun withOrigin(origin: String?, stop: String) =
            entry(stopName = stop).let { it.copy(line = it.line.copy(originName = origin)) }

        assertEquals("Från Sjöängen", Format.origin(withOrigin("Sjöängen", "Tappström")))
        assertEquals("Startar här", Format.origin(withOrigin("Ekerö Tappström", "Tappström")))
        assertNull(Format.origin(withOrigin(null, "Tappström")))
    }
}
