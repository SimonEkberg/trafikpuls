package com.simon.trafikpuls.data.source

import com.simon.trafikpuls.core.LocalRateLimitException
import com.simon.trafikpuls.core.MissingApiKeyException
import com.simon.trafikpuls.core.RateLimiter
import com.simon.trafikpuls.core.TimeFormats
import com.simon.trafikpuls.data.remote.TrafiklabApi
import com.simon.trafikpuls.data.remote.dto.BoardResponseDto
import com.simon.trafikpuls.data.remote.dto.StopLookupResponseDto
import com.simon.trafikpuls.data.remote.dto.TripDetailsResponseDto
import com.simon.trafikpuls.domain.model.BoardKind
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Raw access to Trafiklab data. Implemented by the real API and by the offline demo source,
 * which produce the same wire format so both go through identical mapping code.
 */
interface TrafiklabDataSource {
    /** [at] = start of the 60-minute window; null means "now". */
    suspend fun board(kind: BoardKind, areaId: String, at: LocalDateTime?): BoardResponseDto
    suspend fun trip(tripId: String, startDate: LocalDate): TripDetailsResponseDto
    suspend fun searchStops(query: String): StopLookupResponseDto
}

/**
 * The real API. The [rateLimiter] is checked here, once per HTTP request actually sent - so
 * retries count against the quota and a missing key costs nothing.
 */
class RemoteTrafiklabDataSource(
    private val api: TrafiklabApi,
    private val rateLimiter: RateLimiter,
    private val apiKey: suspend () -> String,
) : TrafiklabDataSource {

    override suspend fun board(kind: BoardKind, areaId: String, at: LocalDateTime?): BoardResponseDto = request { key ->
        val time = at?.format(TimeFormats.apiMinute)
        when (kind) {
            BoardKind.DEPARTURES ->
                if (time == null) api.departures(areaId, key) else api.departuresAt(areaId, time, key)
            BoardKind.ARRIVALS ->
                if (time == null) api.arrivals(areaId, key) else api.arrivalsAt(areaId, time, key)
        }
    }

    override suspend fun trip(tripId: String, startDate: LocalDate): TripDetailsResponseDto = request { key ->
        api.trip(tripId, startDate.toString(), key)
    }

    override suspend fun searchStops(query: String): StopLookupResponseDto = request { key ->
        api.searchStops(query.trim(), key)
    }

    private suspend fun <T> request(block: suspend (key: String) -> T): T {
        val key = apiKey().trim().ifEmpty { throw MissingApiKeyException() }
        if (!rateLimiter.tryAcquire()) throw LocalRateLimitException()
        return block(key)
    }
}
