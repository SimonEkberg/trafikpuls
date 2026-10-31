package com.simon.trafikpuls.core

import com.simon.trafikpuls.testutil.MutableClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Duration
import java.time.Instant

class CoreTest {

    @Test
    fun `rate limiter uses a sliding window`() {
        val clock = MutableClock(Instant.parse("2025-04-01T12:00:00Z"))
        val limiter = RateLimiter(maxRequests = 2, windowMillis = 60_000, clock = clock)
        assertTrue(limiter.tryAcquire())
        clock.advance(Duration.ofSeconds(30))
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())
        clock.advance(Duration.ofSeconds(31)) // the first call has left the window
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())
    }

    @Test
    fun `transient errors are retried until success`() = runTest {
        var calls = 0
        val result = retryTransient(RetryPolicy(maxRetries = 2)) {
            calls++
            if (calls < 3) throw IOException("flaky") else "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, calls)
    }

    @Test
    fun `client errors are not retried`() = runTest {
        var calls = 0
        try {
            retryTransient(RetryPolicy(maxRetries = 2)) {
                calls++
                throw MissingApiKeyException()
            }
            fail("expected exception")
        } catch (expected: MissingApiKeyException) {
            assertEquals(1, calls)
        }
    }

    @Test
    fun `backoff grows and is capped`() {
        val policy = RetryPolicy(initialDelayMillis = 1_000, factor = 2.0, maxDelayMillis = 3_000)
        val random = kotlin.random.Random(1)
        val first = policy.delayFor(0, random)
        val capped = policy.delayFor(5, random)
        assertTrue(first in 800..1_200)
        assertTrue(capped in 2_400..3_600)
    }

    @Test
    fun `http and io errors map to app errors`() {
        assertEquals(AppError.InvalidApiKey, ErrorMapper.fromHttp(401))
        assertEquals(AppError.InvalidApiKey, ErrorMapper.fromHttp(403))
        assertEquals(AppError.NotFound, ErrorMapper.fromHttp(404))
        assertEquals(AppError.RateLimited(retryAfterSeconds = 12), ErrorMapper.fromHttp(429, "12"))
        assertEquals(AppError.Server(502), ErrorMapper.fromHttp(502))
        assertEquals(AppError.BadRequest(400), ErrorMapper.fromHttp(400))
        assertEquals(AppError.Network(timeout = true), ErrorMapper.map(SocketTimeoutException()))
        assertEquals(AppError.Network(), ErrorMapper.map(IOException()))
        assertEquals(AppError.NotFound, ErrorMapper.map(DataNotFoundException("x")))
    }

    @Test
    fun `api timestamps parse with and without offset`() {
        assertEquals("2025-04-01T14:30", parseApiDateTime("2025-04-01T14:30:00").toString())
        assertEquals("2025-04-01T14:30", parseApiDateTime("2025-04-01T12:30:00Z").toString())
        assertEquals(null, parseApiDateTime("not a date"))
        assertEquals("2025-04-01", parseApiDate("20250401").toString())
    }
}
