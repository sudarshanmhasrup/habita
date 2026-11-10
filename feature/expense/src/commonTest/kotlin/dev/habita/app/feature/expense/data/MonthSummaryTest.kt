package dev.habita.app.feature.expense.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonthSummaryTest {
    private val month = YearMonth(2026, 11)
    private val food = Category("food", "Food", 5_000_00, 0)
    private val commute = Category("commute", "Commute", 1_000_00, 1)
    private val other = Category("other", "Other", 0, 2)
    private val gone = Category("old", "Old", 0, 3, archived = true)

    private fun expense(id: String, rupees: Long, category: String?) = Expense(
        id = id,
        amountPaise = rupees * 100,
        description = id,
        payee = null,
        categoryId = category,
        mode = PaymentMode.ONLINE,
        source = ExpenseSource.SMS,
        smsRef = null,
        paidAt = startOf(month) + 1_000,
    )

    @Test
    fun addsUpByCategoryAgainstLimits() {
        val summary = MonthSummary.of(
            month,
            incomePaise = 40_000_00,
            categories = listOf(food, commute, other, gone),
            expenses = listOf(
                expense("lunch", 230, "food"),
                expense("dinner", 217, "food"),
                expense("rapido", 1_080, "commute"),
                expense("abhimanyu", 500, "other"),
                expense("unknown", 39, null),
            ),
        )
        assertEquals(2_066_00, summary.spentPaise)
        assertEquals(40_000_00 - 2_066_00, summary.leftPaise)
        assertEquals(1, summary.unsorted.size)
        val byId = summary.categories.associateBy { it.category.id }
        assertEquals(5_000_00 - 447_00, byId.getValue("food").remainingPaise)
        assertTrue(byId.getValue("commute").isOver)
        assertNull(byId.getValue("other").remainingPaise)
        // Archived and empty: hidden.
        assertNull(byId["old"])
    }

    @Test
    fun paymentsInMissingCategoriesCountAsUnsorted() {
        val summary = MonthSummary.of(month, 0, listOf(food), listOf(expense("x", 10, "deleted-category")))
        assertEquals(1, summary.unsorted.size)
    }

    @Test
    fun markdownFollowsTheNotionLayout() {
        val markdown = MonthSummary.of(month, 40_000_00, listOf(food, commute), listOf(expense("Lunch", 60, "food"))).toMarkdown()
        assertTrue("- **Salary:** 40,000" in markdown)
        assertTrue("1. **Food:** 5,000" in markdown)
        assertTrue("1. **Food:** 4,940" in markdown)
        assertTrue("| 60 | Lunch | Food | Online |" in markdown)
    }

    @Test
    fun countsTheDaysOfAMonthFromSalaryToSalary() {
        val summary = MonthSummary.of(month, 0, emptyList(), emptyList(), payDay = 5)
        // 5 November to 4 December.
        assertEquals(30, summary.dayCount)
        assertEquals(1, summary.dayOf(summary.startsAt))
        assertEquals(6, summary.dayOf(summary.startsAt + 5 * DAY_MILLIS + 1_000))
        assertEquals(30, summary.dayOf(summary.endsAt - 1))
        assertNull(summary.dayOf(summary.endsAt))
        assertNull(summary.dayOf(summary.startsAt - 1))
    }

    @Test
    fun biggestSpendsPutTheRestTogether() {
        val rent = Category("rent", "Rent", 10_000_00, 3)
        val gym = Category("gym", "Gym", 2_000_00, 4)
        val summary = MonthSummary.of(
            month,
            0,
            listOf(food, commute, other, rent, gym),
            listOf(
                expense("rent", 10_000, "rent"),
                expense("zepto", 1_259, "food"),
                expense("gym", 2_000, "gym"),
                expense("zudio", 1_307, "other"),
                expense("rapido", 54, "commute"),
                expense("unknown", 40, null),
            ),
        )
        val spends = summary.biggestSpends(top = 3)
        assertEquals(listOf("rent", "gym", "other", null), spends.map { it.first?.id })
        // Food, commute and the unsorted payment.
        assertEquals(1_353_00, spends.last().second)
        assertEquals(summary.spentPaise, spends.sumOf { it.second })
    }
}