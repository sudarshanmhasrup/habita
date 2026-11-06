package dev.habita.app.shared.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BodyTest {
    // Today is 28 Jan: week 0 is 22–28 Jan, week 1 is 15–21, week 2 is 8–14, week 3 is 1–7.
    private val today = "2026-01-28"

    private fun weight(day: Int, kg: Double) = BodyLog(day = "2026-01-${day.toString().padStart(2, '0')}", weightGrams = (kg * 1000).toInt(), updatedAt = 0)

    private fun waist(day: Int, cm: Double) = BodyLog(day = "2026-01-${day.toString().padStart(2, '0')}", waistMm = (cm * 10).toInt(), updatedAt = 0)

    /** Four weigh-ins in each listed week (newest first), all at that week's weight. */
    private fun weeks(vararg kg: Double): List<BodyLog> = kg.flatMapIndexed { weeksAgo, value ->
        (0 until 4).map { weight(28 - weeksAgo * 7 - it, value) }
    }

    @Test
    fun `counts days the same as the calendar`() {
        assertEquals(0, Body.epochDay("1970-01-01"))
        assertEquals(20_454, Body.epochDay("2026-01-01"))
        assertEquals(1, Body.epochDay("2024-03-01") - Body.epochDay("2024-02-29"))
        assertEquals(365, Body.epochDay("2027-01-01") - Body.epochDay("2026-01-01"))
    }

    @Test
    fun `asks for more data before two weeks of weigh-ins`() {
        val trend = Body.trend(weeks(78.0), today)

        assertEquals(BodyAdvice.NEED_MORE_DATA, trend.advice)
        assertNull(trend.weeklyChangeGrams)
    }

    @Test
    fun `a week with too few weigh-ins does not count`() {
        val logs = weeks(78.0) + listOf(weight(20, 78.5), weight(19, 78.5))

        assertEquals(BodyAdvice.NEED_MORE_DATA, Body.trend(logs, today).advice)
    }

    @Test
    fun `averages each week`() {
        val logs = listOf(weight(28, 78.0), weight(27, 78.4), weight(26, 78.2)) + weeks(0.0, 79.0).drop(4)
        val trend = Body.trend(logs, today)

        assertEquals(78_200, trend.weeks.first().grams)
        assertEquals(-800, trend.weeklyChangeGrams)
    }

    @Test
    fun `steady loss is on track`() {
        assertEquals(BodyAdvice.ON_TRACK, Body.trend(weeks(77.5, 78.0, 78.4), today).advice)
    }

    @Test
    fun `loss after only two weeks is on track`() {
        assertEquals(BodyAdvice.ON_TRACK, Body.trend(weeks(77.6, 78.0), today).advice)
    }

    @Test
    fun `a flat second week is too early to judge`() {
        assertEquals(BodyAdvice.TOO_EARLY, Body.trend(weeks(78.0, 78.0), today).advice)
    }

    @Test
    fun `losing over 1 percent a week is too fast`() {
        assertEquals(BodyAdvice.TOO_FAST, Body.trend(weeks(77.0, 78.0), today).advice)
    }

    @Test
    fun `flat for two weeks is stalled`() {
        assertEquals(BodyAdvice.STALLED, Body.trend(weeks(77.9, 78.0, 78.0), today).advice)
    }

    @Test
    fun `gaining for two weeks is stalled`() {
        assertEquals(BodyAdvice.STALLED, Body.trend(weeks(78.6, 78.3, 78.0), today).advice)
    }

    @Test
    fun `flat weight with a shrinking waist is recomposition`() {
        val logs = weeks(78.0, 78.0, 78.0) + listOf(waist(5, 86.0), waist(27, 85.0))
        val trend = Body.trend(logs, today)

        assertEquals(BodyAdvice.RECOMP, trend.advice)
        assertEquals(10, trend.waistChangeMm)
    }

    @Test
    fun `waist readings less than two weeks apart do not count`() {
        val logs = weeks(78.0, 78.0, 78.0) + listOf(waist(20, 86.0), waist(27, 85.0))

        assertEquals(BodyAdvice.STALLED, Body.trend(logs, today).advice)
    }

    @Test
    fun `finds today and the latest entries`() {
        val logs = listOf(weight(26, 78.0), waist(21, 85.0), weight(28, 77.8).copy(deleted = true))
        val trend = Body.trend(logs, today)

        assertNull(trend.today)
        assertEquals("2026-01-26", trend.latestWeight?.day)
        assertEquals("2026-01-21", trend.latestWaist?.day)
    }
}