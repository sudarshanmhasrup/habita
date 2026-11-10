package dev.habita.app.feature.expense.data

/** A payment out of the user's account, read from a bank SMS. */
data class BankDebit(
    val amountPaise: Long,
    /** Who was paid, as the bank wrote it: a UPI id such as "rapido@ybl" or a name. */
    val payee: String?,
    /** The bank's reference number (UPI ref, RRN), when the SMS has one. */
    val reference: String?,
)

/**
 * Reads debit SMS from Indian banks: UPI payments, card spends, transfers and auto-debits. Every
 * bank words them differently, so this looks for the common pieces (an amount, a debit word, who
 * was paid, a reference number) rather than one fixed format. Credits, OTPs, reminders and failed
 * payments are ignored.
 */
object BankSms {
    private val debitWords = Regex("""\b(debited|debit|spent|paid|sent|withdrawn|purchase|transferred)\b""")
    private val creditWords = Regex("""\b(credited|received|deposited)\b""")
    private val accountWords = Regex("""\b(a/c|acct|account|card|upi|bank|ac)\b""")
    private val ignoreWords = Regex(
        """\b(otp|one time password|will be debited|is due|due on|due date|request(ed)?|collect|""" +
            """failed|declined|reversed|refund(ed)?|cashback|has been registered|has been created|""" +
            """reminder|auto ?pay set ?up)\b""",
    )

    private const val NUMBER = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val currencyAmount = Regex("""(?:\brs\.?|\binr|₹)\s*$NUMBER""")
    private val debitAmount = Regex("""debited\s+(?:by|for|with)?\s*(?:rs\.?|inr|₹)?\s*$NUMBER""")

    private val referencePatterns = listOf(
        Regex("""(?:upi\s*ref(?:\s*no)?|ref(?:erence)?(?:\s*no)?|refno|rrn|utr|upi)\s*[:.#-]?\s*([0-9]{6,})"""),
        Regex("""upi/p2[am]/([0-9]{6,})"""),
    )

    private val payeePatterns = listOf(
        // "to rapido@ybl", "to VPA rapido@ybl"
        Regex("""\b(?:to|vpa)\s+(?:vpa\s+)?([a-z0-9._-]+@[a-z0-9]+)"""),
        // ICICI: "debited for Rs 39.00 on 04-Nov-25; RAPIDO credited."
        Regex(""";\s*([^;.\n]+?)\s+credited"""),
        // Axis: "UPI/P2M/530812345678/RAPIDO"
        Regex("""upi/p2[am]/[0-9]+/([^/\n]+)"""),
        // SBI: "trf to RAPIDO Refno 5308"
        Regex("""\btrf\s+to\s+(.+?)(?=\s+(?:ref|refno|on|upi)\b|[.\n]|$)"""),
        // HDFC and others: "To RAPIDO On 04/11/25", "to Rapido. Ref"
        Regex("""\bto\s+(?!your\b|a/c\b|acct\b|account\b)(.+?)(?=\s+(?:on|ref|refno|upi|via|from)\b|[.,;\n]|$)"""),
        // Cards: "spent on HDFC Bank Card x1234 at AMAZON on 2025-11-04"
        Regex("""\bat\s+(.+?)(?=\s+(?:on|ref|via)\b|[.,;\n]|$)"""),
    )

    /** The debit in [body], or `null` when it isn't a bank SMS about money leaving the account. */
    fun parse(body: String, sender: String? = null): BankDebit? {
        // A text from a person, not a bank, even if it mentions money.
        if (sender != null && sender.filter { it.isLetter() }.isEmpty()) return null
        val text = body.replace('\r', '\n').lowercase()
        if (ignoreWords.containsMatchIn(text)) return null
        if (!accountWords.containsMatchIn(text)) return null
        val debitAt = debitWords.find(text)?.range?.first ?: return null
        val creditAt = creditWords.find(text)?.range?.first
        if (creditAt != null && creditAt < debitAt) return null

        val amount = (currencyAmount.find(text) ?: debitAmount.find(text))
            ?.groupValues?.get(1)
            ?.let(::parseRupees)
            ?: return null
        val reference = referencePatterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
        val payee = payeePatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.get(1)?.let(::cleanPayee)
        }
        return BankDebit(amount, payee, reference)
    }

    private fun cleanPayee(raw: String): String? {
        val payee = raw.replace(Regex("""\s+"""), " ").trim().trim('.', ',', ';', ':', '-').take(40)
        if (payee.length < 2) return null
        if (payee.any { it.isLetter() }.not()) return null
        if (Regex("""\b(a/c|acct|account|card|bank)\b""").containsMatchIn(payee) && '@' !in payee) return null
        return payee
    }

    /** The payee as a key for remembering its category: lowercase with spaces collapsed. */
    fun payeeKey(payee: String): String = payee.lowercase().replace(Regex("""\s+"""), " ").trim()

    /** What a payment from an SMS is called until it's given a name: its payee, or "Bank payment". */
    fun description(payee: String?): String = payee?.let(::displayName) ?: "Bank payment"

    /**
     * A readable name for a payee: the part of a UPI id before the "@", or the name, in title
     * case. "rapido@ybl" becomes "Rapido".
     */
    fun displayName(payee: String): String {
        val name = payee.substringBefore('@').replace(Regex("""[._-]+"""), " ").trim()
        return name.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }.ifEmpty { payee }
    }
}