package dev.habita.app.feature.expense

import dev.habita.app.core.Core
import dev.habita.app.core.data.nowMillis
import dev.habita.app.feature.expense.data.Category
import dev.habita.app.feature.expense.data.ExpenseRepository
import dev.habita.app.feature.expense.data.SmsOutcome
import dev.habita.app.feature.expense.data.currentMonth
import dev.habita.app.feature.expense.data.formatRupees
import dev.habita.app.feature.expense.data.monthLabel
import dev.habita.app.feature.expense.data.monthOf
import dev.habita.app.feature.expense.data.startOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * The expense tracker. Create it once at startup. Besides the screen, the platform uses it to save
 * payments read from bank SMS and to sort them from a notification.
 */
class ExpenseFeature(core: Core) {
    internal val repository = ExpenseRepository(core)

    private val _openRequest = MutableStateFlow<OpenRequest?>(null)

    /** A request from outside the app, such as a notification, to show the expense screen. */
    val openRequest: StateFlow<OpenRequest?> = _openRequest.asStateFlow()

    /** Saves the payment in a bank SMS, or returns `null` when the SMS isn't a payment. */
    suspend fun recordSms(body: String, sender: String?, receivedAt: Long = nowMillis()): SmsOutcome? =
        repository.recordSms(body, sender, receivedAt)

    suspend fun setCategory(expenseId: String, categoryId: String) = repository.setCategory(expenseId, categoryId)

    /**
     * The [count] categories most used over this month and last, for one-tap buttons on a
     * payment notification. Topped up with Food, Commute and Other for a new install.
     */
    suspend fun quickCategories(count: Int): List<Category> {
        val categories = repository.categories.first().filter { !it.archived }
        val now = monthOf(nowMillis())
        val recent = repository.summary(now).first().expenses + repository.summary(now.previous()).first().expenses
        val used = recent.mapNotNull { it.categoryId }.groupingBy { it }.eachCount()
        val fallback = listOf("food", "commute", "other")
        return categories
            .sortedWith(
                compareByDescending<Category> { used[it.id] ?: 0 }
                    .thenBy { fallback.indexOf(it.id).let { index -> if (index < 0) fallback.size else index } }
                    .thenBy { it.position },
            )
            .take(count)
    }

    /**
     * This month for the home screen: what's left of the income (or only what was spent, when no
     * income is set), payments waiting for a category, and the latest payments.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val today: Flow<ExpenseToday> = currentMonth
        .flatMapLatest { repository.summary(it) }
        .map { summary ->
            val names = summary.categories.associate { it.category.id to it.category.name }
            ExpenseToday(
                monthName = monthLabel(summary.month).substringBefore(' '),
                incomeSet = summary.incomePaise > 0,
                incomePaise = summary.incomePaise,
                spentPaise = summary.spentPaise,
                leftPaise = summary.leftPaise,
                toSort = summary.unsorted.size,
                recent = summary.expenses.sortedByDescending { it.paidAt }.take(3).map {
                    RecentPayment(it.description, it.categoryId?.let(names::get) ?: "To sort", it.amountPaise)
                },
            )
        }

    /** Shows the expense screen next time the app is in front, on [expenseId] when it's set. */
    fun requestOpen(expenseId: String?) {
        _openRequest.value = OpenRequest(expenseId, nowMillis())
    }

    /** Shows the form for a new payment next time the expense screen opens. */
    fun requestAdd() {
        _openRequest.value = OpenRequest(null, nowMillis(), add = true)
    }

    internal fun consumeOpenRequest() {
        _openRequest.value = null
    }

    /**
     * Where a scan of the inbox starts: the first millisecond of last month, so payments missed
     * in the last days of a month can still be caught up early in the next.
     */
    fun scanFrom(): Long = startOf(monthOf(nowMillis()).previous())
}

/**
 * Open the expense screen: on one payment when [expenseId] is set, or on a new one with [add].
 * [at] tells repeats apart.
 */
data class OpenRequest(val expenseId: String?, val at: Long, val add: Boolean = false)

/** This month at a glance, for the home screen. Amounts are in paise. */
data class ExpenseToday(
    val monthName: String,
    val incomeSet: Boolean,
    val incomePaise: Long,
    val spentPaise: Long,
    val leftPaise: Long,
    val toSort: Int,
    val recent: List<RecentPayment>,
)

data class RecentPayment(val description: String, val category: String, val amountPaise: Long)