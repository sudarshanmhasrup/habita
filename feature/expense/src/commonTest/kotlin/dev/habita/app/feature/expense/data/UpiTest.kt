package dev.habita.app.feature.expense.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpiTest {
    @Test
    fun readsAShopQrWithoutAmount() {
        val request = UpiRequest.parse("upi://pay?pa=chaipoint@ybl&pn=Chai%20Point&mc=5812&cu=INR")!!
        assertEquals("chaipoint@ybl", request.payeeId)
        assertEquals("Chai Point", request.displayName)
        assertNull(request.amountPaise)
        assertFalse(request.isSigned)
    }

    @Test
    fun readsAmountAndFallsBackToTheIdForTheName() {
        val request = UpiRequest.parse("UPI://pay?pa=rapido.rides@axl&am=43.50")!!
        assertEquals(4350L, request.amountPaise)
        assertEquals("Rapido Rides", request.displayName)
    }

    @Test
    fun ignoresQrsThatArentUpiPayments() {
        assertNull(UpiRequest.parse("https://example.com"))
        assertNull(UpiRequest.parse("upi://pay?pn=NoId"))
        assertNull(UpiRequest.parse("upi://pay?pa=not-an-id"))
    }

    @Test
    fun fillsInTheAmountAndNote() {
        val request = UpiRequest.parse("upi://pay?pa=chaipoint@ybl&pn=Chai%20Point&mc=5812&cu=INR")!!
        assertEquals(
            "upi://pay?pa=chaipoint@ybl&pn=Chai%20Point&mc=5812&am=120.00&cu=INR&tn=Evening%20chai",
            request.toUri(120_00, "Evening chai"),
        )
    }

    @Test
    fun leavesSignedAndFixedAmountQrsAlone() {
        val signed = "upi://pay?pa=shop@hdfc&pn=Shop&sign=MEUCIQ"
        assertEquals(signed, UpiRequest.parse(signed)!!.toUri(500_00, "Groceries"))
        val fixed = "upi://pay?pa=shop@hdfc&pn=Shop&am=99.00"
        assertEquals(fixed, UpiRequest.parse(fixed)!!.toUri(99_00, "Groceries"))
    }

    @Test
    fun readsTheUpiAppsReply() {
        assertEquals(UpiStatus.SUCCESS, upiStatus("txnId=abc&responseCode=00&Status=SUCCESS&txnRef=1"))
        assertEquals(UpiStatus.FAILURE, upiStatus("txnId=abc&Status=FAILURE"))
        assertEquals(UpiStatus.UNKNOWN, upiStatus(null))
        assertEquals(UpiStatus.UNKNOWN, upiStatus("Status=SUBMITTED"))
    }

    @Test
    fun matchesTheBankSmsBySameAmountSoonAfter() {
        val started = 1_000_000_000L
        val payment = PendingPayment("p", 120_00, "chaipoint@ybl", "Chai Point", "Evening chai", "food", started)
        assertTrue(payment.matches(120_00, started + 40_000))
        assertTrue(payment.matches(120_00, started - 60_000))
        assertFalse(payment.matches(121_00, started + 40_000))
        assertFalse(payment.matches(120_00, started + PendingPayment.MATCH_WINDOW + 1))
        assertTrue(payment.isStale(started + PendingPayment.MATCH_WINDOW + 1))
    }
}