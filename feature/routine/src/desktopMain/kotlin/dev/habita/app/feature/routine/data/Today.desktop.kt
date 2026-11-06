package dev.habita.app.feature.routine.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal actual fun dayOf(millis: Long): Day {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    // Calendar counts from Sunday = 1; the app counts from Monday = 0.
    val weekday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    val title = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(millis))
    calendar.add(Calendar.DAY_OF_MONTH, 1)
    return Day(key, weekday, title, calendar.timeInMillis)
}