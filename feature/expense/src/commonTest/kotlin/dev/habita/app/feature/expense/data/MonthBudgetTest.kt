package dev.habita.app.feature.expense.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MonthBudgetTest {
    private val food = Category("food", "Food", 5_000_00, 0)
    private val gym = Category("gym", "Gym", 1_500_00, 1)
    private val october = YearMonth(2026, 10)
    private val november = YearMonth(2026, 11)
    private val december = YearMonth(2026, 12)

    @Test
    fun monthsBeforeTheFirstSavedBudgetKeepTheOldOne() {
        val saved = mapOf(november to MonthBudget(60_000_00, mapOf("food" to 6_000_00)))

        val (income, categories) = budgetFor(october, 55_000_00, listOf(food, gym), saved)

        assertEquals(55_000_00, income)
        assertEquals(listOf(5_000_00L, 1_500_00L), categories.map { it.limitPaise })
    }

    @Test
    fun aSavedBudgetHoldsUntilALaterMonthHasItsOwn() {
        val saved = mapOf(
            october to MonthBudget(55_000_00, mapOf("food" to 5_000_00, "gym" to 1_500_00)),
            december to MonthBudget(70_000_00, mapOf("food" to 7_000_00)),
        )

        assertEquals(55_000_00, budgetFor(november, 0, listOf(food, gym), saved).first)
        val (income, categories) = budgetFor(YearMonth(2027, 3), 0, listOf(food, gym), saved)
        assertEquals(70_000_00, income)
        // Gym has no limit in December's budget.
        assertEquals(listOf(7_000_00L, 0L), categories.map { it.limitPaise })
        assertEquals(december, nextOwnBudget(november, saved))
        assertNull(nextOwnBudget(december, saved))
    }

    @Test
    fun keysReadBackAsTheirMonth() {
        assertEquals("budget@2026-01", MonthBudget.key(YearMonth(2026, 1)))
        assertEquals(YearMonth(2031, 12), MonthBudget.monthOfKey(MonthBudget.key(YearMonth(2031, 12))))
        assertNull(MonthBudget.monthOfKey("income_paise"))
        assertNull(MonthBudget.monthOfKey("budget@2026-13"))
        val budget = MonthBudget(1_00, mapOf("food" to 2_00))
        assertEquals(budget, MonthBudget.decode(budget.encode()))
    }
}