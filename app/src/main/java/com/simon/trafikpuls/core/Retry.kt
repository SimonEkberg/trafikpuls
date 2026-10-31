package com.simon.trafikpuls.core

import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.pow
import kotlin.random.Random

data class RetryPolicy(
    val maxRetries: Int = 2,
    val initialDelayMillis: Long = 600,
    val factor: Double = 2.5,
    val maxDelayMillis: Long = 5_000,
) {
    /** Exponential backoff with +-20 % jitter so parallel callers don't retry in lockstep. */
    fun delayFor(attempt: Int, random: Random = Random.Default): Long {
        val base = (initialDelayMillis * factor.pow(attempt)).toLong().coerceAtMost(maxDelayMillis)
        val jitter = (base * 0.2 * (random.nextDouble() * 2 - 1)).toLong()
        return (base + jitter).coerceAtLeast(0)
    }

    companion object {
        val Default = RetryPolicy()
    }
}

/** Runs [block], retrying only failures that [ErrorMapper] classifies as transient. */
suspend fun <T> retryTransient(policy: RetryPolicy, block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (attempt >= policy.maxRetries || !ErrorMapper.map(e).isRetryable) throw e
            delay(policy.delayFor(attempt))
            attempt++
        }
    }
}
