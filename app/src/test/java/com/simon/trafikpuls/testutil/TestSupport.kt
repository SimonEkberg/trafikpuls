package com.simon.trafikpuls.testutil

import com.simon.trafikpuls.core.SwedishZone
import com.simon.trafikpuls.data.local.BoardCacheDao
import com.simon.trafikpuls.data.local.BoardCacheEntity
import com.simon.trafikpuls.domain.model.Alert
import com.simon.trafikpuls.domain.model.Line
import com.simon.trafikpuls.domain.model.TimetableEntry
import com.simon.trafikpuls.domain.model.TransportMode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** A clock tests can move forward. */
class MutableClock(
    private var now: Instant,
    private val zone: ZoneId = SwedishZone,
) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = Snapshot(now, zone)
    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    private class Snapshot(private val now: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = Snapshot(now, zone)
        override fun instant(): Instant = now
    }
}

class FakeBoardCacheDao : BoardCacheDao {
    val entries = mutableMapOf<String, BoardCacheEntity>()

    override suspend fun get(key: String): BoardCacheEntity? = entries[key]

    override suspend fun put(entity: BoardCacheEntity) {
        entries[entity.cacheKey] = entity
    }

    override suspend fun deleteOlderThan(olderThanEpochMs: Long): Int {
        val before = entries.size
        entries.values.removeAll { it.fetchedAtEpochMs < olderThanEpochMs }
        return before - entries.size
    }
}

fun fixture(name: String): String =
    requireNotNull(FakeBoardCacheDao::class.java.classLoader?.getResource("fixtures/$name")) { "Missing fixture $name" }
        .readText(Charsets.UTF_8)

val BASE_TIME: LocalDateTime = LocalDateTime.of(2025, 4, 1, 14, 30)

fun entry(
    delay: Int = 0,
    realtime: Boolean = true,
    canceled: Boolean = false,
    operator: String? = "Nobina Sverige AB",
    agency: String? = "Skånetrafiken",
    line: String = "3",
    mode: TransportMode = TransportMode.BUS,
    scheduled: LocalDateTime = BASE_TIME,
    alerts: List<Alert> = emptyList(),
    stopId: String = "3",
    stopName: String = "Malmö Centralstation",
): TimetableEntry = TimetableEntry(
    tripId = "trip-$line-$scheduled",
    startDate = scheduled.toLocalDate(),
    technicalNumber = 1,
    stopId = stopId,
    stopName = stopName,
    scheduled = scheduled,
    expected = scheduled.plusSeconds(delay.toLong()),
    delaySeconds = delay,
    isCanceled = canceled,
    isRealtime = realtime,
    line = Line(line, null, mode, "Mot centrum", null, null),
    agencyName = agency,
    operatorName = operator,
    scheduledPlatform = "A",
    realtimePlatform = "A",
    alerts = alerts,
    advanceOrderMessage = null,
)
