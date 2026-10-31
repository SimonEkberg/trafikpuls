package com.simon.trafikpuls.data.remote

import com.simon.trafikpuls.data.remote.dto.BoardResponseDto
import com.simon.trafikpuls.data.remote.dto.StopLookupResponseDto
import com.simon.trafikpuls.data.remote.dto.TripDetailsResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Trafiklab Realtime APIs: timetables, trips and stop lookup. */
interface TrafiklabApi {

    @GET("departures/{areaId}")
    suspend fun departures(
        @Path("areaId") areaId: String,
        @Query("key") key: String,
    ): BoardResponseDto

    /** [time] is `yyyy-MM-ddTHH:mm`; the API returns the 60 minutes that follow. */
    @GET("departures/{areaId}/{time}")
    suspend fun departuresAt(
        @Path("areaId") areaId: String,
        @Path("time") time: String,
        @Query("key") key: String,
    ): BoardResponseDto

    @GET("arrivals/{areaId}")
    suspend fun arrivals(
        @Path("areaId") areaId: String,
        @Query("key") key: String,
    ): BoardResponseDto

    @GET("arrivals/{areaId}/{time}")
    suspend fun arrivalsAt(
        @Path("areaId") areaId: String,
        @Path("time") time: String,
        @Query("key") key: String,
    ): BoardResponseDto

    @GET("trips/{tripId}/{startDate}")
    suspend fun trip(
        @Path("tripId") tripId: String,
        @Path("startDate") startDate: String,
        @Query("key") key: String,
    ): TripDetailsResponseDto

    @GET("stops/name/{query}")
    suspend fun searchStops(
        @Path("query") query: String,
        @Query("key") key: String,
    ): StopLookupResponseDto
}
