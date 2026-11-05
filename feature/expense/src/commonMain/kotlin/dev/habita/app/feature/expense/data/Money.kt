package dev.habita.app.feature.expense.data

/**
 * Rupees in Indian grouping, such as "₹1,23,456", with paise only when there are any
 * ("₹39.50").
 */
fun formatRupees(paise: Long, symbol: Boolean = true): String {
    val negative = paise < 0
    val absolute = if (negative) -paise else paise
    val rupees = absolute / 100
    val rest = absolute % 100
    val digits = rupees.toString()
    val grouped = if (digits.length <= 3) {
        digits
    } else {
        val head = digits.dropLast(3)
        val tail = digits.takeLast(3)
        head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
    }
    val decimals = if (rest == 0L) "" else "." + rest.toString().padStart(2, '0')
    return (if (negative) "-" else "") + (if (symbol) "₹" else "") + grouped + decimals
}

/**
 * Paise from typed rupees such as "39", "39.5" or "1,234.50", or `null` when it isn't a positive
 * amount.
 */
fun parseRupees(text: String): Long? {
    val clean = text.replace(",", "").trim()
    if (clean.isEmpty()) return null
    val parts = clean.split('.')
    if (parts.size > 2 || parts.any { part -> part.any { !it.isDigit() } }) return null
    val rupees = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
    val paise = parts.getOrNull(1)?.take(2)?.padEnd(2, '0')?.toLongOrNull() ?: 0
    return (rupees * 100 + paise).takeIf { it > 0 }
}