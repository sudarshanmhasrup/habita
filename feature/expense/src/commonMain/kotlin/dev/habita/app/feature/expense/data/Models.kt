package dev.habita.app.feature.expense.data

import kotlinx.serialization.Serializable

/** A spending category and its monthly limit. A limit of 0 means no limit. */
data class Category(
    val id: String,
    val name: String,
    val limitPaise: Long,
    val position: Int,
    val archived: Boolean = false,
)

enum class PaymentMode(val label: String) { ONLINE("Online"), CASH("Cash") }

/** Where a payment came from: a bank SMS, or typed in by hand. */
enum class ExpenseSource { SMS, MANUAL }

/**
 * One payment. [categoryId] is `null` until it is sorted. A split payment has [parts] that add up
 * to the amount, each in its own category; its [categoryId] is then the biggest part's.
 */
data class Expense(
    val id: String,
    val amountPaise: Long,
    val description: String,
    val payee: String?,
    val categoryId: String?,
    val mode: PaymentMode,
    val source: ExpenseSource,
    val smsRef: String?,
    val paidAt: Long,
    val parts: List<ExpensePart> = emptyList(),
) {
    val isSplit: Boolean get() = parts.isNotEmpty()

    /** How much of the payment went to each category. */
    val byCategory: Map<String?, Long>
        get() = if (isSplit) parts.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.amountPaise } } else mapOf(categoryId to amountPaise)
}

/** One part of a split payment, such as the kettle in a grocery order. [note] says what it was. */
@Serializable
data class ExpensePart(val categoryId: String, val amountPaise: Long, val note: String = "")

/** A calendar month, with [month] from 1 to 12. */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    fun next(): YearMonth = if (month == 12) YearMonth(year + 1, 1) else YearMonth(year, month + 1)

    fun previous(): YearMonth = if (month == 1) YearMonth(year - 1, 12) else YearMonth(year, month - 1)

    override fun compareTo(other: YearMonth): Int = compareValuesBy(this, other, { it.year }, { it.month })
}

/** What a new SMS turned into. */
sealed interface SmsOutcome {
    /** Saved. [category] is set when the payee's category was remembered from before. */
    data class Recorded(val expense: Expense, val category: Category?) : SmsOutcome

    /** Already saved from an earlier copy of the same SMS. */
    data object Duplicate : SmsOutcome
}