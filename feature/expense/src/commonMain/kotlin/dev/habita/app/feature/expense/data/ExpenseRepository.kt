package dev.habita.app.feature.expense.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.habita.app.core.Core
import dev.habita.app.core.data.newId
import dev.habita.app.core.data.nowMillis
import dev.habita.app.feature.expense.db.ExpenseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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
    private val syncTables = expenseSyncTables(database)

    init {
        // A new install starts with the default categories, so the screen is useful from the first
        // open. They count as never edited (time 0, not dirty): another device's real budget wins.
        database.transaction {
            if (categoryQueries.count().executeAsOne() == 0L) {
                Defaults.categories.forEach { categoryQueries.save(it.id, it.name, it.limitPaise, it.position.toLong(), 0, 0, 0) }
            }
            if (values.select(INCOME).executeAsOneOrNull() == null) values.save(INCOME, Defaults.INCOME_PAISE.toString(), 0, 0)
            // Household came later: installs from before get it at the end, unedited, so a synced
            // one from another device wins.
            if (categoryQueries.byId(Defaults.HOUSEHOLD_ID).executeAsOneOrNull() == null) {
                categoryQueries.save(Defaults.HOUSEHOLD_ID, "Household", 0, categoryQueries.nextPosition().executeAsOne(), 0, 0, 0)
            }
        }
        syncTables.forEach(sync::register)
        // Orders that expired while the app was closed go now. Sync checks again whenever
        // payments or orders come in from another device (see ExpenseSync).
        settlePendingOrders(database, nowMillis())
    }

    val categories: Flow<List<Category>> =
        categoryQueries.all(::toCategory).asFlow().mapToList(Dispatchers.Default)

    val income: Flow<Long> = values.select(INCOME).asFlow().mapToOneOrNull(Dispatchers.Default)
        .map { it?.toLongOrNull() ?: Defaults.INCOME_PAISE }

    /** Each month's own budget ([MonthBudget]). */
    internal val budgets: Flow<SavedBudgets> = values.budgets().asFlow().mapToList(Dispatchers.Default).map { rows ->
        rows.mapNotNull { row ->
            val month = MonthBudget.monthOfKey(row.key) ?: return@mapNotNull null
            MonthBudget.decode(row.value_)?.let { month to it }
        }.toMap()
    }

    /** The day of the month the salary comes, which budget months start on. 1 by default. */
    val payDay: Flow<Int> = values.select(PAY_DAY).asFlow().mapToOneOrNull(Dispatchers.Default)
        .map(::toPayDay)
        .distinctUntilChanged()

    /** The salary day as it is now, for setting up a screen. */
    fun payDayNow(): Int = toPayDay(values.select(PAY_DAY).executeAsOneOrNull())

    /** The budget month it is now. */
    val thisMonth: Flow<YearMonth> = currentPeriod(payDay)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun summary(month: YearMonth): Flow<MonthSummary> = payDay.flatMapLatest { day ->
        val payments = expenses.inRange(periodStart(month, day), periodStart(month.next(), day), ::toExpense)
            .asFlow()
            .mapToList(Dispatchers.Default)
        combine(income, categories, budgets, payments) { income, categories, budgets, list ->
            val (monthIncome, monthCategories) = budgetFor(month, income, categories, budgets)
            MonthSummary.of(month, monthIncome, monthCategories, list, day)
        }
    }

    /**
     * The month of the oldest payment, or `null` with none yet. It follows new payments and ones
     * synced in, such as a new phone getting its history.
     */
    val firstMonth: Flow<YearMonth?> = combine(expenses.firstPaidAt().asFlow().mapToOneOrNull(Dispatchers.Default), payDay) { first, day ->
        first?.MIN?.let { periodOf(it, day) }
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
            val categoryId = debit.payee?.let { payee ->
                expenses.lastCategoryForPayee(payee).executeAsOneOrNull()
                    ?: rules.forPayee(BankSms.payeeKey(payee)).executeAsOneOrNull()
            }
            val category = categoryId?.let { id ->
                categoryQueries.all(::toCategory).executeAsList().firstOrNull { it.id == id && !it.archived }
            }
            val expense = Expense(
                id = newId(),
                amountPaise = debit.amountPaise,
                description = BankSms.description(debit.payee),
                payee = debit.payee,
                categoryId = category?.id,
                mode = PaymentMode.ONLINE,
                source = ExpenseSource.SMS,
                smsRef = debit.reference,
                paidAt = receivedAt,
            )
            // An order logged before its bank SMS came: the payment takes the order's split.
            val order = takePendingOrder(debit.amountPaise, receivedAt)
            val saved = if (order != null) expense.withOrder(order) else expense
            write(saved)
            SmsOutcome.Recorded(saved, if (order != null) null else category)
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

    /**
     * Logs a paid order, such as groceries, split by category. Paid from a wallet or in cash,
     * there's no bank SMS, so it is saved now. Otherwise its bank payment is split: one already in
     * from up to a day before, or the next to come in within a day.
     */
    suspend fun recordOrder(order: PaidOrder): OrderOutcome = io {
        database.transactionWithResult {
            if (!order.bankSms) {
                val expense = Expense(
                    id = newId(),
                    amountPaise = order.amountPaise,
                    description = order.description,
                    payee = null,
                    categoryId = null,
                    mode = if (order.cash) PaymentMode.CASH else PaymentMode.ONLINE,
                    source = ExpenseSource.MANUAL,
                    smsRef = null,
                    paidAt = order.at,
                ).withOrder(order)
                write(expense)
                return@transactionWithResult OrderOutcome.Added(expense)
            }
            val payment = expenses.unsplitNear(order.amountPaise, order.at - ORDER_WINDOW, order.at + SMS_DELAY, ::toExpense)
                .executeAsOneOrNull()
            if (payment != null) {
                write(payment.withOrder(order))
                OrderOutcome.Matched(payment)
            } else {
                values.save(ORDER_PREFIX + newId(), PendingJson.encodeToString(PaidOrder.serializer(), order), nowMillis(), 1)
                OrderOutcome.Waiting
            }
        }.also { sync.request() }
    }

    /** This repository's sync table for [collection], for tests. */
    internal fun syncTable(collection: String) = syncTables.single { it.collection == collection }

    /** Splits payments already in for waiting orders and drops expired ones; see [settlePendingOrders]. */
    internal fun settlePendingOrders(now: Long): Boolean = settlePendingOrders(database, now)

    /** The order waiting for a bank payment of [amountPaise] at [at], taken off the list. */
    private fun takePendingOrder(amountPaise: Long, at: Long): PaidOrder? {
        val match = values.withPrefix(ORDER_PREFIX + "%").executeAsList().firstNotNullOfOrNull { row ->
            val order = runCatching { PendingJson.decodeFromString(PaidOrder.serializer(), row.value_) }.getOrNull()
            order?.takeIf { it.amountPaise == amountPaise && at in it.at - SMS_DELAY..it.at + ORDER_WINDOW }?.let { row.key to it }
        } ?: return null
        values.save(match.first, "", nowMillis(), 1)
        return match.second
    }

    suspend fun delete(expenseId: String) = change { expenses.remove(nowMillis(), expenseId) }

    suspend fun restore(expenseId: String) = change { expenses.restore(nowMillis(), expenseId) }

    /**
     * Saves [month]'s budget, which holds from it until a later month with its own. [limits] has
     * every category shown in the budget; [newCategory] (name and limit) is added with it. The
     * new category's own limit stays 0, so the months before don't get it.
     */
    internal suspend fun saveBudget(month: YearMonth, incomePaise: Long, limits: Map<String, Long>, newCategory: Pair<String, Long>?) = change {
        database.transaction {
            val now = nowMillis()
            val all = limits.toMutableMap()
            newCategory?.let { (name, limit) ->
                val id = newId()
                categoryQueries.save(id, name.trim(), 0, categoryQueries.nextPosition().executeAsOne(), 0, now, 1)
                all[id] = limit
            }
            values.save(MonthBudget.key(month), MonthBudget(incomePaise, all.filterValues { it > 0 }).encode(), now, 1)
        }
    }

    /** Makes budget months start on [day] of the month, on every device. */
    internal suspend fun setPayDay(day: Int) = change {
        values.save(PAY_DAY, day.coerceIn(1, MAX_PAY_DAY).toString(), nowMillis(), 1)
    }

    /** Renames categories ([names]), removes some ([remove]) and brings removed ones back. */
    internal suspend fun saveCategories(names: Map<String, String>, remove: Set<String>, bringBack: Set<String>) {
        val ids = names.keys + remove + bringBack
        if (ids.isEmpty()) return
        change {
            database.transaction {
                val now = nowMillis()
                ids.forEach { id ->
                    val row = categoryQueries.byId(id).executeAsOneOrNull() ?: return@forEach
                    val archived = when (id) {
                        in remove -> 1L
                        in bringBack -> 0L
                        else -> row.archived
                    }
                    val name = names[id]?.trim()?.takeIf { it.isNotEmpty() } ?: row.name
                    categoryQueries.save(id, name, row.limit_paise, row.position, archived, now, 1)
                }
            }
        }
    }

    private fun write(expense: Expense) = writeExpense(database, expense)

    private fun remember(expense: Expense) {
        // A split, such as one grocery order, says nothing about the payee's usual category.
        if (expense.isSplit) return
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

    internal companion object {
        const val INCOME = "income_paise"
        const val PAY_DAY = "pay_day"

        fun toPayDay(value: String?): Int = value?.toIntOrNull()?.coerceIn(1, MAX_PAY_DAY) ?: 1
        const val DUPLICATE_WINDOW = 2 * 60 * 1000L

        /** Orders waiting for their bank SMS, saved as values so every device knows them. */
        const val ORDER_PREFIX = "order@"

        /** How far apart an order and its bank payment can be. */
        const val ORDER_WINDOW = 24 * 60 * 60 * 1000L

        /** How long after an order is logged its bank SMS may still come. */
        const val SMS_DELAY = 60 * 60 * 1000L

        /** After this many days a waiting order is dropped: its payment isn't coming. */
        const val PENDING_DAYS = 7

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
            parts: String?,
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
            parts = decodeParts(parts),
        )
    }
}

/** Saves a payment as a local edit: dirty, so it syncs. */
internal fun writeExpense(database: ExpenseDatabase, expense: Expense) {
    database.expenseQueries.save(
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
        encodeParts(expense.parts),
    )
}

/**
 * Orders waiting for their bank payment also match one that came in some other way than a new
 * SMS, such as by sync from the phone that read it. Each waiting order splits an unsplit payment
 * of its amount within a day of it; orders waiting longer than a week are dropped, since their
 * payment isn't coming. Runs in one transaction with whatever called it. Returns whether anything
 * changed.
 */
internal fun settlePendingOrders(database: ExpenseDatabase, now: Long): Boolean = database.transactionWithResult {
    val values = database.expenseValueQueries
    var changed = false
    values.withPrefix(ExpenseRepository.ORDER_PREFIX + "%").executeAsList().forEach { row ->
        val order = runCatching { PendingJson.decodeFromString(PaidOrder.serializer(), row.value_) }.getOrNull()
        val window = ExpenseRepository.ORDER_WINDOW
        val payment = order?.let {
            database.expenseQueries.unsplitNear(it.amountPaise, it.at - window, it.at + window, ExpenseRepository::toExpense).executeAsOneOrNull()
        }
        when {
            order != null && payment != null -> writeExpense(database, payment.withOrder(order))
            order != null && now - order.at < ExpenseRepository.PENDING_DAYS * window -> return@forEach
        }
        values.save(row.key, "", now, 1)
        changed = true
    }
    changed
}