package com.simon.trafikpuls.core

/**
 * Everything that can go wrong when fetching traffic data. Transport-level exceptions are
 * mapped to these by [ErrorMapper] so the UI only ever deals with a closed set of cases.
 */
sealed interface AppError {
    data object MissingApiKey : AppError
    data object InvalidApiKey : AppError
    data class RateLimited(val retryAfterSeconds: Long? = null, val localThrottle: Boolean = false) : AppError
    data object NotFound : AppError
    data class BadRequest(val code: Int) : AppError
    data class Server(val code: Int) : AppError
    data class Network(val timeout: Boolean = false) : AppError
    data class Parse(val detail: String?) : AppError
    data class Unexpected(val detail: String?) : AppError

    /** Transient failures are worth an automatic retry; client errors and quota hits are not. */
    val isRetryable: Boolean
        get() = this is Network || this is Server
}

/** Thrown by a data source when no API key is configured, before any request is made. */
class MissingApiKeyException : IllegalStateException("No Trafiklab API key configured")

/** Thrown by a data source when the requested stop or trip does not exist. */
class DataNotFoundException(message: String) : Exception(message)

/** Thrown instead of sending a request when the client-side quota guard has no tokens left. */
class LocalRateLimitException : Exception("Client-side rate limit reached")
