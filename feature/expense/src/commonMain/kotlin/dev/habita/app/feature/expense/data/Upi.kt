package dev.habita.app.feature.expense.data

import kotlinx.serialization.Serializable

/**
 * A UPI payment request from a QR code: "upi://pay?pa=shop@ybl&pn=Shop&am=120". [payeeId] is the
 * UPI id (pa), [payeeName] the name (pn), [amountPaise] the fixed amount (am) when the QR has one.
 * [raw] keeps every parameter so the request goes to the UPI app as the shop made it.
 */
data class UpiRequest(
    val payeeId: String,
    val payeeName: String?,
    val amountPaise: Long?,
    val note: String?,
    val raw: String,
    private val params: List<Pair<String, String>>,
) {
    /** Signed QR codes (verified shops) can't be changed, or the UPI app rejects them. */
    val isSigned: Boolean = params.any { it.first.equals("sign", ignoreCase = true) }

    /** The name to show: the QR's name, or the UPI id's name part. */
    val displayName: String = payeeName?.takeIf { it.isNotBlank() } ?: BankSms.displayName(payeeId)

    /**
     * The request to hand to the UPI app, with the amount and note filled in. A signed QR, or
     * one that already has an amount, goes as it was scanned.
     */
    fun toUri(amountPaise: Long, note: String?): String {
        if (isSigned || this.amountPaise != null) return raw
        val keep = params.filterNot { (key, _) -> key.lowercase() in setOf("am", "cu", "tn") }
        val added = buildList {
            add("am" to formatAmount(amountPaise))
            add("cu" to "INR")
            val text = note?.trim()?.takeIf { it.isNotEmpty() } ?: this@UpiRequest.note
            if (text != null) add("tn" to text.take(50))
        }
        return "upi://pay?" + (keep + added).joinToString("&") { (key, value) -> "$key=${encode(value)}" }
    }

    companion object {
        /** The request in a scanned QR, or `null` when it isn't a UPI payment QR. */
        fun parse(text: String): UpiRequest? {
            val trimmed = text.trim()
            if (!trimmed.lowercase().startsWith("upi://pay")) return null
            val query = trimmed.substringAfter('?', "")
            val params = query.split('&').filter { it.isNotEmpty() }.map { part ->
                decode(part.substringBefore('=')) to decode(part.substringAfter('=', ""))
            }
            fun value(key: String) = params.firstOrNull { it.first.equals(key, ignoreCase = true) }?.second?.takeIf { it.isNotBlank() }
            val payeeId = value("pa")?.takeIf { '@' in it } ?: return null
            return UpiRequest(
                payeeId = payeeId,
                payeeName = value("pn"),
                amountPaise = value("am")?.let(::parseRupees),
                note = value("tn"),
                raw = trimmed,
                params = params,
            )
        }

        private fun formatAmount(paise: Long): String = "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}"

        /** Percent-decoding as in a URL query: "%20" and "+" are spaces; bytes are read as UTF-8. */
        private fun decode(text: String): String {
            val bytes = mutableListOf<Byte>()
            var index = 0
            while (index < text.length) {
                val char = text[index]
                val code = if (char == '%' && index + 2 < text.length) text.substring(index + 1, index + 3).toIntOrNull(16) else null
                when {
                    code != null -> {
                        bytes += code.toByte()
                        index += 3
                    }
                    char == '+' -> {
                        bytes += ' '.code.toByte()
                        index++
                    }
                    else -> {
                        bytes += char.toString().encodeToByteArray().toList()
                        index++
                    }
                }
            }
            return bytes.toByteArray().decodeToString()
        }

        private fun encode(text: String): String = buildString {
            text.encodeToByteArray().forEach { byte ->
                val char = byte.toInt().toChar()
                if (byte >= 0 && (char.isLetterOrDigit() || char in "-_.~@")) {
                    append(char)
                } else {
                    append('%')
                    append(((byte.toInt() and 0xFF) or 0x100).toString(16).substring(1).uppercase())
                }
            }
        }
    }
}

/** What the UPI app said when it handed back, when it said anything. */
enum class UpiStatus { SUCCESS, FAILURE, UNKNOWN }

/** Reads the "Status=SUCCESS&txnId=…" text UPI apps return. */
fun upiStatus(response: String?): UpiStatus {
    val status = response
        ?.split('&')
        ?.firstOrNull { it.substringBefore('=').equals("status", ignoreCase = true) }
        ?.substringAfter('=')
        ?.lowercase()
    return when (status) {
        "success" -> UpiStatus.SUCCESS
        "failure", "failed" -> UpiStatus.FAILURE
        else -> UpiStatus.UNKNOWN
    }
}

/**
 * A payment started from a scanned QR, waiting for its bank SMS. When the SMS comes, the payment is
 * saved with this description and category instead of asking for them.
 */
@Serializable
data class PendingPayment(
    val id: String,
    val amountPaise: Long,
    val payeeId: String,
    val payeeName: String,
    val description: String,
    val categoryId: String?,
    val startedAt: Long,
) {
    /**
     * Whether a bank debit of [amountPaise] at [receivedAt] is this payment: the same amount, from
     * shortly before it started (clock differences) to [MATCH_WINDOW] after.
     */
    fun matches(amountPaise: Long, receivedAt: Long): Boolean =
        amountPaise == this.amountPaise && receivedAt in (startedAt - EARLY_SLACK)..(startedAt + MATCH_WINDOW)

    /** Old enough that no SMS is coming; shown with buttons to settle it by hand. */
    fun isStale(now: Long): Boolean = now > startedAt + MATCH_WINDOW

    companion object {
        const val MATCH_WINDOW = 30 * 60 * 1000L
        const val EARLY_SLACK = 2 * 60 * 1000L
    }
}