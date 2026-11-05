package dev.habita.app.feature.expense.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BankSmsTest {
    @Test
    fun hdfcUpiSent() {
        val debit = BankSms.parse(
            "Sent Rs.39.00\nFrom HDFC Bank A/C *1234\nTo RAPIDO\nOn 04/11/25\nRef 530812345678\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
            sender = "AX-HDFCBK",
        )!!
        assertEquals(3900, debit.amountPaise)
        assertEquals("rapido", debit.payee)
        assertEquals("530812345678", debit.reference)
    }

    @Test
    fun sbiUpiWithoutCurrency() {
        val debit = BankSms.parse(
            "Dear UPI user A/C X1234 debited by 217.0 on date 04Nov25 trf to SWIGGY Refno 530899887766. If not u? call 1800111109. -SBI",
            sender = "VM-SBIUPI",
        )!!
        assertEquals(21700, debit.amountPaise)
        assertEquals("swiggy", debit.payee)
        assertEquals("530899887766", debit.reference)
    }

    @Test
    fun iciciDebitNamesPayeeBeforeCredited() {
        val debit = BankSms.parse(
            "ICICI Bank Acct XX123 debited for Rs 60.00 on 04-Nov-25; ZOMATO LTD credited. UPI:530811112222. Call 18002662 for dispute.",
            sender = "JD-ICICIB",
        )!!
        assertEquals(6000, debit.amountPaise)
        assertEquals("zomato ltd", debit.payee)
        assertEquals("530811112222", debit.reference)
    }

    @Test
    fun axisUpiPath() {
        val debit = BankSms.parse(
            "INR 1,500.00 debited\nA/c no. XX1234\n04-11-25, 09:12:44\nUPI/P2M/530855554444/CULT FIT\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank",
            sender = "AD-AXISBK",
        )!!
        assertEquals(150000, debit.amountPaise)
        assertEquals("cult fit", debit.payee)
        assertEquals("530855554444", debit.reference)
    }

    @Test
    fun kotakSentToUpiId() {
        val debit = BankSms.parse(
            "Sent Rs.500.00 from Kotak Bank AC X1234 to abhimanyu.k@okaxis on 04-11-25.UPI Ref 530877776666. Not you, https://kotak.com/KBANKT/Fraud",
            sender = "BZ-KOTAKB",
        )!!
        assertEquals(50000, debit.amountPaise)
        assertEquals("abhimanyu.k@okaxis", debit.payee)
        assertEquals("530877776666", debit.reference)
    }

    @Test
    fun cardSpendAtMerchant() {
        val debit = BankSms.parse(
            "Rs 1,200.00 spent on HDFC Bank Card x4321 at NYKAA on 2025-11-04:18:22:10. Avl bal Rs 20,000. Not you? Call 18002586161",
            sender = "AX-HDFCBK",
        )!!
        assertEquals(120000, debit.amountPaise)
        assertEquals("nykaa", debit.payee)
    }

    @Test
    fun autoDebitWithoutPayeeStillCounts() {
        val debit = BankSms.parse(
            "Your A/c XX1234 is debited with INR 3,250.00 towards NACH. Avl Bal INR 40,120.50",
            sender = "VM-BOIIND",
        )!!
        assertEquals(325000, debit.amountPaise)
    }

    @Test
    fun ignoresCredits() {
        assertNull(BankSms.parse("Rs 48,000.00 credited to your A/c XX1234 on 01-11-25 by NEFT. Avl Bal Rs 60,000", "AX-HDFCBK"))
        assertNull(BankSms.parse("Your A/c XX1234 has been credited with INR 500.00 by VPA abhimanyu@okaxis. Ref 530877776666", "VM-SBIUPI"))
    }

    @Test
    fun ignoresOtpsRemindersAndFailures() {
        assertNull(BankSms.parse("OTP for txn of Rs 500 on your HDFC Bank Card is 123456. Do not share.", "AX-HDFCBK"))
        assertNull(BankSms.parse("Rs 3,250 will be debited from your A/c XX1234 on 05-11-25 towards EMI.", "VM-BOIIND"))
        assertNull(BankSms.parse("Your payment of Rs 39 to rapido@ybl from A/c XX1234 has failed. Ref 530812345678", "AX-HDFCBK"))
        assertNull(BankSms.parse("abhimanyu@okaxis has requested Rs 500 from your account via UPI. Pay in app.", "AX-HDFCBK"))
    }

    @Test
    fun ignoresTextsFromPeople() {
        assertNull(BankSms.parse("Sent Rs 500 to your account bro, check", sender = "+919876543210"))
    }

    @Test
    fun displayNames() {
        assertEquals("Rapido", BankSms.displayName("rapido@ybl"))
        assertEquals("Abhimanyu K", BankSms.displayName("abhimanyu.k@okaxis"))
        assertEquals("Zomato Ltd", BankSms.displayName("zomato ltd"))
    }

    @Test
    fun money() {
        assertEquals("₹39", formatRupees(3900))
        assertEquals("₹4,493", formatRupees(449300))
        assertEquals("₹1,23,456.50", formatRupees(12345650))
        assertEquals("-₹500", formatRupees(-50000))
        assertEquals(3950L, parseRupees("39.5"))
        assertEquals(123450L, parseRupees("1,234.50"))
        assertNull(parseRupees("0"))
        assertNull(parseRupees("abc"))
    }
}