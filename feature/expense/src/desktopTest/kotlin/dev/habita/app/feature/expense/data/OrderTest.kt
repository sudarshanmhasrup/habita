package dev.habita.app.feature.expense.data

import dev.habita.app.core.AppConfig
import dev.habita.app.core.Core
import dev.habita.app.core.data.DesktopDatabaseFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OrderTest {
    private fun repository() = ExpenseRepository(
        Core(AppConfig(serverUrl = "http://localhost:9", userName = "Test", loginEnabled = false), DesktopDatabaseFactory(Files.createTempDirectory("habita").toFile())),
    )

    private val at = 1_762_600_000_000L
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
}