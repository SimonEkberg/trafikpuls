package com.simon.trafikpuls.domain

import com.simon.trafikpuls.domain.model.TimetableEntry

/**
 * The operator filter is a case-insensitive "contains" so that a short filter such as
 * "Nobina" matches the full company name "Nobina Sverige AB".
 */
fun TimetableEntry.matchesOperator(filter: String?): Boolean =
    filter.isNullOrBlank() || operatorLabel.contains(filter.trim(), ignoreCase = true)

fun List<TimetableEntry>.filterOperator(filter: String?): List<TimetableEntry> =
    if (filter.isNullOrBlank()) this else filter { it.matchesOperator(filter) }

data class OperatorOption(
    val name: String,
    val departures: Int,
    val isHighlighted: Boolean,
)

object OperatorOptions {
    /** Pinned first whenever present in the data. */
    const val HIGHLIGHTED = "Nobina"

    fun from(entries: Iterable<TimetableEntry>): List<OperatorOption> =
        entries.groupingBy { it.operatorLabel }
            .eachCount()
            .map { (name, count) -> OperatorOption(name, count, name.contains(HIGHLIGHTED, ignoreCase = true)) }
            .sortedWith(
                compareByDescending<OperatorOption> { it.isHighlighted }
                    .thenByDescending { it.departures }
                    .thenBy { it.name },
            )
}
