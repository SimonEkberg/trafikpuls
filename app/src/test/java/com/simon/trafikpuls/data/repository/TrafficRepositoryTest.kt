package com.simon.trafikpuls.data.repository

import com.simon.trafikpuls.core.AppError
import com.simon.trafikpuls.core.DataOrigin
import com.simon.trafikpuls.core.Outcome
import com.simon.trafikpuls.core.RateLimiter
import com.simon.trafikpuls.core.RetryPolicy
import com.simon.trafikpuls.data.demo.DemoTrafiklabDataSource
import com.simon.trafikpuls.data.remote.NetworkModule
import com.simon.trafikpuls.data.source.RemoteTrafiklabDataSource
import com.simon.trafikpuls.domain.model.BoardKind
import com.simon.trafikpuls.domain.model.TimeWindow
import com.simon.trafikpuls.testutil.FakeBoardCacheDao
import com.simon.trafikpuls.testutil.MutableClock
import com.simon.trafikpuls.testutil.fixture
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** The repository against a real HTTP stack (MockWebServer), covering the robustness rules. */
class TrafficRepositoryTest {

    private lateinit var server: MockWebServer
    private val clock = MutableClock(Instant.parse("2025-04-01T12:22:00Z")) // 14:22 in Sweden
    private val cache = FakeBoardCacheDao()
    private var apiKey = "test-key"
    private var demoMode = false

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repository(rateLimit: Int = 20) = TrafficRepository(
        remote = RemoteTrafiklabDataSource(
            api = NetworkModule.trafiklabApi(
                NetworkModule.okHttpClient(debugLogging = false, userAgent = "test"),
                server.url("/v1/").toString(),
            ),
            rateLimiter = RateLimiter(rateLimit, 60_000, clock),
            apiKey = { apiKey },
        ),
        demo = DemoTrafiklabDataSource(clock, latencyMillis = 0),
        isDemoMode = { demoMode },
        boardCache = cache,
        json = NetworkModule.json,
        clock = clock,
        retryPolicy = RetryPolicy(maxRetries = 2, initialDelayMillis = 1),
    )

    private fun okBoard() = MockResponse().setBody(fixture("departures.json"))

    @Test
    fun `successful board is mapped, cached and sends the key as query parameter`() = runTest {
        server.enqueue(okBoard())

        val outcome = repository().board("740000002") as Outcome.Success

        assertEquals(DataOrigin.NETWORK, outcome.meta.origin)
        assertEquals(6, outcome.value.entries.size)
        assertEquals("/v1/departures/740000002?key=test-key", server.takeRequest().path)
        assertNotNull(cache.entries["DEPARTURES:UPCOMING:740000002"])
    }

    @Test
    fun `fresh cache is served without a new request`() = runTest {
        server.enqueue(okBoard())
        val repository = repository()
        repository.board("740000002")
        clock.advance(Duration.ofSeconds(10))

        val second = repository.board("740000002") as Outcome.Success

        assertEquals(DataOrigin.CACHE, second.meta.origin)
        assertEquals(false, second.meta.isStale)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `transient server errors are retried`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(okBoard())

        assertTrue(repository().board("740000002") is Outcome.Success)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `dropped connections are retried`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(okBoard())

        assertTrue(repository().board("740000002") is Outcome.Success)
    }

    @Test
    fun `an invalid key is not retried`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(Outcome.Failure(AppError.InvalidApiKey), repository().board("740000002"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `quota exceeded carries Retry-After`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))

        assertEquals(Outcome.Failure(AppError.RateLimited(retryAfterSeconds = 30)), repository().board("740000002"))
    }

    @Test
    fun `when the API fails the last good board is served as stale`() = runTest {
        server.enqueue(okBoard())
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) }
        val repository = repository()
        repository.board("740000002")
        clock.advance(Duration.ofMinutes(2))

        val outcome = repository.board("740000002") as Outcome.Success

        assertEquals(DataOrigin.CACHE, outcome.meta.origin)
        assertEquals(AppError.Server(500), outcome.meta.staleReason)
        assertEquals(6, outcome.value.entries.size)
    }

    @Test
    fun `cache older than the stale limit is not shown`() = runTest {
        server.enqueue(okBoard())
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) }
        val repository = repository()
        repository.board("740000002")
        clock.advance(Duration.ofMinutes(31))

        assertEquals(Outcome.Failure(AppError.Server(500)), repository.board("740000002"))
    }

    @Test
    fun `malformed json is a parse error`() = runTest {
        server.enqueue(MockResponse().setBody("{ this is not json"))

        val outcome = repository().board("740000002") as Outcome.Failure
        assertTrue(outcome.error is AppError.Parse)
    }

    @Test
    fun `missing key fails fast without any request`() = runTest {
        apiKey = " "

        assertEquals(Outcome.Failure(AppError.MissingApiKey), repository().board("740000002"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the client-side rate limiter protects the quota and falls back to cache`() = runTest {
        server.enqueue(okBoard())
        val repository = repository(rateLimit = 1)
        repository.board("740000002")
        clock.advance(Duration.ofSeconds(45)) // cache no longer fresh, rate window still full

        val outcome = repository.board("740000002") as Outcome.Success

        assertEquals(AppError.RateLimited(localThrottle = true), outcome.meta.staleReason)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `retries count against the rate limit`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503)) }

        val outcome = repository(rateLimit = 2).board("740000002")

        assertEquals(Outcome.Failure(AppError.RateLimited(localThrottle = true)), outcome)
        assertEquals(2, server.requestCount) // the third attempt was stopped before the network
    }

    @Test
    fun `a missing key does not use up the rate limit`() = runTest {
        server.enqueue(okBoard())
        val repository = repository(rateLimit = 1)
        apiKey = ""
        repository.board("740000002")
        apiKey = "test-key"

        assertTrue(repository.board("740000002") is Outcome.Success)
    }

    @Test
    fun `past hour window sends a start time`() = runTest {
        server.enqueue(okBoard())

        repository().board("740000002", BoardKind.DEPARTURES, TimeWindow.PAST_HOUR)

        assertEquals("/v1/departures/740000002/2025-04-01T13:22?key=test-key", server.takeRequest().path)
    }

    @Test
    fun `trip and stop search hit the documented endpoints`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("trip.json")))
        server.enqueue(MockResponse().setBody(fixture("stops.json")))
        val repository = repository()

        val trip = repository.trip("121120000372401393", LocalDate.of(2026, 1, 20)) as Outcome.Success
        val stops = repository.searchStops("sto") as Outcome.Success

        assertEquals(6, trip.value.calls.size)
        assertEquals(2, stops.value.size)
        assertEquals("/v1/trips/121120000372401393/2026-01-20?key=test-key", server.takeRequest().path)
        assertEquals("/v1/stops/name/sto?key=test-key", server.takeRequest().path)
    }

    @Test
    fun `demo mode never touches the network`() = runTest {
        demoMode = true

        val outcome = repository().board("740000003") as Outcome.Success

        assertEquals(DataOrigin.DEMO, outcome.meta.origin)
        assertTrue(outcome.value.entries.isNotEmpty())
        assertEquals(0, server.requestCount)
    }
}
