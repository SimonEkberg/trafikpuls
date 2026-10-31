@file:OptIn(ExperimentalSerializationApi::class)

package com.simon.trafikpuls.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

// Wire format of the Trafiklab Realtime APIs (https://realtime-api.trafiklab.se/v1).
// Every field the app can live without has a default: the API may add or omit fields
// without notice, and one odd departure must never break a whole board.

@Serializable
data class BoardResponseDto(
    val timestamp: String? = null,
    val query: QueryDto? = null,
    val stops: List<RealtimeStopDto> = emptyList(),
    val departures: List<TimetableEntryDto>? = null,
    val arrivals: List<TimetableEntryDto>? = null,
)

@Serializable
data class QueryDto(
    val queryTime: String? = null,
    val query: String? = null,
)

@Serializable
data class RealtimeStopDto(
    val id: String = "",
    val name: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("transport_modes") val transportModes: List<String> = emptyList(),
    val alerts: List<AlertDto> = emptyList(),
)

@Serializable
data class TimetableEntryDto(
    val scheduled: String? = null,
    val realtime: String? = null,
    val delay: Int = 0,
    val canceled: Boolean = false,
    val route: RouteDto? = null,
    val trip: TripDto? = null,
    val agency: AgencyDto? = null,
    val stop: StopDto? = null,
    @SerialName("scheduled_platform") val scheduledPlatform: PlatformDto? = null,
    @SerialName("realtime_platform") val realtimePlatform: PlatformDto? = null,
    val alerts: List<AlertDto> = emptyList(),
    val advanceOrder: AdvanceOrderDto? = null,
    @SerialName("is_realtime") val isRealtime: Boolean = false,
)

@Serializable
data class RouteDto(
    val name: String? = null,
    val designation: String? = null,
    @SerialName("transport_mode_code") val transportModeCode: Int? = null,
    @SerialName("transport_mode") val transportMode: String? = null,
    val direction: String? = null,
    val origin: StopRefDto? = null,
    val destination: StopRefDto? = null,
)

@Serializable
data class StopRefDto(
    val id: String? = null,
    val name: String? = null,
)

@Serializable
data class TripDto(
    @SerialName("trip_id") val tripId: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("technical_number") val technicalNumber: Int? = null,
)

@Serializable
data class AgencyDto(
    val id: String? = null,
    val name: String? = null,
    val operator: String? = null,
)

@Serializable
data class StopDto(
    val id: String = "",
    val name: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("area_id") val areaId: String? = null,
)

@Serializable
data class PlatformDto(
    val id: String? = null,
    val designation: String? = null,
)

@Serializable
data class AlertDto(
    val type: String? = null,
    val title: String? = null,
    val text: String? = null,
)

@Serializable
data class AdvanceOrderDto(
    val message: String? = null,
)

@Serializable
data class TripDetailsResponseDto(
    val timestamp: String? = null,
    val query: QueryDto? = null,
    val agency: AgencyDto? = null,
    val route: RouteDto? = null,
    val trip: TripDto? = null,
    val calls: List<CallDto> = emptyList(),
)

@Serializable
data class CallDto(
    val scheduledDeparture: String? = null,
    val realtimeDeparture: String? = null,
    val departureDelay: Int? = null,
    val departureCanceled: Boolean = false,
    val scheduledArrival: String? = null,
    val realtimeArrival: String? = null,
    val arrivalDelay: Int? = null,
    val arrivalCanceled: Boolean = false,
    val stop: StopDto = StopDto(),
    @SerialName("scheduled_platform") val scheduledPlatform: PlatformDto? = null,
    @SerialName("realtime_platform") val realtimePlatform: PlatformDto? = null,
    val alerts: List<AlertDto> = emptyList(),
    val advanceOrder: AdvanceOrderDto? = null,
    @SerialName("is_realtime") val isRealtime: Boolean = false,
)

@Serializable
data class StopLookupResponseDto(
    val timestamp: String? = null,
    val query: QueryDto? = null,
    @SerialName("stop_groups") val stopGroups: List<StopGroupDto> = emptyList(),
)

@Serializable
data class StopGroupDto(
    val id: String = "",
    val name: String = "",
    // The documentation example says "area_type", the OpenAPI spec says "group_type": accept both.
    @SerialName("area_type") @JsonNames("group_type") val areaType: String? = null,
    @SerialName("average_daily_stop_times") val averageDailyStopTimes: Double? = null,
    @SerialName("transport_modes") val transportModes: List<String> = emptyList(),
    val stops: List<StopDto> = emptyList(),
)
