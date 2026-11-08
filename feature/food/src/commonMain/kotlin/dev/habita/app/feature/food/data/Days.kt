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

/** When something runs out, as a person would say it: "today", "tomorrow", "Thu" or "Thu 12 Nov". */
fun whenLabel(day: Int, today: Int): String = when (val ahead = day - today) {
    in Int.MIN_VALUE..0 -> "today"
    1 -> "tomorrow"
    in 2..6 -> weekdayName(day)
    else -> dateLabel(day)
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