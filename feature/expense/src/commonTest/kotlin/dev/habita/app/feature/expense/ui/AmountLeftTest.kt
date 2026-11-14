package dev.habita.app.feature.expense.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AmountLeftTest {
    @Test
    fun aHiddenAmountShowsOnlyTheRupeeSignAndDots() {
        assertEquals("₹••••", rupeesOrHidden(12_345_00, hidden = true))
        // Nothing of the amount gets through, not even how many digits it has.
        assertEquals(rupeesOrHidden(5_00, hidden = true), rupeesOrHidden(1_23_45_678_00, hidden = true))
        assertFalse(rupeesOrHidden(12_345_00, hidden = true).any { it.isDigit() })
    }

    @Test
    fun aShownAmountIsFormattedAsRupees() {
        assertEquals("₹12,345", rupeesOrHidden(12_345_00, hidden = false))
        assertEquals("₹39.50", rupeesOrHidden(39_50, hidden = false))
    }
}