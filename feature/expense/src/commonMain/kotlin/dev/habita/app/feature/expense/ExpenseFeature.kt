package dev.habita.app.feature.expense

import dev.habita.app.core.Core
import dev.habita.app.core.data.nowMillis
import dev.habita.app.feature.expense.data.Category
import dev.habita.app.feature.expense.data.ExpenseRepository
import dev.habita.app.feature.expense.data.SmsOutcome
import dev.habita.app.feature.expense.data.formatRupees
import dev.habita.app.feature.expense.data.monthOf
import dev.habita.app.feature.expense.data.startOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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

    /** One line for the home screen: payments to sort, or what's left of this month's income. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val status: Flow<String> = flow { emit(monthOf(nowMillis())) }
        .flatMapLatest { repository.summary(it) }
        .map { summary ->
            when (val count = summary.unsorted.size) {
                0 -> "${formatRupees(summary.leftPaise)} left this month"
                1 -> "1 payment to sort"
                else -> "$count payments to sort"
            }
        }

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

    /** Shows the expense screen next time the app is in front, on [expenseId] when it's set. */
    fun requestOpen(expenseId: String?) {
        _openRequest.value = OpenRequest(expenseId, null, nowMillis())
    }

    /** Shows the expense screen on paying the QR code [scanned] from elsewhere, such as home. */
    fun requestPay(scanned: String) {
        _openRequest.value = OpenRequest(null, scanned, nowMillis())
    }

    internal fun consumeOpenRequest() {
        _openRequest.value = null
    }

    /** The first millisecond of this month, for reading this month's SMS. */
    fun startOfThisMonth(): Long = startOf(monthOf(nowMillis()))
}

/**
 * Open the expense screen: on one payment when [expenseId] is set, or on paying a QR when [scan]
 * holds its text. [at] tells repeats apart.
 */
data class OpenRequest(val expenseId: String?, val scan: String?, val at: Long)