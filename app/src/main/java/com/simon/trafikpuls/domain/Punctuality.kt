package com.simon.trafikpuls.domain

import com.simon.trafikpuls.domain.model.TimetableEntry

/**
 * Thresholds that turn raw delays into operational statuses. "On time" means no more than
 * [lateThresholdSeconds] late and no more than [earlyThresholdSeconds] early - an early bus
 * leaves passengers behind, so it counts against punctuality just like a late one.
 */
data class PunctualityPolicy(
    val lateThresholdSeconds: Int = 180,
    val earlyThresholdSeconds: Int = 60,
    val severeDelaySeconds: Int = 600,
    val warningPunctuality: Double = 0.90,
    val criticalPunctuality: Double = 0.75,
    /** Share of canceled or severely late departures that makes a stop critical. */
    val criticalShare: Double = 0.05,
) {
    fun classify(entry: TimetableEntry): DepartureStatus =
        classify(entry.delaySeconds, entry.isRealtime, entry.isCanceled)

    fun classify(delaySeconds: Int, isRealtime: Boolean, isCanceled: Boolean): DepartureStatus = when {
        isCanceled -> DepartureStatus.CANCELED
        !isRealtime -> DepartureStatus.NO_REALTIME
        delaySeconds >= severeDelaySeconds -> DepartureStatus.SEVERELY_LATE
        delaySeconds > lateThresholdSeconds -> DepartureStatus.LATE
        delaySeconds < -earlyThresholdSeconds -> DepartureStatus.EARLY
        else -> DepartureStatus.ON_TIME
    }

    /**
     * Rates rather than counts, so a busy hub with one cancellation among 60 departures shows
     * "deviations" while a stop where every twentieth departure is canceled shows "disrupted".
     */
    fun health(kpis: BoardKpis): HealthStatus {
        if (kpis.total == 0) return HealthStatus.UNKNOWN
        val punctuality = kpis.punctuality
        val canceledShare = kpis.canceled.toDouble() / kpis.total
        val severeShare = if (kpis.tracked == 0) 0.0 else kpis.severelyLate.toDouble() / kpis.tracked
        return when {
            punctuality != null && punctuality < criticalPunctuality -> HealthStatus.CRITICAL
            canceledShare >= criticalShare || severeShare >= criticalShare -> HealthStatus.CRITICAL
            punctuality != null && punctuality < warningPunctuality -> HealthStatus.WARNING
            kpis.canceled > 0 || kpis.severelyLate > 0 -> HealthStatus.WARNING
            punctuality == null -> HealthStatus.UNKNOWN
            else -> HealthStatus.OK
        }
    }
}

enum class DepartureStatus(val severity: Int) {
    CANCELED(4),
    SEVERELY_LATE(3),
    LATE(2),
    EARLY(1),
    ON_TIME(0),
    NO_REALTIME(0);

    val isDeviation: Boolean get() = severity > 0
}

enum class HealthStatus { OK, WARNING, CRITICAL, UNKNOWN }

/**
 * Operational key figures for a set of departures. Punctuality is computed over departures
 * that report realtime data only - counting untracked departures as "on time" would inflate it.
 */
data class BoardKpis(
    val total: Int = 0,
    val canceled: Int = 0,
    val tracked: Int = 0,
    val onTime: Int = 0,
    /** Later than the threshold; includes [severelyLate]. */
    val late: Int = 0,
    val severelyLate: Int = 0,
    val early: Int = 0,
    val sumDelaySeconds: Long = 0,
    val maxDelaySeconds: Int? = null,
    val alertCount: Int = 0,
) {
    val punctuality: Double? get() = if (tracked == 0) null else onTime.toDouble() / tracked

    /** Share of non-canceled departures that report realtime - a proxy for onboard IT health. */
    val realtimeCoverage: Double?
        get() = (total - canceled).let { running -> if (running <= 0) null else tracked.toDouble() / running }

    val averageDelaySeconds: Double? get() = if (tracked == 0) null else sumDelaySeconds.toDouble() / tracked

    val deviations: Int get() = canceled + late + early

    operator fun plus(other: BoardKpis) = BoardKpis(
        total = total + other.total,
        canceled = canceled + other.canceled,
        tracked = tracked + other.tracked,
        onTime = onTime + other.onTime,
        late = late + other.late,
        severelyLate = severelyLate + other.severelyLate,
        early = early + other.early,
        sumDelaySeconds = sumDelaySeconds + other.sumDelaySeconds,
        maxDelaySeconds = listOfNotNull(maxDelaySeconds, other.maxDelaySeconds).maxOrNull(),
        alertCount = alertCount + other.alertCount,
    )

    companion object {
        val Empty = BoardKpis()

        fun of(entries: List<TimetableEntry>, policy: PunctualityPolicy, alertCount: Int = 0): BoardKpis {
            var canceled = 0
            var tracked = 0
            var onTime = 0
            var late = 0
            var severelyLate = 0
            var early = 0
            var sumDelay = 0L
            var maxDelay: Int? = null
            for (entry in entries) {
                val status = policy.classify(entry)
                if (status == DepartureStatus.CANCELED) {
                    canceled++
                    continue
                }
                if (status == DepartureStatus.NO_REALTIME) continue
                tracked++
                sumDelay += entry.delaySeconds
                maxDelay = maxOf(maxDelay ?: entry.delaySeconds, entry.delaySeconds)
                when (status) {
                    DepartureStatus.SEVERELY_LATE -> { late++; severelyLate++ }
                    DepartureStatus.LATE -> late++
                    DepartureStatus.EARLY -> early++
                    else -> onTime++
                }
            }
            return BoardKpis(
                total = entries.size,
                canceled = canceled,
                tracked = tracked,
                onTime = onTime,
                late = late,
                severelyLate = severelyLate,
                early = early,
                sumDelaySeconds = sumDelay,
                maxDelaySeconds = maxDelay,
                alertCount = alertCount,
            )
        }
    }
}
