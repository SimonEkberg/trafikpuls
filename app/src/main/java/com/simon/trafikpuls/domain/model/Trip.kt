package com.simon.trafikpuls.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class TripDetails(
    val tripId: String,
    val startDate: LocalDate,
    val technicalNumber: Int?,
    val line: Line,
    val agencyName: String?,
    val operatorName: String?,
    val calls: List<TripCall>,
) {
    val operatorLabel: String
        get() = operatorName?.takeIf { it.isNotBlank() }
            ?: agencyName?.takeIf { it.isNotBlank() }
            ?: TimetableEntry.UNKNOWN_OPERATOR
}

/** One stop along a trip. The first call has no arrival and the last no departure in practice. */
data class TripCall(
    val stopId: String,
    val areaId: String?,
    val stopName: String,
    val scheduledArrival: LocalDateTime?,
    val expectedArrival: LocalDateTime?,
    val arrivalDelaySeconds: Int?,
    val arrivalCanceled: Boolean,
    val scheduledDeparture: LocalDateTime?,
    val expectedDeparture: LocalDateTime?,
    val departureDelaySeconds: Int?,
    val departureCanceled: Boolean,
    val platform: String?,
    val isRealtime: Boolean,
    val alerts: List<Alert>,
) {
    val scheduledTime: LocalDateTime? get() = scheduledDeparture ?: scheduledArrival
    val expectedTime: LocalDateTime? get() = expectedDeparture ?: expectedArrival ?: scheduledTime

    /** Departure delay is the operational figure; the terminus only has an arrival. */
    val delaySeconds: Int? get() = departureDelaySeconds ?: arrivalDelaySeconds

    /** The stop is skipped when every part of the call that exists is canceled. */
    val isCanceled: Boolean
        get() = (departureCanceled || arrivalCanceled) &&
            (scheduledDeparture == null || departureCanceled) &&
            (scheduledArrival == null || arrivalCanceled)
}
