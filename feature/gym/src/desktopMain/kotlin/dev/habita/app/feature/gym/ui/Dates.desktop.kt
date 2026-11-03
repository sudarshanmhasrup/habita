package dev.habita.app.feature.gym.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal actual fun shortDate(millis: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))

internal actual fun startOfWeek(millis: Long): Long =
    midnight(millis).apply {
        val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
    }.timeInMillis

internal actual fun daysBetween(from: Long, to: Long): Int {
    val start = midnight(from)
    val end = midnight(to)
    var days = 0
    while (start.before(end)) {
        start.add(Calendar.DAY_OF_YEAR, 1)
        days++
    }
    return days
}

private fun midnight(millis: Long): Calendar = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}