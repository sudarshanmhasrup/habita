package dev.habita.app.feature.expense.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PayDayTest {
    private val november = YearMonth(2026, 11)
    private val december = YearMonth(2026, 12)
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun aMonthFromSalaryToSalaryStartsAtMidnightOnTheSalaryDay() {
        val start = periodStart(november, 5)

        assertEquals(startOfDay(start), start)
        assertEquals(startOf(november) + 4 * day, start)
        assertEquals(startOf(november), periodStart(november, 1))
        // Days past 28 would skip February, so they count as the 28th.
        assertEquals(periodStart(november, 28), periodStart(november, 31))
    }

    @Test
    fun thePaymentsBeforeTheSalaryDayBelongToTheMonthBefore() {
        assertEquals(YearMonth(2026, 10), periodOf(startOf(november) + 3 * day, 5))
        assertEquals(november, periodOf(periodStart(november, 5), 5))
        assertEquals(november, periodOf(periodStart(december, 5) - 1, 5))
        assertEquals(december, periodOf(periodStart(december, 5), 5))
        // A salary on the 5th: early January is still December, of the year before.
        assertEquals(december, periodOf(startOf(YearMonth(2027, 1)) + day, 5))
    }

    @Test
    fun onlyAMonthFromSalaryToSalaryNamesItsDays() {
        assertNull(MonthSummary.of(november, 0, emptyList(), emptyList()).rangeLabel)
        val summary = MonthSummary.of(november, 0, emptyList(), emptyList(), payDay = 5)
        assertEquals("${shortDayLabel(periodStart(november, 5))} – ${shortDayLabel(periodStart(december, 5) - 1)}", summary.rangeLabel)
        assertTrue(summary.toMarkdown().contains(summary.rangeLabel!!))
    }
}