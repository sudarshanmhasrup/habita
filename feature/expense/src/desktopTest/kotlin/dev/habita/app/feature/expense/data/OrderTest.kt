package dev.habita.app.feature.expense.data

import dev.habita.app.core.AppConfig
import dev.habita.app.core.Core
import dev.habita.app.core.data.DesktopDatabaseFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.habita.app.feature.expense.db.ExpenseDatabase
import dev.habita.app.shared.api.SyncCollections
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OrderTest {
    private fun repository() = ExpenseRepository(
        Core(AppConfig(serverUrl = "http://localhost:9", userName = "Test", loginEnabled = false), DesktopDatabaseFactory(Files.createTempDirectory("habita").toFile())),
    )

    // Now: opening the repository drops orders that waited over a week, by the real clock.
    private val at = System.currentTimeMillis()
    private val sms = "Sent Rs.1259.00\nFrom HDFC Bank A/C *1234\nTo ZEPTO\nOn 08/11/26\nRef 531200000001\nNot You?"
    private val parts = listOf(ExpensePart("food", 810_00, "Milk, dahi"), ExpensePart(Defaults.HOUSEHOLD_ID, 449_00, "Kettle"))
    private fun order(bankSms: Boolean = true) = PaidOrder("Zepto order", 1_259_00, at, parts, bankSms = bankSms)

    @Test
    fun `an order logged after its bank sms splits that payment`() = runBlocking {
        val repository = repository()
        val payment = assertIs<SmsOutcome.Recorded>(repository.recordSms(sms, "AX-HDFCBK", at - 60_000)).expense

        assertIs<OrderOutcome.Matched>(repository.recordOrder(order()))
        val split = assertNotNull(repository.expense(payment.id))
        assertEquals(parts, split.parts)
        assertEquals("Zepto order", split.description)
        assertEquals("531200000001", split.smsRef)
    }

    @Test
    fun `an order logged before its bank sms waits and splits the payment when it comes`() = runBlocking {
        val repository = repository()
        assertEquals(OrderOutcome.Waiting, repository.recordOrder(order()))

        val payment = assertIs<SmsOutcome.Recorded>(repository.recordSms(sms, "AX-HDFCBK", at + 5 * 60_000)).expense
        assertEquals(parts, payment.parts)
        // Taken once: a second payment of the same amount stays whole.
        val second = sms.replace("531200000001", "531200000002")
        assertTrue(assertIs<SmsOutcome.Recorded>(repository.recordSms(second, "AX-HDFCBK", at + 10 * 60_000)).expense.parts.isEmpty())
    }

    @Test
    fun `a wallet order is saved at once, split`() = runBlocking {
        val repository = repository()
        val added = assertIs<OrderOutcome.Added>(repository.recordOrder(order(bankSms = false))).expense
        assertEquals(ExpenseSource.MANUAL, added.source)
        assertEquals(parts, repository.expense(added.id)?.parts)
    }

    @Test
    fun `every install has a household category`() = runBlocking {
        assertTrue(repository().categories.first().any { it.id == Defaults.HOUSEHOLD_ID && it.name == "Household" })
    }

    @Test
    fun `a split payment doesn't set the payee's usual category`() = runBlocking {
        val repository = repository()
        repository.recordSms(sms, "AX-HDFCBK", at - 60_000)
        repository.recordOrder(order())

        // The next Zepto payment, a different amount, isn't sorted into Food on its own.
        val next = sms.replace("1259.00", "449.00").replace("531200000001", "531200000009")
        val payment = assertIs<SmsOutcome.Recorded>(repository.recordSms(next, "AX-HDFCBK", at + 2 * 24 * 60 * 60_000L)).expense
        assertEquals(null, payment.categoryId)
    }

    @Test
    fun `a waiting order splits a payment that comes in by sync, and expires after a week`() = runBlocking {
        val repository = repository()
        assertEquals(OrderOutcome.Waiting, repository.recordOrder(order()))
        // On another phone the bank SMS came in; its payment reaches this one by sync.
        val phone = ExpenseDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpenseDatabase.Schema.create(it) })
        phone.expenseQueries.save("synced", 1_259_00, "Zepto", "zepto", null, "ONLINE", "SMS", "531200000003", at + 60_000, at + 60_000, 0, null)
        val record = expenseSyncTables(phone).single { it.collection == SyncCollections.EXPENSES }.dirty().single()
        repository.syncTable(SyncCollections.EXPENSES).applyRemote(listOf(record))

        assertEquals(parts, repository.expense("synced")?.parts)

        assertEquals(OrderOutcome.Waiting, repository.recordOrder(order().copy(amountPaise = 999_00)))
        assertFalse(repository.settlePendingOrders(at + 60_000))
        assertTrue(repository.settlePendingOrders(at + 8 * 24 * 60 * 60_000L))
        assertFalse(repository.settlePendingOrders(at + 9 * 24 * 60 * 60_000L))
    }
}