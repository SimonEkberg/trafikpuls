package com.simon.trafikpuls.core

import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException

object ErrorMapper {

    fun map(error: Throwable): AppError = when (error) {
        is MissingApiKeyException -> AppError.MissingApiKey
        is DataNotFoundException -> AppError.NotFound
        is LocalRateLimitException -> AppError.RateLimited(localThrottle = true)
        is HttpException -> fromHttp(error.code(), error.response()?.headers()?.get("Retry-After"))
        // SocketTimeoutException is an InterruptedIOException.
        is InterruptedIOException -> AppError.Network(timeout = true)
        is IOException -> AppError.Network()
        is SerializationException -> AppError.Parse(error.message)
        else -> AppError.Unexpected(error.message ?: error::class.java.simpleName)
    }

    fun fromHttp(code: Int, retryAfter: String? = null): AppError = when (code) {
        401, 403 -> AppError.InvalidApiKey
        404 -> AppError.NotFound
        429 -> AppError.RateLimited(retryAfterSeconds = retryAfter?.trim()?.toLongOrNull())
        in 500..599 -> AppError.Server(code)
        else -> AppError.BadRequest(code)
    }
}
