package dev.habita.app.feature.expense.data

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

actual fun monthOf(millis: Long): YearMonth {
    val calendar = NSCalendar.currentCalendar
    val date = date(millis)
    return YearMonth(
        calendar.component(NSCalendarUnitYear, fromDate = date).toInt(),
        calendar.component(NSCalendarUnitMonth, fromDate = date).toInt(),
    )
}

actual fun startOf(month: YearMonth): Long {
    val components = NSDateComponents().apply {
        year = month.year.toLong()
        this.month = month.month.toLong()
        day = 1
    }
    return millis(NSCalendar.currentCalendar.dateFromComponents(components)!!)
}

actual fun monthLabel(month: YearMonth): String = format("MMMMyyyy", startOf(month))

actual fun dayLabel(millis: Long): String = format("EEEdMMM", millis)

actual fun timeLabel(millis: Long): String = format("jmm", millis).lowercase()

actual fun shortDayLabel(millis: Long): String = format("dMMM", millis)

actual fun fileMonth(month: YearMonth): String = "${month.year}-${month.month.toString().padStart(2, '0')}"

actual fun startOfDay(millis: Long): Long = millis(NSCalendar.currentCalendar.startOfDayForDate(date(millis)))

private fun format(template: String, millis: Long): String =
    NSDateFormatter().apply { setLocalizedDateFormatFromTemplate(template) }.stringFromDate(date(millis))

private fun date(millis: Long): NSDate = NSDate.dateWithTimeIntervalSince1970(millis / 1000.0)

private fun millis(date: NSDate): Long = (date.timeIntervalSince1970 * 1000).toLong()