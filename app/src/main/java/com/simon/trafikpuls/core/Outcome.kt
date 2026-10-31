package com.simon.trafikpuls.core

import java.time.Instant

enum class DataOrigin { NETWORK, CACHE, DEMO }

data class FetchMeta(
    val origin: DataOrigin,
    val fetchedAt: Instant,
    /** Set when this is fallback data, served because a fresh fetch failed. */
    val staleReason: AppError? = null,
) {
    val isStale: Boolean get() = staleReason != null
}

/** Result of a repository call: either data (possibly stale, see [FetchMeta]) or a typed error. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T, val meta: FetchMeta) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}
