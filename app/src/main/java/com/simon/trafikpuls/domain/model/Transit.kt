package com.simon.trafikpuls.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class TransportMode(val label: String) {
    BUS("Buss"),
    TRAIN("Tåg"),
    METRO("Tunnelbana"),
    TRAM("Spårvagn"),
    BOAT("Båt"),
    TAXI("Taxi"),
    UNKNOWN("Övrigt");

    companion object {
        fun parse(raw: String?): TransportMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

data class Line(
    /** Line number or train number; falls back to the route name when the API has none. */
    val designation: String,
    val name: String?,
    val mode: TransportMode,
    val direction: String,
    val originName: String?,
    val destinationName: String?,
)

data class Alert(
    val type: String,
    val title: String,
    val text: String,
) {
    val typeLabel: String get() = AlertTypes.label(type)

    /** The same alert is repeated on every affected departure; this key merges them. */
    val dedupeKey: String get() = "${title.trim()}|${text.trim()}".lowercase()
}

/** Alert types follow the GTFS-RT cause vocabulary. */
object AlertTypes {
    private val labels = mapOf(
        "UNKNOWN_CAUSE" to "Okänd orsak",
        "OTHER_CAUSE" to "Trafikinformation",
        "TECHNICAL_PROBLEM" to "Tekniskt fel",
        "STRIKE" to "Strejk",
        "DEMONSTRATION" to "Demonstration",
        "ACCIDENT" to "Olycka",
        "HOLIDAY" to "Helgtrafik",
        "WEATHER" to "Väder",
        "MAINTENANCE" to "Underhåll",
        "CONSTRUCTION" to "Byggarbete",
        "POLICE_ACTIVITY" to "Polisinsats",
        "MEDICAL_EMERGENCY" to "Sjukdomsfall",
    )

    fun label(type: String): String {
        if (type.isBlank()) return "Trafikinformation"
        return labels[type.trim().uppercase()]
            ?: type.trim().lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

enum class BoardKind(val label: String) {
    DEPARTURES("Avgångar"),
    ARRIVALS("Ankomster"),
}

/** The API always returns a fixed 60-minute window starting at the query time. */
enum class TimeWindow(val label: String) {
    UPCOMING("Nästa 60 min"),
    PAST_HOUR("Senaste 60 min"),
}

/** One departure or arrival at a stop - the API uses the same shape for both boards. */
data class TimetableEntry(
    val tripId: String,
    val startDate: LocalDate,
    val technicalNumber: Int?,
    val stopId: String,
    val stopName: String,
    val scheduled: LocalDateTime,
    /** Realtime estimate (or actual time, for the past); equals [scheduled] without realtime data. */
    val expected: LocalDateTime,
    /** Seconds; negative when early, 0 when there is no realtime data. */
    val delaySeconds: Int,
    val isCanceled: Boolean,
    val isRealtime: Boolean,
    val line: Line,
    val agencyName: String?,
    val operatorName: String?,
    val scheduledPlatform: String?,
    val realtimePlatform: String?,
    val alerts: List<Alert>,
    val advanceOrderMessage: String?,
) {
    val id: String get() = "$tripId|$startDate|$stopId|$scheduled"

    /** The contracted operator (e.g. "Nobina Sverige AB"), or the agency when the operator is unknown. */
    val operatorLabel: String
        get() = operatorName?.takeIf { it.isNotBlank() }
            ?: agencyName?.takeIf { it.isNotBlank() }
            ?: UNKNOWN_OPERATOR

    val platform: String? get() = realtimePlatform ?: scheduledPlatform

    val platformChanged: Boolean
        get() = scheduledPlatform != null && realtimePlatform != null &&
            !scheduledPlatform.equals(realtimePlatform, ignoreCase = true)

    companion object {
        const val UNKNOWN_OPERATOR = "Okänd operatör"
    }
}

data class StopBoard(
    val areaId: String,
    val kind: BoardKind,
    val window: TimeWindow,
    val name: String,
    val queryTime: LocalDateTime?,
    val stopAlerts: List<Alert>,
    val entries: List<TimetableEntry>,
)
