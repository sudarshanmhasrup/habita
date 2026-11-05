package dev.habita.app.feature.expense.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

actual fun monthOf(millis: Long): YearMonth {
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    return YearMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
}

actual fun startOf(month: YearMonth): Long = Calendar.getInstance().apply {
    clear()
    set(month.year, month.month - 1, 1, 0, 0, 0)
}.timeInMillis

actual fun monthLabel(month: YearMonth): String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(startOf(month)))

actual fun dayLabel(millis: Long): String = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(millis))

actual fun timeLabel(millis: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis)).lowercase()

actual fun shortDayLabel(millis: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))

actual fun fileMonth(month: YearMonth): String = "${month.year}-${month.month.toString().padStart(2, '0')}"

actual fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis