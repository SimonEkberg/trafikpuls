package com.simon.trafikpuls.domain

import com.simon.trafikpuls.domain.model.TripDetails
import java.time.LocalDateTime

data class DelayPoint(
    val index: Int,
    val stopName: String,
    val delaySeconds: Int,
    val isRealtime: Boolean,
    val isCanceled: Boolean,
    val time: LocalDateTime?,
)

/** Delay change between two consecutive stops - where on the route time was lost. */
data class DelaySegment(
    val fromIndex: Int,
    val toIndex: Int,
    val fromStop: String,
    val toStop: String,
    val deltaSeconds: Int,
)

data class TripInsights(
    val points: List<DelayPoint>,
    /** Index of the latest stop the vehicle has passed (by expected time), if any. */
    val lastPassedIndex: Int?,
    val currentDelaySeconds: Int?,
    /** Predicted (or actual) arrival delay at the terminus. */
    val finalDelaySeconds: Int?,
    val maxDelay: DelayPoint?,
    val worstSegment: DelaySegment?,
    val canceledStops: Int,
    val realtimeShare: Double?,
)

object TripAnalyzer {
    /** Smaller increases are noise in the predictions, not something to act on. */
    const val MIN_SEGMENT_INCREASE_SECONDS = 60

    fun analyze(trip: TripDetails, now: LocalDateTime): TripInsights {
        val points = trip.calls.mapIndexed { index, call ->
            DelayPoint(
                index = index,
                stopName = call.stopName,
                delaySeconds = call.delaySeconds ?: 0,
                isRealtime = call.isRealtime,
                isCanceled = call.isCanceled,
                time = call.expectedTime,
            )
        }
        val usable = points.filter { it.isRealtime && !it.isCanceled }

        val lastPassed = trip.calls
            .indexOfLast { call -> call.expectedTime?.let { !it.isAfter(now) } == true }
            .takeIf { it >= 0 }
        val currentDelay = lastPassed
            ?.let { points[it] }
            ?.takeIf { it.isRealtime && !it.isCanceled }
            ?.delaySeconds

        val lastCall = trip.calls.lastOrNull()
        val finalDelay = lastCall
            ?.takeIf { it.isRealtime && !it.isCanceled }
            ?.let { it.arrivalDelaySeconds ?: it.delaySeconds }

        val worstSegment = points.zipWithNext()
            .filter { (a, b) -> a.isRealtime && b.isRealtime && !a.isCanceled && !b.isCanceled }
            .map { (a, b) -> DelaySegment(a.index, b.index, a.stopName, b.stopName, b.delaySeconds - a.delaySeconds) }
            .maxByOrNull { it.deltaSeconds }
            ?.takeIf { it.deltaSeconds >= MIN_SEGMENT_INCREASE_SECONDS }

        return TripInsights(
            points = points,
            lastPassedIndex = lastPassed,
            currentDelaySeconds = currentDelay,
            finalDelaySeconds = finalDelay,
            maxDelay = usable.maxByOrNull { it.delaySeconds }?.takeIf { it.delaySeconds > 0 },
            worstSegment = worstSegment,
            canceledStops = points.count { it.isCanceled },
            realtimeShare = if (points.isEmpty()) null else points.count { it.isRealtime }.toDouble() / points.size,
        )
    }
}
