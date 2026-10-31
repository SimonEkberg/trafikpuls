package com.simon.trafikpuls.core

import java.time.Clock

/**
 * Client-side guard for the Trafiklab quota (Bronze level: 25 calls/minute). A sliding window
 * kept a little below the server limit means the app degrades to cached data instead of
 * collecting 429 responses.
 */
class RateLimiter(
    private val maxRequests: Int,
    private val windowMillis: Long,
    private val clock: Clock,
) {
    private val timestamps = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = clock.millis()
        while (timestamps.isNotEmpty() && now - timestamps.first() >= windowMillis) {
            timestamps.removeFirst()
        }
        if (timestamps.size >= maxRequests) return false
        timestamps.addLast(now)
        return true
    }
}
