package dev.habita.app.compose

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

internal actual fun currentHour(): Int =
    NSCalendar.currentCalendar.component(NSCalendarUnitHour, fromDate = NSDate()).toInt()

internal actual fun todayLabel(): String =
    NSDateFormatter().apply { setLocalizedDateFormatFromTemplate("EEEEdMMMM") }.stringFromDate(NSDate())