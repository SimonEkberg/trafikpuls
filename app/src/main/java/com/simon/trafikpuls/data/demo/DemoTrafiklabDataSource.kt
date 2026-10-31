package com.simon.trafikpuls.data.demo

import com.simon.trafikpuls.core.DataNotFoundException
import com.simon.trafikpuls.core.TimeFormats
import com.simon.trafikpuls.core.nowInSweden
import com.simon.trafikpuls.data.remote.dto.AgencyDto
import com.simon.trafikpuls.data.remote.dto.BoardResponseDto
import com.simon.trafikpuls.data.remote.dto.CallDto
import com.simon.trafikpuls.data.remote.dto.PlatformDto
import com.simon.trafikpuls.data.remote.dto.QueryDto
import com.simon.trafikpuls.data.remote.dto.RealtimeStopDto
import com.simon.trafikpuls.data.remote.dto.RouteDto
import com.simon.trafikpuls.data.remote.dto.StopDto
import com.simon.trafikpuls.data.remote.dto.StopGroupDto
import com.simon.trafikpuls.data.remote.dto.StopLookupResponseDto
import com.simon.trafikpuls.data.remote.dto.StopRefDto
import com.simon.trafikpuls.data.remote.dto.TimetableEntryDto
import com.simon.trafikpuls.data.remote.dto.TripDetailsResponseDto
import com.simon.trafikpuls.data.remote.dto.TripDto
import com.simon.trafikpuls.data.source.TrafiklabDataSource
import com.simon.trafikpuls.domain.model.BoardKind
import com.simon.trafikpuls.domain.model.TransportMode
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Offline stand-in for the Trafiklab API. It produces the same wire format as the real API,
 * relative to the current time, so demo mode exercises the exact same mapping, KPI and UI code.
 * Everything is derived from deterministic seeds: a departure keeps its delay between refreshes,
 * and the trip view agrees with the departure board.
 */
class DemoTrafiklabDataSource(
    private val clock: Clock,
    private val latencyMillis: Long = 300,
) : TrafiklabDataSource {

    override suspend fun board(kind: BoardKind, areaId: String, at: LocalDateTime?): BoardResponseDto {
        delay(latencyMillis)
        val now = clock.nowInSweden()
        val from = (at ?: now).truncatedTo(ChronoUnit.MINUTES)
        val until = from.plusMinutes(60)
        val stop = DemoNetwork.stop(areaId)
        val entries = stop.lines
            .flatMap { line -> departureTimes(line, from, until).map { time -> DemoTrips.entry(stop, line, time, kind) } }
            .sortedBy { it.realtime ?: it.scheduled }
        return BoardResponseDto(
            timestamp = now.format(TimeFormats.apiSecond),
            query = QueryDto(queryTime = from.format(TimeFormats.apiSecond), query = areaId),
            stops = listOf(
                RealtimeStopDto(
                    id = stop.id,
                    name = stop.name,
                    lat = stop.lat,
                    lon = stop.lon,
                    transportModes = stop.transportModes.map { it.name },
                    alerts = stop.stopAlerts,
                ),
            ),
            departures = entries.takeIf { kind == BoardKind.DEPARTURES },
            arrivals = entries.takeIf { kind == BoardKind.ARRIVALS },
        )
    }

    override suspend fun trip(tripId: String, startDate: LocalDate): TripDetailsResponseDto {
        delay(latencyMillis)
        val ref = DemoTrips.parse(tripId) ?: throw DataNotFoundException("Unknown demo trip $tripId")
        val stop = DemoNetwork.stop(ref.areaId)
        val line = stop.lines.firstOrNull { it.key == ref.lineKey }
            ?: throw DataNotFoundException("Unknown demo line ${ref.lineKey}")
        return DemoTrips.details(stop, line, ref.boardTime)
    }

    override suspend fun searchStops(query: String): StopLookupResponseDto {
        delay(latencyMillis)
        val now = clock.nowInSweden().format(TimeFormats.apiSecond)
        val q = query.trim()
        val groups = DemoNetwork.stops
            .filter { it.name.contains(q, ignoreCase = true) }
            .sortedByDescending { it.averageDaily }
            .map { stop ->
                StopGroupDto(
                    id = stop.id,
                    name = stop.name,
                    areaType = "RIKSHALLPLATS",
                    averageDailyStopTimes = stop.averageDaily,
                    transportModes = stop.transportModes.map { it.name },
                    stops = listOf(StopDto(stop.id, stop.name, stop.lat, stop.lon)),
                )
            }
        return StopLookupResponseDto(timestamp = now, query = QueryDto(now, q), stopGroups = groups)
    }

    private fun departureTimes(line: DemoLine, from: LocalDateTime, until: LocalDateTime): List<LocalDateTime> {
        val times = mutableListOf<LocalDateTime>()
        var time = from
        while (time.isBefore(until)) {
            val minuteOfDay = time.hour * 60 + time.minute
            if (Math.floorMod(minuteOfDay - line.offsetMinutes, line.headwayMinutes) == 0) times += time
            time = time.plusMinutes(1)
        }
        return times
    }
}

/** Deterministic trip generation shared by the board and the trip view. */
internal object DemoTrips {

    data class Ref(val areaId: String, val lineKey: String, val boardTime: LocalDateTime)

    private class Profile(
        val route: List<String>,
        val boardIndex: Int,
        val firstScheduled: LocalDateTime,
        val isRealtime: Boolean,
        val isCanceled: Boolean,
        val platformChanged: Boolean,
        val delays: IntArray,
        val technicalNumber: Int,
    )

    private val keyFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

    fun tripId(stop: DemoStop, line: DemoLine, boardTime: LocalDateTime): String =
        "demo-${stop.id}-${line.key}-${boardTime.format(keyFormat)}"

    fun parse(tripId: String): Ref? {
        val parts = tripId.split('-')
        if (parts.size != 4 || parts[0] != "demo") return null
        val time = runCatching { LocalDateTime.parse(parts[3], keyFormat) }.getOrNull() ?: return null
        return Ref(parts[1], parts[2], time)
    }

    fun entry(stop: DemoStop, line: DemoLine, boardTime: LocalDateTime, kind: BoardKind): TimetableEntryDto {
        val profile = profile(stop, line, boardTime)
        val delay = if (profile.isRealtime) profile.delays[profile.boardIndex] else 0
        // Arrivals are modelled one minute before the departure from the same stop.
        val scheduled = if (kind == BoardKind.ARRIVALS) boardTime.minusMinutes(1) else boardTime
        return TimetableEntryDto(
            scheduled = scheduled.format(TimeFormats.apiSecond),
            realtime = scheduled.plusSeconds(delay.toLong()).format(TimeFormats.apiSecond),
            delay = delay,
            canceled = profile.isCanceled,
            route = route(line, profile),
            trip = TripDto(
                tripId = tripId(stop, line, boardTime),
                startDate = profile.firstScheduled.toLocalDate().toString(),
                technicalNumber = profile.technicalNumber,
            ),
            agency = AgencyDto(name = line.agency, operator = line.operator),
            stop = StopDto(id = stop.id, name = stop.name, lat = stop.lat, lon = stop.lon),
            scheduledPlatform = line.platform?.let { PlatformDto(designation = it) },
            realtimePlatform = line.platform?.let {
                PlatformDto(designation = if (profile.platformChanged) alternativePlatform(it) else it)
            },
            alerts = listOfNotNull(line.alert),
            isRealtime = profile.isRealtime,
        )
    }

    fun details(stop: DemoStop, line: DemoLine, boardTime: LocalDateTime): TripDetailsResponseDto {
        val profile = profile(stop, line, boardTime)
        val calls = profile.route.mapIndexed { index, stopName ->
            val scheduled = profile.firstScheduled.plusMinutes((index * line.minutesBetweenStops).toLong())
            val delay = if (profile.isRealtime) profile.delays[index] else 0
            val expected = scheduled.plusSeconds(delay.toLong())
            val isFirst = index == 0
            val isLast = index == profile.route.lastIndex
            val isBoardStop = index == profile.boardIndex
            CallDto(
                scheduledDeparture = if (isLast) null else scheduled.format(TimeFormats.apiSecond),
                realtimeDeparture = if (isLast) null else expected.format(TimeFormats.apiSecond),
                departureDelay = if (isLast) null else delay,
                departureCanceled = profile.isCanceled && !isLast,
                scheduledArrival = if (isFirst) null else scheduled.format(TimeFormats.apiSecond),
                realtimeArrival = if (isFirst) null else expected.format(TimeFormats.apiSecond),
                arrivalDelay = if (isFirst) null else delay,
                arrivalCanceled = profile.isCanceled && !isFirst,
                stop = StopDto(
                    id = "${stop.id}$index",
                    name = stopName,
                    lat = stop.lat + (index - profile.boardIndex) * 0.004,
                    lon = stop.lon + (index - profile.boardIndex) * 0.006,
                    areaId = if (isBoardStop) stop.id else null,
                ),
                scheduledPlatform = if (isBoardStop) line.platform?.let { PlatformDto(designation = it) } else null,
                alerts = if (isBoardStop) listOfNotNull(line.alert) else emptyList(),
                isRealtime = profile.isRealtime,
            )
        }
        return TripDetailsResponseDto(
            timestamp = boardTime.format(TimeFormats.apiSecond),
            query = QueryDto(query = tripId(stop, line, boardTime)),
            agency = AgencyDto(name = line.agency, operator = line.operator),
            route = route(line, profile),
            trip = TripDto(
                tripId = tripId(stop, line, boardTime),
                startDate = profile.firstScheduled.toLocalDate().toString(),
                technicalNumber = profile.technicalNumber,
            ),
            calls = calls,
        )
    }

    private fun route(line: DemoLine, profile: Profile) = RouteDto(
        name = line.name,
        designation = line.designation,
        transportModeCode = line.mode.gtfsRouteType(),
        transportMode = line.mode.name,
        direction = line.direction,
        origin = StopRefDto(name = profile.route.first()),
        destination = StopRefDto(name = profile.route.last()),
    )

    private fun profile(stop: DemoStop, line: DemoLine, boardTime: LocalDateTime): Profile {
        val seed = (stop.id.hashCode().toLong() * 31 + line.key.hashCode()) * 1_000_003L +
            boardTime.toEpochSecond(ZoneOffset.UTC) / 60
        val random = Random(seed)
        val route = routeFor(stop, line)
        val boardIndex = route.indexOf(stop.name)
        val isRealtime = random.nextDouble() < realtimeShare(line.reliability)
        val isCanceled = random.nextDouble() < cancelRate(line.reliability)
        val platformChanged = random.nextDouble() < 0.03
        val boardDelay = boardDelay(line.reliability, random)
        return Profile(
            route = route,
            boardIndex = boardIndex,
            firstScheduled = boardTime.minusMinutes((boardIndex * line.minutesBetweenStops).toLong()),
            isRealtime = isRealtime,
            isCanceled = isCanceled,
            platformChanged = platformChanged,
            delays = delayWalk(random, route.size, boardIndex, boardDelay, line.reliability),
            technicalNumber = (abs(seed % 9000) + 1000).toInt(),
        )
    }

    /** The stop sits ~40 % along the route; the rest is a stable pick from the city's pool. */
    private fun routeFor(stop: DemoStop, line: DemoLine): List<String> {
        val others = line.stopPool
            .filter { it != stop.name && it != line.destination }
            .shuffled(Random(line.key.hashCode()))
            .take((line.routeLength - 2).coerceAtLeast(1))
        val boardIndex = (line.routeLength * 0.4).toInt().coerceIn(0, others.size)
        return buildList {
            addAll(others.subList(0, boardIndex))
            add(stop.name)
            addAll(others.subList(boardIndex, others.size))
            add(line.destination)
        }
    }

    private fun realtimeShare(reliability: Reliability) = when (reliability) {
        Reliability.STEADY -> 0.97
        Reliability.NORMAL -> 0.94
        Reliability.TROUBLED -> 0.88
    }

    private fun cancelRate(reliability: Reliability) = when (reliability) {
        Reliability.STEADY -> 0.002
        Reliability.NORMAL -> 0.006
        Reliability.TROUBLED -> 0.025
    }

    /** Roughly 95 / 89 / 50 % on time for steady / normal / troubled lines. */
    private fun boardDelay(reliability: Reliability, random: Random): Int {
        val roll = random.nextDouble()
        return when (reliability) {
            Reliability.STEADY -> when {
                roll < 0.92 -> random.nextInt(-40, 120)
                roll < 0.98 -> random.nextInt(120, 240)
                roll < 0.995 -> random.nextInt(240, 420)
                else -> random.nextInt(-120, -61)
            }
            Reliability.NORMAL -> when {
                roll < 0.88 -> random.nextInt(-45, 160)
                roll < 0.94 -> random.nextInt(160, 300)
                roll < 0.98 -> random.nextInt(300, 600)
                roll < 0.992 -> random.nextInt(600, 840)
                else -> random.nextInt(-150, -61)
            }
            Reliability.TROUBLED -> when {
                roll < 0.50 -> random.nextInt(-30, 170)
                roll < 0.75 -> random.nextInt(170, 420)
                roll < 0.93 -> random.nextInt(420, 780)
                roll < 0.98 -> random.nextInt(780, 1200)
                else -> random.nextInt(-140, -61)
            }
        }
    }

    /**
     * Delay per stop. Up to the board stop the delay builds towards the board value, with one
     * "hotspot" segment taking most of a large increase - the pattern the trip chart highlights.
     */
    private fun delayWalk(random: Random, size: Int, boardIndex: Int, boardDelay: Int, reliability: Reliability): IntArray {
        val delays = IntArray(size)
        if (boardIndex == 0) {
            delays[0] = boardDelay
        } else {
            val start = random.nextInt(-30, 46).coerceAtMost(boardDelay + 30)
            val total = boardDelay - start
            val hotspot = random.nextInt(1, boardIndex + 1)
            val hot = if (total > 150) (total * 0.6).roundToInt() else 0
            val perStep = (total - hot).toDouble() / boardIndex
            delays[0] = start
            for (i in 1 until boardIndex) {
                val jump = if (i >= hotspot) hot else 0
                delays[i] = (start + perStep * i + jump + random.nextInt(-12, 13)).roundToInt()
            }
            delays[boardIndex] = boardDelay
        }
        val drift = when (reliability) {
            Reliability.STEADY -> random.nextInt(-10, 15)
            Reliability.NORMAL -> random.nextInt(-15, 30)
            Reliability.TROUBLED -> random.nextInt(0, 60)
        }
        for (i in boardIndex + 1 until size) {
            delays[i] = delays[i - 1] + drift + random.nextInt(-20, 21)
        }
        return delays
    }

    private fun alternativePlatform(platform: String): String {
        val last = platform.last()
        return if (last.isLetter()) platform.dropLast(1) + (last + 1) else platform + "b"
    }

    private fun TransportMode.gtfsRouteType(): Int = when (this) {
        TransportMode.BUS -> 700
        TransportMode.TRAIN -> 100
        TransportMode.METRO -> 401
        TransportMode.TRAM -> 900
        TransportMode.BOAT -> 1000
        TransportMode.TAXI -> 1500
        TransportMode.UNKNOWN -> 1700
    }
}
