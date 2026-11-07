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
 * Payments, categories and the monthly income, kept on the phone and synced when the server is
 * reachable. Every change records its time, marks the record dirty and asks for a sync.
 */
class ExpenseRepository(core: Core) {
    private val sync = core.sync
    private val database = ExpenseDatabase(core.databases.open("expense.db", ExpenseDatabase.Schema))
    private val expenses = database.expenseQueries
    private val categoryQueries = database.categoryQueries
    private val rules = database.payeeRuleQueries
    private val values = database.expenseValueQueries

    init {
        // A new install starts with the default categories, so the screen is useful from the first
        // open. They count as never edited (time 0, not dirty): another device's real budget wins.
        database.transaction {
            if (categoryQueries.count().executeAsOne() == 0L) {
                Defaults.categories.forEach { categoryQueries.save(it.id, it.name, it.limitPaise, it.position.toLong(), 0, 0, 0) }
            }
            if (values.select(INCOME).executeAsOneOrNull() == null) values.save(INCOME, Defaults.INCOME_PAISE.toString(), 0, 0)
        }
        expenseSyncTables(database).forEach(sync::register)
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
     * minutes. A payment deleted after it came in counts as saved, so a rescan doesn't bring it
     * back.
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
        }.also { if (it is SmsOutcome.Recorded) sync.request() }
    }

    /** Saves a new or edited payment. A payment with a payee teaches its category for next time. */
    suspend fun save(expense: Expense) = change {
        database.transaction {
            write(expense)
            remember(expense)
        }
    }

    /** Sorts a payment into [categoryId], and remembers it for the payee's next payment. */
    suspend fun setCategory(expenseId: String, categoryId: String) = change {
        database.transaction {
            expenses.setCategory(categoryId, nowMillis(), expenseId)
            expenses.byId(expenseId, ::toExpense).executeAsOneOrNull()?.let(::remember)
        }
    }

    suspend fun delete(expenseId: String) = change { expenses.remove(nowMillis(), expenseId) }

    suspend fun restore(expenseId: String) = change { expenses.restore(nowMillis(), expenseId) }

    suspend fun setLimit(categoryId: String, limitPaise: Long) = change { categoryQueries.setLimit(limitPaise, nowMillis(), categoryId) }

    suspend fun setIncome(paise: Long) = change { values.save(INCOME, paise.toString(), nowMillis(), 1) }

    suspend fun addCategory(name: String, limitPaise: Long) = change {
        database.transaction {
            val position = categoryQueries.nextPosition().executeAsOne()
            categoryQueries.save(newId(), name.trim(), limitPaise, position, 0, nowMillis(), 1)
        }
    }

    suspend fun archiveCategory(category: Category) = change {
        categoryQueries.save(category.id, category.name, category.limitPaise, category.position.toLong(), 1, nowMillis(), 1)
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

    /** A local edit: saved now, synced soon. */
    private suspend fun change(block: () -> Unit) {
        io(block)
        sync.request()
    }

    private companion object {
        const val INCOME = "income_paise"
        const val DUPLICATE_WINDOW = 2 * 60 * 1000L

        fun toCategory(
            id: String,
            name: String,
            limit: Long,
            position: Long,
            archived: Long,
            @Suppress("UNUSED_PARAMETER") updatedAt: Long,
            @Suppress("UNUSED_PARAMETER") dirty: Long,
        ) = Category(id, name, limit, position.toInt(), archived != 0L)

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