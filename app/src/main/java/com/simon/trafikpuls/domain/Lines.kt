package com.simon.trafikpuls.domain

import com.simon.trafikpuls.domain.model.Line
import com.simon.trafikpuls.domain.model.TimetableEntry
import com.simon.trafikpuls.domain.model.TransportMode

private const val SEPARATOR = '|'

/** A line in one direction, e.g. "BUS|311|Brommaplan" - the unit a commuter filters on. */
fun lineKeyOf(line: Line): String = listOf(line.mode.name, line.designation, line.direction).joinToString(SEPARATOR.toString())

val TimetableEntry.lineKey: String get() = lineKeyOf(line)

/** An empty selection means "all lines". */
fun List<TimetableEntry>.filterLines(selected: Set<String>): List<TimetableEntry> =
    if (selected.isEmpty()) this else filter { it.lineKey in selected }

data class LineOption(
    val key: String,
    val line: Line,
    val departures: Int,
    val isSelected: Boolean,
)

object LineOptions {

    /**
     * Every line/direction in the data, plus selected ones that happen to have no departures right
     * now - otherwise a saved filter could not be seen or removed during a quiet hour.
     */
    fun from(entries: List<TimetableEntry>, selected: Set<String>): List<LineOption> {
        val inData = entries.groupBy { it.lineKey }.map { (key, group) ->
            LineOption(key, group.first().line, group.size, key in selected)
        }
        val missing = selected
            .filter { key -> inData.none { it.key == key } }
            .mapNotNull { key -> parse(key)?.let { LineOption(key, it, 0, isSelected = true) } }
        return (inData + missing).sortedWith(
            compareBy<LineOption>({ it.line.designation.toIntOrNull() ?: Int.MAX_VALUE }, { it.line.designation }, { it.line.direction }),
        )
    }

    fun parse(key: String): Line? {
        val parts = key.split(SEPARATOR, limit = 3)
        if (parts.size != 3) return null
        return Line(
            designation = parts[1],
            name = null,
            mode = TransportMode.parse(parts[0]),
            direction = parts[2],
            originName = null,
            destinationName = null,
        )
    }
}
