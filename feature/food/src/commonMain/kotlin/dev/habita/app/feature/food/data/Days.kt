package dev.habita.app.feature.food.data

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** How far the device's time zone is ahead of UTC at [at], in milliseconds. */
internal expect fun utcOffsetMillis(at: Long): Long

/** The local day holding [millis]: days since 1 January 1970, in the device's time zone. */
fun dayOf(millis: Long): Int = (millis + utcOffsetMillis(millis)).floorDiv(DAY_MILLIS).toInt()

private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Thu": 1 January 1970 was a Thursday. */
fun weekdayName(day: Int): String = weekdays[(day + 3).mod(7)]

/** "Thu 12 Nov". */
fun dateLabel(day: Int): String {
    val (_, month, dayOfMonth) = civil(day)
    return "${weekdayName(day)} $dayOfMonth ${months[month - 1]}"
}

/** "14/11": day and month, the way a best-before date is written. */
fun shortDate(day: Int): String {
    val (_, month, dayOfMonth) = civil(day)
    return "$dayOfMonth/$month"
}

/** When something runs out, as a person would say it: "today", "tomorrow", "Thu" or "Thu 12 Nov". */
fun whenLabel(day: Int, today: Int): String = when (val ahead = day - today) {
    in Int.MIN_VALUE..0 -> "today"
    1 -> "tomorrow"
    in 2..6 -> weekdayName(day)
    else -> dateLabel(day)
}

/** The day count for a calendar date (Howard Hinnant's days-from-civil). */
fun dayFrom(year: Int, month: Int, dayOfMonth: Int): Int {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * ((month + 9) % 12) + 2) / 5 + dayOfMonth - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097 + doe - 719_468
}

/**
 * A date as printed on a pack: "14/11", "14-11-2026", "14.11.26" or "14 Nov". Without a year it is
 * the next such date from about two months ago, so "14/1" in December means January. `null` when
 * it doesn't read as a real date.
 */
fun parseDate(text: String, today: Int): Int? {
    val match = Regex("^\\s*(\\d{1,2})\\s*[/.\\- ]\\s*(\\d{1,2}|[A-Za-z]{3,})\\.?\\s*(?:[/.\\- ,]\\s*(\\d{2}|\\d{4}))?\\s*$").find(text) ?: return null
    val (dayText, monthText, yearText) = match.destructured
    val dayOfMonth = dayText.toInt()
    val month = monthText.toIntOrNull()
        ?: months.indexOfFirst { monthText.take(3).equals(it, ignoreCase = true) }.takeIf { it >= 0 }?.plus(1)
        ?: return null
    if (month !in 1..12 || dayOfMonth !in 1..31) return null
    fun real(year: Int) = dayFrom(year, month, dayOfMonth).takeIf { civil(it) == Triple(year, month, dayOfMonth) }
    if (yearText.isNotEmpty()) return real(yearText.toInt().let { if (it < 100) 2000 + it else it })
    val thisYear = civil(today).first
    return listOf(thisYear - 1, thisYear, thisYear + 1).mapNotNull(::real).firstOrNull { it >= today - 60 }
}

/** Year, month (1–12) and day of month for a day count (Howard Hinnant's civil-from-days). */
private fun civil(days: Int): Triple<Int, Int, Int> {
    val z = days + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val y = yoe + era * 400 + if (m <= 2) 1 else 0
    return Triple(y, m, d)
}