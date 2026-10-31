package com.simon.trafikpuls.domain.model

enum class AreaType(val label: String) {
    RIKSHALLPLATS("Rikshållplats"),
    META_STOP("Samlingshållplats"),
    UNKNOWN("Hållplats");

    companion object {
        fun parse(raw: String?): AreaType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** A searchable stop group (rikshållplats or meta stop) - the id the departure board API accepts. */
data class StopGroup(
    val id: String,
    val name: String,
    val areaType: AreaType,
    val averageDailyDepartures: Double?,
    val transportModes: Set<TransportMode>,
    val stopCount: Int,
)

data class WatchedStop(
    val areaId: String,
    val name: String,
    val transportModes: Set<TransportMode>,
)
