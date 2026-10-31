package com.simon.trafikpuls.data.live

import com.simon.trafikpuls.core.AppError
import com.simon.trafikpuls.core.RetryPolicy
import com.simon.trafikpuls.data.local.WatchedStopDao
import com.simon.trafikpuls.data.local.WatchedStopEntity
import com.simon.trafikpuls.data.remote.NetworkModule
import com.simon.trafikpuls.data.remote.dto.BoardResponseDto
import com.simon.trafikpuls.data.remote.dto.StopLookupResponseDto
import com.simon.trafikpuls.data.remote.dto.TripDetailsResponseDto
import com.simon.trafikpuls.data.repository.TrafficRepository
import com.simon.trafikpuls.data.repository.WatchlistRepository
import com.simon.trafikpuls.data.settings.AppSettings
import com.simon.trafikpuls.data.source.TrafiklabDataSource
import com.simon.trafikpuls.domain.model.BoardKind
import com.simon.trafikpuls.testutil.FakeBoardCacheDao
import com.simon.trafikpuls.testutil.MutableClock
import com.simon.trafikpuls.testutil.fixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistMonitorTest {

    private val clock = MutableClock(Instant.parse("2025-04-01T12:22:00Z"))
    private val remote = FakeDataSource()
    private val dao = FakeWatchedStopDao()
    private val lineFilters = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    private fun TestScope.monitor(): WatchlistMonitor {
        val traffic = TrafficRepository(
            remote = remote,
            demo = remote,
            isDemoMode = { false },
            boardCache = FakeBoardCacheDao(),
            json = NetworkModule.json,
            clock = clock,
            retryPolicy = RetryPolicy(maxRetries = 0),
        )
        val watchlist = WatchlistRepository(dao, flowOf(false), { false }, clock)
        return WatchlistMonitor(watchlist, traffic, flowOf(AppSettings(demoMode = false)), lineFilters, backgroundScope, clock)
    }

    private fun stop(areaId: String) = WatchedStopEntity(areaId, false, "Stop $areaId", "BUS", 0, 0)

    @Test
    fun `a failed refresh does not keep showing an expired board`() = runTest {
        dao.insert(stop("740000002"))
        val monitor = monitor()
        monitor.refresh()
        runCurrent()
        assertNotNull(monitor.snapshot.value.stops.single().board)
        val firstRefresh = monitor.snapshot.value.lastRefreshAt

        clock.advance(Duration.ofMinutes(31)) // older than the repository's stale limit
        remote.failure = IOException("offline")
        monitor.refresh()
        runCurrent()

        val stop = monitor.snapshot.value.stops.single()
        assertNull(stop.board)
        assertEquals(AppError.Network(), stop.error)
        assertEquals(firstRefresh, monitor.snapshot.value.lastRefreshAt) // nothing succeeded
    }

    @Test
    fun `a failure shortly after a success shows the cached board flagged as stale`() = runTest {
        dao.insert(stop("740000002"))
        val monitor = monitor()
        monitor.refresh()

        clock.advance(Duration.ofMinutes(2))
        remote.failure = IOException("offline")
        monitor.refresh()
        runCurrent()

        val stop = monitor.snapshot.value.stops.single()
        assertNotNull(stop.board)
        assertEquals(AppError.Network(), stop.meta?.staleReason)
    }

    @Test
    fun `adding several stops quickly costs one round of requests`() = runTest {
        monitor()
        runCurrent()

        dao.insert(stop("1"))
        dao.insert(stop("2"))
        dao.insert(stop("3"))
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(3, remote.boardCalls)
    }

    @Test
    fun `followed lines narrow the board everywhere the stop is shown`() = runTest {
        dao.insert(stop("740000002"))
        val monitor = monitor()
        monitor.refresh()
        runCurrent()
        assertEquals(6, monitor.snapshot.value.stops.single().board!!.entries.size)

        lineFilters.value = mapOf("740000002" to setOf("BUS|16|Eketrägatan"))
        runCurrent()

        val stop = monitor.snapshot.value.stops.single()
        assertEquals(listOf("16"), stop.board!!.entries.map { it.line.designation })
        assertEquals(setOf("BUS|16|Eketrägatan"), stop.lineFilter)
    }

    private class FakeDataSource : TrafiklabDataSource {
        var failure: Exception? = null
        var boardCalls = 0

        override suspend fun board(kind: BoardKind, areaId: String, at: LocalDateTime?): BoardResponseDto {
            boardCalls++
            failure?.let { throw it }
            return NetworkModule.json.decodeFromString(fixture("departures.json"))
        }

        override suspend fun trip(tripId: String, startDate: LocalDate): TripDetailsResponseDto = error("unused")

        override suspend fun searchStops(query: String): StopLookupResponseDto = error("unused")
    }

    private class FakeWatchedStopDao : WatchedStopDao {
        private val rows = MutableStateFlow<List<WatchedStopEntity>>(emptyList())

        override fun observe(demo: Boolean): Flow<List<WatchedStopEntity>> = rows.map { list -> list.filter { it.demo == demo } }

        override suspend fun getAll(demo: Boolean) = rows.value.filter { it.demo == demo }

        override suspend fun count(demo: Boolean) = getAll(demo).size

        override suspend fun maxPosition(demo: Boolean) = getAll(demo).maxOfOrNull { it.position } ?: -1

        override suspend fun insert(entity: WatchedStopEntity): Long {
            if (rows.value.any { it.areaId == entity.areaId && it.demo == entity.demo }) return -1
            rows.value = rows.value + entity
            return rows.value.size.toLong()
        }

        override suspend fun delete(areaId: String, demo: Boolean) {
            rows.value = rows.value.filterNot { it.areaId == areaId && it.demo == demo }
        }
    }
}
