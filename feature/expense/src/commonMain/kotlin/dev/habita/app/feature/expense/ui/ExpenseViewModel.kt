package dev.habita.app.feature.expense.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.habita.app.core.data.newId
import dev.habita.app.core.data.nowMillis
import dev.habita.app.feature.expense.data.Expense
import dev.habita.app.feature.expense.data.ExpenseRepository
import dev.habita.app.feature.expense.data.ExpenseSource
import dev.habita.app.feature.expense.data.MonthSummary
import dev.habita.app.feature.expense.data.PaymentMode
import dev.habita.app.feature.expense.data.SmsOutcome
import dev.habita.app.feature.expense.data.YearMonth
import dev.habita.app.feature.expense.data.monthOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The screens of the expense tracker. */
internal sealed interface ExpenseRoute {
    data object Month : ExpenseRoute

    /** Adding a payment ([expenseId] `null`) or editing one. */
    data class Edit(val expenseId: String?) : ExpenseRoute

    /** The monthly income and each category's limit. */
    data object Budget : ExpenseRoute
}

/** The form for adding or editing a payment. */
internal data class ExpenseDraft(
    val id: String?,
    val amountText: String = "",
    val description: String = "",
    val categoryId: String? = null,
    val mode: PaymentMode = PaymentMode.ONLINE,
    val paidAt: Long = nowMillis(),
    val original: Expense? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {
    private val _route = MutableStateFlow<ExpenseRoute>(ExpenseRoute.Month)
    val route: StateFlow<ExpenseRoute> = _route.asStateFlow()

    private val _month = MutableStateFlow(monthOf(nowMillis()))
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    val summary: StateFlow<MonthSummary?> = _month
        .flatMapLatest { repository.summary(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _draft = MutableStateFlow<ExpenseDraft?>(null)
    val draft: StateFlow<ExpenseDraft?> = _draft.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)

    /** A short note to show once, such as how many payments an import found. */
    val message: StateFlow<String?> = _message.asStateFlow()

    /** The last payment deleted, so it can be brought back. */
    private val _deleted = MutableStateFlow<Expense?>(null)
    val deleted: StateFlow<Expense?> = _deleted.asStateFlow()

    private var firstMonth: YearMonth? = null

    init {
        viewModelScope.launch { firstMonth = repository.firstMonth() }
    }

    val canGoBack: Boolean get() = firstMonth?.let { _month.value > it } ?: false

    val canGoForward: Boolean get() = _month.value < monthOf(nowMillis())

    fun previousMonth() {
        if (canGoBack) _month.value = _month.value.previous()
    }

    fun nextMonth() {
        if (canGoForward) _month.value = _month.value.next()
    }

    /** Goes back one screen. Returns `false` on the month screen, where back leaves the tracker. */
    fun back(): Boolean {
        if (_route.value == ExpenseRoute.Month) return false
        _route.value = ExpenseRoute.Month
        _draft.value = null
        return true
    }

    fun openBudget() {
        _route.value = ExpenseRoute.Budget
    }

    /** Opens the form for a new payment, or for [expenseId]. */
    fun openEdit(expenseId: String?) {
        viewModelScope.launch {
            val existing = expenseId?.let { repository.expense(it) }
            _draft.value = if (existing == null) {
                ExpenseDraft(id = null)
            } else {
                ExpenseDraft(
                    id = existing.id,
                    amountText = (existing.amountPaise / 100.0).let { if (existing.amountPaise % 100 == 0L) "${existing.amountPaise / 100}" else "$it" },
                    description = existing.description,
                    categoryId = existing.categoryId,
                    mode = existing.mode,
                    paidAt = existing.paidAt,
                    original = existing,
                )
            }
            _route.value = ExpenseRoute.Edit(existing?.id)
        }
    }

    fun updateDraft(change: (ExpenseDraft) -> ExpenseDraft) {
        _draft.value = _draft.value?.let(change)
    }

    fun saveDraft(amountPaise: Long) {
        val draft = _draft.value ?: return
        val original = draft.original
        val expense = Expense(
            id = draft.id ?: newId(),
            amountPaise = amountPaise,
            description = draft.description.trim().ifEmpty { "Payment" },
            payee = original?.payee,
            categoryId = draft.categoryId,
            mode = draft.mode,
            source = original?.source ?: ExpenseSource.MANUAL,
            smsRef = original?.smsRef,
            paidAt = draft.paidAt,
        )
        viewModelScope.launch {
            repository.save(expense)
            _month.value = monthOf(expense.paidAt)
            back()
        }
    }

    fun deleteDraft() {
        val original = _draft.value?.original ?: return
        viewModelScope.launch {
            repository.delete(original.id)
            _deleted.value = original
            back()
        }
    }

    fun undoDelete() {
        val expense = _deleted.value ?: return
        _deleted.value = null
        viewModelScope.launch { repository.restore(expense.id) }
    }

    fun dismissUndo() {
        _deleted.value = null
    }

    fun setCategory(expenseId: String, categoryId: String) {
        viewModelScope.launch { repository.setCategory(expenseId, categoryId) }
    }

    fun saveBudget(incomePaise: Long?, limits: Map<String, Long>, newCategory: Pair<String, Long>?) {
        viewModelScope.launch {
            incomePaise?.let { repository.setIncome(it) }
            limits.forEach { (id, limit) -> repository.setLimit(id, limit) }
            newCategory?.let { (name, limit) -> repository.addCategory(name, limit) }
            back()
        }
    }

    /** Saves the payments in [messages] that aren't saved yet, and says how many were new. */
    fun importSms(messages: List<InboxSms>) {
        viewModelScope.launch {
            var added = 0
            messages.forEach { sms ->
                if (repository.recordSms(sms.body, sms.sender, sms.receivedAt) is SmsOutcome.Recorded) added++
            }
            firstMonth = repository.firstMonth()
            _message.value = when (added) {
                0 -> "No new bank payments in this month's or last month's SMS."
                1 -> "Added 1 payment from your SMS."
                else -> "Added $added payments from your SMS."
            }
        }
    }

    fun showMessage(text: String?) {
        _message.value = text
    }
}