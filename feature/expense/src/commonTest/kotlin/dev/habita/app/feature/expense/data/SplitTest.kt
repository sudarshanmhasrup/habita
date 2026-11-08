package dev.habita.app.feature.expense.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitTest {
    private val month = YearMonth(2026, 11)
    private val food = Category("food", "Food", 5_000_00, 0)
    private val household = Category("household", "Household", 0, 1)

    private fun order(parts: List<ExpensePart>) = Expense(
        id = "zepto",
        amountPaise = parts.sumOf { it.amountPaise },
        description = "Zepto order",
        payee = "zepto",
        categoryId = mainCategory(parts),
        mode = PaymentMode.ONLINE,
        source = ExpenseSource.SMS,
        smsRef = null,
        paidAt = startOf(month) + 1_000,
        parts = parts,
    )

    @Test
    fun `a split payment counts each part in its own category`() {
        val expense = order(listOf(ExpensePart("food", 810_00, "Milk, dahi"), ExpensePart("household", 449_00, "Kettle")))
        val summary = MonthSummary.of(month, 0, listOf(food, household), listOf(expense))

        assertEquals(810_00, summary.categories.first { it.category.id == "food" }.spentPaise)
        assertEquals(449_00, summary.categories.first { it.category.id == "household" }.spentPaise)
        assertEquals(1_259_00, summary.spentPaise)
        assertTrue(summary.unsorted.isEmpty())
        assertEquals("food", expense.categoryId)
    }

    @Test
    fun `parts survive being stored as text`() {
        val parts = listOf(ExpensePart("food", 810_00, "Milk"), ExpensePart("household", 449_00))
        assertEquals(parts, decodeParts(encodeParts(parts)))
        assertEquals(null, encodeParts(emptyList()))
        assertEquals(emptyList(), decodeParts("not json"))
    }

    @Test
    fun `the markdown names each part of a split`() {
        val expense = order(listOf(ExpensePart("food", 810_00), ExpensePart("household", 449_00)))
        val markdown = MonthSummary.of(month, 0, listOf(food, household), listOf(expense)).toMarkdown()
        assertTrue("Food 810 + Household 449" in markdown, markdown)
    }
}