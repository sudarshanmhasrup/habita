package dev.habita.app.feature.gym.ui

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitWeekday
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.dateByAddingTimeInterval
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

internal actual fun shortDate(millis: Long): String =
    NSDateFormatter().apply { setLocalizedDateFormatFromTemplate("dMMM") }.stringFromDate(date(millis))

internal actual fun backupDate(millis: Long): String =
    NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd"
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
    }.stringFromDate(date(millis))

internal actual fun startOfWeek(millis: Long): Long {
    val calendar = NSCalendar.currentCalendar
    val midnight = calendar.startOfDayForDate(date(millis))
    // Weekdays run from 1 (Sunday) to 7 (Saturday); weeks here start on Monday.
    val weekday = calendar.component(NSCalendarUnitWeekday, fromDate = midnight).toInt()
    val daysSinceMonday = (weekday - 2 + 7) % 7
    val monday = calendar.dateByAddingUnit(NSCalendarUnitDay, value = -daysSinceMonday.toLong(), toDate = midnight, options = 0u)
        ?: midnight.dateByAddingTimeInterval(-daysSinceMonday * 86_400.0)
    return millis(monday)
}

internal actual fun daysBetween(from: Long, to: Long): Int {
    val calendar = NSCalendar.currentCalendar
    val days = calendar.components(
        NSCalendarUnitDay,
        fromDate = calendar.startOfDayForDate(date(from)),
        toDate = calendar.startOfDayForDate(date(to)),
        options = 0u,
    ).day.toInt()
    return days.coerceAtLeast(0)
}

private fun date(millis: Long): NSDate = NSDate.dateWithTimeIntervalSince1970(millis / 1000.0)

private fun millis(date: NSDate): Long = (date.timeIntervalSince1970 * 1000).toLong()