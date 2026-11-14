package dev.habita.app.feature.gym.ui

import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals

class WeighInDayTest {
    private fun localMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    @Test
    fun aNightWeighInCountsForThatDay() {
        assertEquals("2026-11-13", backupDate(localMillis(2026, 11, 13, 22, 30)))
        assertEquals("2026-11-13", backupDate(localMillis(2026, 11, 13, 23, 59)))
        assertEquals("2026-11-13", backupDate(localMillis(2026, 11, 13, 6, 0)))
    }
}