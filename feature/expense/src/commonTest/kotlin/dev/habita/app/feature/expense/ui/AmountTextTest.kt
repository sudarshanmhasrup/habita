package dev.habita.app.feature.expense.ui

import dev.habita.app.feature.expense.data.parseRupees
import kotlin.test.Test
import kotlin.test.assertEquals

class AmountTextTest {
    @Test
    fun anEditedAmountReadsBackAsTyped() {
        assertEquals("39", amountText(39_00))
        assertEquals("39.5", amountText(39_50))
        assertEquals("39.05", amountText(39_05))
        // A Double would print this one as 1.2345678E7, which the field can't read back.
        assertEquals("12345678.9", amountText(12_345_678_90))
        listOf(1L, 39_05L, 12_345_678_90L).forEach { assertEquals(it, parseRupees(amountText(it))) }
    }
}