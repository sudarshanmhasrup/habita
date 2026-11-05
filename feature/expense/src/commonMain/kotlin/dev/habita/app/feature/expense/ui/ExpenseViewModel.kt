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
import dev.habita.app.feature.expense.data.PendingPayment
import dev.habita.app.feature.expense.data.UpiRequest
import dev.habita.app.feature.expense.data.UpiStatus
import dev.habita.app.feature.expense.data.formatRupees
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

    /** Paying a scanned UPI QR. */
    data object Pay : ExpenseRoute
}

/** The form for paying a scanned QR. A QR with its own amount keeps it. */
internal data class PayDraft(
    val request: UpiRequest,
    val amountText: String,
    val description: String,
    val categoryId: String?,
) {
    val amountFixed: Boolean get() = request.amountPaise != null
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

    private val _payDraft = MutableStateFlow<PayDraft?>(null)
    val payDraft: StateFlow<PayDraft?> = _payDraft.asStateFlow()

    val pending = repository.pending.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        _payDraft.value = null
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
                0 -> "No new bank payments found this month."
                1 -> "Added 1 payment from your SMS."
                else -> "Added $added payments from your SMS."
            }
        }
    }

    /** Opens the pay screen for a scanned QR, or says why it can't be paid. */
    fun onScanned(text: String?) {
        if (text == null) return
        val request = UpiRequest.parse(text)
        if (request == null) {
            _message.value = "That isn't a UPI payment QR."
            return
        }
        viewModelScope.launch {
            _payDraft.value = PayDraft(
                request = request,
                amountText = request.amountPaise?.let { formatRupees(it, symbol = false).replace(",", "") } ?: "",
                description = request.note ?: request.displayName,
                categoryId = repository.categoryFor(request.payeeId),
            )
            _route.value = ExpenseRoute.Pay
        }
    }

    fun updatePay(change: (PayDraft) -> PayDraft) {
        _payDraft.value = _payDraft.value?.let(change)
    }

    /**
     * Remembers the payment so its bank SMS is saved with this description and category, then
     * returns its id and the request to hand to the UPI app.
     */
    suspend fun startPay(amountPaise: Long): Pair<String, String>? {
        val draft = _payDraft.value ?: return null
        val payment = PendingPayment(
            id = newId(),
            amountPaise = amountPaise,
            payeeId = draft.request.payeeId,
            payeeName = draft.request.displayName,
            description = draft.description.trim().ifEmpty { draft.request.displayName },
            categoryId = draft.categoryId,
            startedAt = nowMillis(),
        )
        repository.startPayment(payment)
        return payment.id to draft.request.toUri(amountPaise, draft.description)
    }

    /** What happened in the UPI app; `null` means there was no UPI app to open. */
    fun onPayResult(paymentId: String, amountPaise: Long, status: UpiStatus?) {
        viewModelScope.launch {
            _message.value = when (status) {
                null -> {
                    repository.dropPayment(paymentId)
                    "No UPI app found. Install GPay or PhonePe to pay from here."
                }
                UpiStatus.FAILURE -> {
                    repository.dropPayment(paymentId)
                    "The payment didn't go through."
                }
                UpiStatus.SUCCESS -> "Paid ${formatRupees(amountPaise)}. It's saved as soon as the bank SMS arrives."
                UpiStatus.UNKNOWN -> "It's saved when the bank SMS arrives. If you didn't pay, tap Didn't pay below."
            }
            back()
        }
    }

    /** A started payment whose SMS never came: [paid] saves it, otherwise it's dropped. */
    fun settle(paymentId: String, paid: Boolean) {
        viewModelScope.launch {
            if (paid) repository.settlePayment(paymentId) else repository.dropPayment(paymentId)
        }
    }

    fun showMessage(text: String?) {
        _message.value = text
    }
}