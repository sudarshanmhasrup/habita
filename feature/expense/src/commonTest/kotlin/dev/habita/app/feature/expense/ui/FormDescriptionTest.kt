package dev.habita.app.feature.expense.ui

import dev.habita.app.feature.expense.data.Expense
import dev.habita.app.feature.expense.data.ExpenseSource
import dev.habita.app.feature.expense.data.PaymentMode
import kotlin.test.Test
import kotlin.test.assertEquals

class FormDescriptionTest {
    private fun payment(description: String, payee: String?, source: ExpenseSource = ExpenseSource.SMS) = Expense(
        id = "1",
        amountPaise = 326_00,
        description = description,
        payee = payee,
        categoryId = null,
        mode = PaymentMode.ONLINE,
        source = source,
        smsRef = null,
        paidAt = 0,
    )

    @Test
    fun anSmsPaymentStillNamedAfterItsPayeeStartsEmpty() {
        assertEquals("", formDescription(payment("Zomato Media Private", "zomato media private")))
        assertEquals("", formDescription(payment("Bank payment", null)))
    }

    @Test
    fun aNameTheOwnerGaveStays() {
        assertEquals("Lunch", formDescription(payment("Lunch", "zomato media private")))
        assertEquals("Lunch", formDescription(payment("Lunch", null, ExpenseSource.MANUAL)))
    }
}