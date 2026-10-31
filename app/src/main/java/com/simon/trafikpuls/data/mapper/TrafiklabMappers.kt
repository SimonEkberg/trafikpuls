package com.simon.trafikpuls.data.mapper

import com.simon.trafikpuls.core.parseApiDate
import com.simon.trafikpuls.core.parseApiDateTime
import com.simon.trafikpuls.data.remote.dto.AlertDto
import com.simon.trafikpuls.data.remote.dto.BoardResponseDto
import com.simon.trafikpuls.data.remote.dto.CallDto
import com.simon.trafikpuls.data.remote.dto.PlatformDto
import com.simon.trafikpuls.data.remote.dto.RouteDto
import com.simon.trafikpuls.data.remote.dto.StopGroupDto
import com.simon.trafikpuls.data.remote.dto.TimetableEntryDto
import com.simon.trafikpuls.data.remote.dto.TripDetailsResponseDto
import com.simon.trafikpuls.domain.model.Alert
import com.simon.trafikpuls.domain.model.AreaType
import com.simon.trafikpuls.domain.model.BoardKind
import com.simon.trafikpuls.domain.model.Line
import com.simon.trafikpuls.domain.model.StopBoard
import com.simon.trafikpuls.domain.model.StopGroup
import com.simon.trafikpuls.domain.model.TimeWindow
import com.simon.trafikpuls.domain.model.TimetableEntry
import com.simon.trafikpuls.domain.model.TransportMode
import com.simon.trafikpuls.domain.model.TripCall
import com.simon.trafikpuls.domain.model.TripDetails
import java.time.LocalDate

// DTO -> domain. Mapping is defensive: an entry missing essential data is dropped
// instead of failing the whole response.

fun BoardResponseDto.toStopBoard(
    areaId: String,
    kind: BoardKind,
    window: TimeWindow,
    nameHint: String? = null,
): StopBoard {
    val raw = when (kind) {
        BoardKind.DEPARTURES -> departures
        BoardKind.ARRIVALS -> arrivals
    }.orEmpty()
    return StopBoard(
        areaId = areaId,
        kind = kind,
        window = window,
        name = nameHint?.takeIf { it.isNotBlank() }
            ?: stops.firstOrNull { it.name.isNotBlank() }?.name
            ?: areaId,
        queryTime = parseApiDateTime(query?.queryTime),
        stopAlerts = stops.flatMap { it.alerts }.mapNotNull { it.toDomain() }.distinctBy { it.dedupeKey },
        entries = raw.mapNotNull { it.toDomain() }.sortedWith(compareBy({ it.expected }, { it.scheduled })),
    )
}

fun TimetableEntryDto.toDomain(): TimetableEntry? {
    val scheduledTime = parseApiDateTime(scheduled) ?: return null
    val tripId = trip?.tripId?.takeIf { it.isNotBlank() } ?: return null
    return TimetableEntry(
        tripId = tripId,
        startDate = parseApiDate(trip?.startDate) ?: scheduledTime.toLocalDate(),
        technicalNumber = trip?.technicalNumber,
        stopId = stop?.id.orEmpty(),
        stopName = stop?.name.orEmpty(),
        scheduled = scheduledTime,
        expected = parseApiDateTime(realtime) ?: scheduledTime.plusSeconds(delay.toLong()),
        delaySeconds = delay,
        isCanceled = canceled,
        isRealtime = isRealtime,
        line = route.toLine(),
        agencyName = agency?.name?.takeIf { it.isNotBlank() },
        operatorName = agency?.operator?.takeIf { it.isNotBlank() },
        scheduledPlatform = scheduledPlatform.designationOrNull(),
        realtimePlatform = realtimePlatform.designationOrNull(),
        alerts = alerts.mapNotNull { it.toDomain() },
        advanceOrderMessage = advanceOrder?.message?.takeIf { it.isNotBlank() },
    )
}

fun RouteDto?.toLine(): Line = Line(
    designation = this?.designation?.takeIf { it.isNotBlank() }
        ?: this?.name?.takeIf { it.isNotBlank() }
        ?: "–",
    name = this?.name?.takeIf { it.isNotBlank() },
    mode = TransportMode.parse(this?.transportMode),
    direction = this?.direction?.takeIf { it.isNotBlank() } ?: this?.destination?.name.orEmpty(),
    originName = this?.origin?.name,
    destinationName = this?.destination?.name,
)

fun AlertDto.toDomain(): Alert? {
    val title = title?.trim().orEmpty()
    val text = text?.trim().orEmpty()
    if (title.isEmpty() && text.isEmpty()) return null
    return Alert(
        type = type?.trim().orEmpty(),
        title = title.ifEmpty { text.take(80) },
        text = text,
    )
}

fun TripDetailsResponseDto.toTripDetails(fallbackTripId: String, fallbackDate: LocalDate): TripDetails =
    TripDetails(
        tripId = trip?.tripId?.takeIf { it.isNotBlank() } ?: fallbackTripId,
        startDate = parseApiDate(trip?.startDate) ?: fallbackDate,
        technicalNumber = trip?.technicalNumber,
        line = route.toLine(),
        agencyName = agency?.name?.takeIf { it.isNotBlank() },
        operatorName = agency?.operator?.takeIf { it.isNotBlank() },
        calls = calls.map { it.toDomain() },
    )

fun CallDto.toDomain(): TripCall = TripCall(
    stopId = stop.id,
    areaId = stop.areaId,
    stopName = stop.name,
    scheduledArrival = parseApiDateTime(scheduledArrival),
    expectedArrival = parseApiDateTime(realtimeArrival),
    arrivalDelaySeconds = arrivalDelay,
    arrivalCanceled = arrivalCanceled,
    scheduledDeparture = parseApiDateTime(scheduledDeparture),
    expectedDeparture = parseApiDateTime(realtimeDeparture),
    departureDelaySeconds = departureDelay,
    departureCanceled = departureCanceled,
    platform = realtimePlatform.designationOrNull() ?: scheduledPlatform.designationOrNull(),
    isRealtime = isRealtime,
    alerts = alerts.mapNotNull { it.toDomain() },
)

fun StopGroupDto.toDomain(): StopGroup = StopGroup(
    id = id,
    name = name,
    areaType = AreaType.parse(areaType),
    averageDailyDepartures = averageDailyStopTimes,
    transportModes = transportModes.map(TransportMode::parse).filter { it != TransportMode.UNKNOWN }.toSet(),
    stopCount = stops.size,
)

private fun PlatformDto?.designationOrNull(): String? = this?.designation?.trim()?.takeIf { it.isNotEmpty() }
