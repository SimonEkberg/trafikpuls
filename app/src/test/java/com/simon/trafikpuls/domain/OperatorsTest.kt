package com.simon.trafikpuls.domain

import com.simon.trafikpuls.testutil.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorsTest {

    @Test
    fun `operator filter is a case-insensitive contains`() {
        val entries = listOf(
            entry(operator = "Nobina Sverige AB"),
            entry(operator = "Keolis Sverige AB"),
            entry(operator = null, agency = "Västtrafik"),
        )
        assertEquals(1, entries.filterOperator("nobina").size)
        assertEquals(1, entries.filterOperator("västtrafik").size) // falls back to agency
        assertEquals(3, entries.filterOperator(null).size)
        assertEquals(3, entries.filterOperator("  ").size)
    }

    @Test
    fun `Nobina is pinned first in the operator options`() {
        val entries = List(5) { entry(operator = "Keolis Sverige AB") } + List(2) { entry(operator = "Nobina Sverige AB") }
        val options = OperatorOptions.from(entries)
        assertEquals(listOf("Nobina Sverige AB", "Keolis Sverige AB"), options.map { it.name })
        assertTrue(options.first().isHighlighted)
        assertEquals(5, options[1].departures)
    }
}
