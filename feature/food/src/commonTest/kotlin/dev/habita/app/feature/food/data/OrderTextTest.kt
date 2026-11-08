package dev.habita.app.feature.food.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderTextTest {
    private val plan = (PlanText.parse(PlanText.EXAMPLE) as PlanResult.Ok).plan

    @Test
    fun `the template reads, with pantry items and other lines`() {
        val order = assertIs<OrderResult.Ok>(OrderText.parse(OrderText.TEMPLATE, plan)).order

        assertEquals("Zepto", order.shop)
        assertEquals(OrderPayment.BANK, order.payment)
        assertEquals(806_00, order.totalPaise)
        assertEquals("milk", order.lines[0].itemId)
        assertEquals(21.0, order.lines[0].quantity)
        assertNull(order.lines[1].itemId)
        assertEquals("Household", order.lines[1].category)
    }

    @Test
    fun `wallet and cash payments are told apart`() {
        assertEquals(OrderPayment.WALLET, (OrderText.parse(OrderText.TEMPLATE.replace("Paid: UPI", "Paid: Blinkit wallet"), plan) as OrderResult.Ok).order.payment)
        assertEquals(OrderPayment.CASH, (OrderText.parse(OrderText.TEMPLATE.replace("Paid: UPI", "Paid: COD"), plan) as OrderResult.Ok).order.payment)
    }

    @Test
    fun `lines must add up to the total`() {
        val problems = assertIs<OrderResult.Error>(OrderText.parse(OrderText.TEMPLATE.replace("Total: 806", "Total: 900"), plan)).problems
        assertTrue(problems.single().contains("add up to ₹806, not ₹900"), problems.toString())
    }

    @Test
    fun `an order without a shop or table says so`() {
        val problems = assertIs<OrderResult.Error>(OrderText.parse("Paid: UPI", plan)).problems
        assertEquals(2, problems.size)
    }
}