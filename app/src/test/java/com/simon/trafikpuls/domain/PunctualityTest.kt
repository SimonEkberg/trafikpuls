package com.simon.trafikpuls.domain

import com.simon.trafikpuls.testutil.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PunctualityTest {

    private val policy = PunctualityPolicy(lateThresholdSeconds = 180, earlyThresholdSeconds = 60, severeDelaySeconds = 600)

    @Test
    fun `classification boundaries`() {
        assertEquals(DepartureStatus.ON_TIME, policy.classify(entry(delay = 180)))
        assertEquals(DepartureStatus.LATE, policy.classify(entry(delay = 181)))
        assertEquals(DepartureStatus.LATE, policy.classify(entry(delay = 599)))
        assertEquals(DepartureStatus.SEVERELY_LATE, policy.classify(entry(delay = 600)))
        assertEquals(DepartureStatus.ON_TIME, policy.classify(entry(delay = -60)))
        assertEquals(DepartureStatus.EARLY, policy.classify(entry(delay = -61)))
    }

    @Test
    fun `canceled wins over delay and missing realtime is its own status`() {
        assertEquals(DepartureStatus.CANCELED, policy.classify(entry(delay = 900, canceled = true)))
        assertEquals(DepartureStatus.NO_REALTIME, policy.classify(entry(delay = 0, realtime = false)))
    }

    @Test
    fun `punctuality only counts departures with realtime data`() {
        val kpis = BoardKpis.of(
            listOf(
                entry(delay = 30),
                entry(delay = 400),
                entry(delay = 0, realtime = false),
                entry(delay = 0, realtime = false),
                entry(canceled = true),
            ),
            policy,
        )
        assertEquals(5, kpis.total)
        assertEquals(2, kpis.tracked)
        assertEquals(0.5, kpis.punctuality!!, 1e-9)
        assertEquals(0.5, kpis.realtimeCoverage!!, 1e-9) // 2 of 4 running departures
        assertEquals(400, kpis.maxDelaySeconds)
        assertEquals(215.0, kpis.averageDelaySeconds!!, 1e-9)
        assertEquals(2, kpis.deviations) // one late + one canceled
    }

    @Test
    fun `empty input gives no punctuality instead of a fake 100 percent`() {
        val kpis = BoardKpis.of(emptyList(), policy)
        assertNull(kpis.punctuality)
        assertNull(kpis.realtimeCoverage)
        assertEquals(HealthStatus.UNKNOWN, policy.health(kpis))
    }

    @Test
    fun `kpis add up across stops`() {
        val a = BoardKpis.of(listOf(entry(delay = 30), entry(delay = 700)), policy)
        val b = BoardKpis.of(listOf(entry(delay = -120), entry(canceled = true)), policy)
        val sum = a + b
        assertEquals(4, sum.total)
        assertEquals(3, sum.tracked)
        assertEquals(1, sum.onTime)
        assertEquals(1, sum.severelyLate)
        assertEquals(1, sum.early)
        assertEquals(1, sum.canceled)
        assertEquals(700, sum.maxDelaySeconds)
    }

    @Test
    fun `health status`() {
        val onTime = List(10) { entry(delay = 20) }
        assertEquals(HealthStatus.OK, policy.health(BoardKpis.of(onTime, policy)))

        val oneLateOfTen = List(9) { entry(delay = 20) } + entry(delay = 300)
        assertEquals(HealthStatus.OK, policy.health(BoardKpis.of(oneLateOfTen, policy))) // 90 %

        val twoLateOfTen = List(8) { entry(delay = 20) } + List(2) { entry(delay = 300) }
        assertEquals(HealthStatus.WARNING, policy.health(BoardKpis.of(twoLateOfTen, policy))) // 80 %

        val oneCanceledOfForty = List(39) { entry(delay = 20) } + entry(canceled = true)
        assertEquals(HealthStatus.WARNING, policy.health(BoardKpis.of(oneCanceledOfForty, policy)))

        val threeCanceledOfTwenty = List(17) { entry(delay = 20) } + List(3) { entry(canceled = true) }
        assertEquals(HealthStatus.CRITICAL, policy.health(BoardKpis.of(threeCanceledOfTwenty, policy)))

        val oneSevereOfTen = List(9) { entry(delay = 20) } + entry(delay = 900)
        assertEquals(HealthStatus.CRITICAL, policy.health(BoardKpis.of(oneSevereOfTen, policy))) // 10 % severe

        val untrackedOnly = List(3) { entry(realtime = false) }
        assertEquals(HealthStatus.UNKNOWN, policy.health(BoardKpis.of(untrackedOnly, policy)))
    }
}
