package dev.habita.app.feature.routine.data

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitWeekday
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

internal actual fun dayOf(millis: Long): Day {
    val calendar = NSCalendar.currentCalendar
    val date = NSDate.dateWithTimeIntervalSince1970(millis / 1000.0)
    val start = calendar.startOfDayForDate(date)
    val next = calendar.dateByAddingUnit(NSCalendarUnitDay, value = 1, toDate = start, options = 0u)!!
    // NSCalendar counts from Sunday = 1; the app counts from Monday = 0.
    val weekday = (calendar.component(NSCalendarUnitWeekday, fromDate = date).toInt() + 5) % 7
    val key = NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        dateFormat = "yyyy-MM-dd"
    }.stringFromDate(date)
    val title = NSDateFormatter().apply { setLocalizedDateFormatFromTemplate("EEEEdMMMM") }.stringFromDate(date)
    return Day(key, weekday, title, (next.timeIntervalSince1970 * 1000).toLong())
}