package dev.habita.app.feature.expense.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.habita.app.core.Core
import dev.habita.app.core.data.newId
import dev.habita.app.core.data.nowMillis
import dev.habita.app.feature.expense.db.ExpenseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Payments, categories and the monthly income, kept on the phone. Every change marks the record
 * dirty, ready for syncing once the server supports expenses.
 */
class ExpenseRepository(core: Core) {
    private val database = ExpenseDatabase(core.databases.open("expense.db", ExpenseDatabase.Schema))
    private val expenses = database.expenseQueries
    private val categoryQueries = database.categoryQueries
    private val rules = database.payeeRuleQueries
    private val values = database.expenseValueQueries

    init {
        // A new install starts with the user's budget, so the screen is useful from the first open.
        database.transaction {
            if (categoryQueries.count().executeAsOne() == 0L) {
                Defaults.categories.forEach { categoryQueries.save(it.id, it.name, it.limitPaise, it.position.toLong(), 0) }
            }
            if (values.select(INCOME).executeAsOneOrNull() == null) values.save(INCOME, Defaults.INCOME_PAISE.toString())
        }
    }

    val categories: Flow<List<Category>> =
        categoryQueries.all(::toCategory).asFlow().mapToList(Dispatchers.Default)

    val income: Flow<Long> = values.select(INCOME).asFlow().mapToOneOrNull(Dispatchers.Default)
        .map { it?.toLongOrNull() ?: Defaults.INCOME_PAISE }

    fun summary(month: YearMonth): Flow<MonthSummary> {
        val payments = expenses.inRange(startOf(month), startOf(month.next()), ::toExpense)
            .asFlow()
            .mapToList(Dispatchers.Default)
        return combine(income, categories, payments) { income, categories, list ->
            MonthSummary.of(month, income, categories, list)
        }
    }

    /** The month of the oldest payment, or `null` with none yet. */
    suspend fun firstMonth(): YearMonth? = io {
        expenses.firstPaidAt().executeAsOneOrNull()?.MIN?.let(::monthOf)
    }

    suspend fun expense(id: String): Expense? = io { expenses.byId(id, ::toExpense).executeAsOneOrNull() }

    /**
     * Saves a payment from a bank SMS, sorted into the payee's remembered category when there is
     * one. Returns `null` for an SMS that isn't a debit, and [SmsOutcome.Duplicate] for one that
     * is already saved: the same reference number, or with none, the same amount within two
     * minutes.
     */
    suspend fun recordSms(body: String, sender: String?, receivedAt: Long): SmsOutcome? = io {
        val debit = BankSms.parse(body, sender) ?: return@io null
        database.transactionWithResult {
            val duplicate = if (debit.reference != null) {
                expenses.bySmsRef(debit.reference).executeAsOneOrNull() != null
            } else {
                expenses.nearDuplicate(debit.amountPaise, receivedAt - DUPLICATE_WINDOW, receivedAt + DUPLICATE_WINDOW)
                    .executeAsOneOrNull() != null
            }
            if (duplicate) return@transactionWithResult SmsOutcome.Duplicate
            val categoryId = debit.payee?.let { rules.forPayee(BankSms.payeeKey(it)).executeAsOneOrNull() }
            val category = categoryId?.let { id ->
                categoryQueries.all(::toCategory).executeAsList().firstOrNull { it.id == id && !it.archived }
            }
            val expense = Expense(
                id = newId(),
                amountPaise = debit.amountPaise,
                description = debit.payee?.let(BankSms::displayName) ?: "Bank payment",
                payee = debit.payee,
                categoryId = category?.id,
                mode = PaymentMode.ONLINE,
                source = ExpenseSource.SMS,
                smsRef = debit.reference,
                paidAt = receivedAt,
            )
            write(expense)
            SmsOutcome.Recorded(expense, category)
        }
    }

    /** Saves a new or edited payment. A payment with a payee teaches its category for next time. */
    suspend fun save(expense: Expense) = io {
        database.transaction {
            write(expense)
            remember(expense)
        }
    }

    /** Sorts a payment into [categoryId], and remembers it for the payee's next payment. */
    suspend fun setCategory(expenseId: String, categoryId: String) = io {
        database.transaction {
            expenses.setCategory(categoryId, nowMillis(), expenseId)
            expenses.byId(expenseId, ::toExpense).executeAsOneOrNull()?.let(::remember)
        }
    }

    suspend fun delete(expenseId: String) = io { expenses.remove(nowMillis(), expenseId) }

    suspend fun restore(expenseId: String) = io { expenses.restore(nowMillis(), expenseId) }

    suspend fun setLimit(categoryId: String, limitPaise: Long) = io { categoryQueries.setLimit(limitPaise, categoryId) }

    suspend fun setIncome(paise: Long) = io { values.save(INCOME, paise.toString()) }

    suspend fun addCategory(name: String, limitPaise: Long) = io {
        database.transaction {
            val position = categoryQueries.nextPosition().executeAsOne()
            categoryQueries.save(newId(), name.trim(), limitPaise, position, 0)
        }
    }

    suspend fun archiveCategory(category: Category) = io {
        categoryQueries.save(category.id, category.name, category.limitPaise, category.position.toLong(), 1)
    }

    private fun write(expense: Expense) {
        expenses.save(
            expense.id,
            expense.amountPaise,
            expense.description,
            expense.payee,
            expense.categoryId,
            expense.mode.name,
            expense.source.name,
            expense.smsRef,
            expense.paidAt,
            nowMillis(),
            0,
        )
    }

    private fun remember(expense: Expense) {
        val payee = expense.payee ?: return
        val categoryId = expense.categoryId ?: return
        rules.save(BankSms.payeeKey(payee), categoryId)
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.Default) { block() }

    private companion object {
        const val INCOME = "income_paise"
        const val DUPLICATE_WINDOW = 2 * 60 * 1000L

        fun toCategory(id: String, name: String, limit: Long, position: Long, archived: Long) =
            Category(id, name, limit, position.toInt(), archived != 0L)

        fun toExpense(
            id: String,
            amount: Long,
            description: String,
            payee: String?,
            categoryId: String?,
            mode: String,
            source: String,
            smsRef: String?,
            paidAt: Long,
            @Suppress("UNUSED_PARAMETER") updatedAt: Long,
            @Suppress("UNUSED_PARAMETER") deleted: Long,
            @Suppress("UNUSED_PARAMETER") dirty: Long,
        ) = Expense(
            id = id,
            amountPaise = amount,
            description = description,
            payee = payee,
            categoryId = categoryId,
            mode = runCatching { PaymentMode.valueOf(mode) }.getOrDefault(PaymentMode.ONLINE),
            source = runCatching { ExpenseSource.valueOf(source) }.getOrDefault(ExpenseSource.MANUAL),
            smsRef = smsRef,
            paidAt = paidAt,
        )
    }
}