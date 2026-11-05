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
}