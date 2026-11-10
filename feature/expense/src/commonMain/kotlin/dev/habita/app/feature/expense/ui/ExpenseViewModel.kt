package dev.habita.app.feature.expense.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.habita.app.core.data.newId
import dev.habita.app.core.data.nowMillis
import dev.habita.app.feature.expense.data.BankSms
import dev.habita.app.feature.expense.data.Expense
import dev.habita.app.feature.expense.data.ExpensePart
import dev.habita.app.feature.expense.data.ExpenseRepository
import dev.habita.app.feature.expense.data.ExpenseSource
import dev.habita.app.feature.expense.data.MonthSummary
import dev.habita.app.feature.expense.data.PaymentMode
import dev.habita.app.feature.expense.data.SmsOutcome
import dev.habita.app.feature.expense.data.YearMonth
import dev.habita.app.feature.expense.data.MAX_PAY_DAY
import dev.habita.app.feature.expense.data.periodOf
import dev.habita.app.feature.expense.data.mainCategory
import dev.habita.app.feature.expense.data.parseRupees
import dev.habita.app.feature.expense.data.nextOwnBudget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

/**
 * What Save on the budget screen hands over: the income (`null` when the field was emptied, which
 * keeps it), every shown category's limit, renamed categories, ones to remove and ones to bring
 * back, and a new category's name and limit.
 */
internal data class BudgetEdit(
    val incomePaise: Long?,
    val limits: Map<String, Long>,
    val names: Map<String, String> = emptyMap(),
    val remove: Set<String> = emptySet(),
    val bringBack: Set<String> = emptySet(),
    val newCategory: Pair<String, Long>? = null,
    /** The salary day budget months start on, 1 to 28. */
    val payDay: Int? = null,
)

/** The form for adding or editing a payment. */
internal data class ExpenseDraft(
    val id: String?,
    val amountText: String = "",
    val description: String = "",
    val categoryId: String? = null,
    val mode: PaymentMode = PaymentMode.ONLINE,
    val paidAt: Long = nowMillis(),
    val original: Expense? = null,
    /** Parts of a split payment; empty for one in a single category. */
    val parts: List<PartDraft> = emptyList(),
) {
    /** The parts as saved, or `null` while one has no category or amount. */
    fun savedParts(): List<ExpensePart>? = parts.map { part ->
        val amount = parseRupees(part.amountText) ?: return null
        ExpensePart(part.categoryId ?: return null, amount, part.note.trim())
    }
}

/** One part of a split in the form. */
internal data class PartDraft(val categoryId: String? = null, val amountText: String = "", val note: String = "")

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {
    private val _route = MutableStateFlow<ExpenseRoute>(ExpenseRoute.Month)
    val route: StateFlow<ExpenseRoute> = _route.asStateFlow()

    /** The salary day budget months start on. */
    val payDay: StateFlow<Int> = repository.payDay.stateIn(viewModelScope, SharingStarted.Eagerly, repository.payDayNow())

    private val _month = MutableStateFlow(periodOf(nowMillis(), payDay.value))
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

    /** The budget month it is now. */
    val thisMonth: StateFlow<YearMonth> = repository.thisMonth.stateIn(viewModelScope, SharingStarted.Eagerly, _month.value)

    private val firstMonth = repository.firstMonth.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whether there are older payments to go back to. */
    val canGoBack: StateFlow<Boolean> = combine(_month, firstMonth) { month, first -> first != null && month > first }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Whether there is a later month to go to: up to next month, so its budget can be planned. */
    val canGoForward: StateFlow<Boolean> = combine(_month, thisMonth) { month, now -> month < now.next() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun previousMonth() {
        if (canGoBack.value) _month.value = _month.value.previous()
    }

    fun nextMonth() {
        if (canGoForward.value) _month.value = _month.value.next()
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
                    amountText = amountText(existing.amountPaise),
                    description = formDescription(existing),
                    categoryId = existing.categoryId,
                    mode = existing.mode,
                    paidAt = existing.paidAt,
                    original = existing,
                    parts = existing.parts.map { PartDraft(it.categoryId, amountText(it.amountPaise), it.note) },
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
        val parts = draft.savedParts() ?: return
        val expense = Expense(
            id = draft.id ?: newId(),
            amountPaise = amountPaise,
            // Left empty, a payment keeps the name it had, such as its payee's.
            description = draft.description.trim().ifEmpty { original?.description ?: "Payment" },
            payee = original?.payee,
            categoryId = if (parts.isEmpty()) draft.categoryId else mainCategory(parts),
            mode = draft.mode,
            source = original?.source ?: ExpenseSource.MANUAL,
            smsRef = original?.smsRef,
            paidAt = draft.paidAt,
            parts = parts,
        )
        viewModelScope.launch {
            repository.save(expense)
            _month.value = periodOf(expense.paidAt, payDay.value)
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

    /** The first later month with a budget of its own, where saving this month's stops. */
    val laterBudget: StateFlow<YearMonth?> = combine(_month, repository.budgets) { month, budgets -> nextOwnBudget(month, budgets) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Saves the budget for the month on screen and the months after it. Nothing is saved when
     * nothing changed, so a later change to an earlier month still reaches this one.
     */
    fun saveBudget(edit: BudgetEdit) {
        val month = _month.value
        val current = summary.value
        val income = edit.incomePaise ?: current?.incomePaise ?: 0
        val shownBefore = current?.categories.orEmpty().filter { !it.category.archived }.associate { it.category.id to it.category.limitPaise }
        val unchanged = current != null && edit.newCategory == null && income == current.incomePaise && edit.limits == shownBefore
        viewModelScope.launch {
            repository.saveCategories(edit.names, edit.remove, edit.bringBack)
            if (!unchanged) repository.saveBudget(month, income, edit.limits, edit.newCategory)
            val day = edit.payDay?.coerceIn(1, MAX_PAY_DAY)
            if (day != null && day != payDay.value) {
                repository.setPayDay(day)
                // The months shift, so go to the one it is now.
                _month.value = periodOf(nowMillis(), day)
            }
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

/**
 * What the "What for" field starts with: empty for a bank SMS payment still called after its
 * payee, so the owner types what it was for instead of editing the bank's name.
 */
internal fun formDescription(expense: Expense): String =
    if (expense.source == ExpenseSource.SMS && expense.description == BankSms.description(expense.payee)) "" else expense.description

/** Paise as the amount field shows them: "39", "39.5" or "1234567.05", never "1.2E7". */
internal fun amountText(paise: Long): String {
    val rest = paise % 100
    return if (rest == 0L) "${paise / 100}" else "${paise / 100}." + rest.toString().padStart(2, '0').trimEnd('0')
}