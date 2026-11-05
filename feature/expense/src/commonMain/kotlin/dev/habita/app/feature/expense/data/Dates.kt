package dev.habita.app.feature.expense.data

/** The month holding [millis], in the device's time zone. */
expect fun monthOf(millis: Long): YearMonth

/** The first millisecond of [month], in the device's time zone. */
expect fun startOf(month: YearMonth): Long

/** "November 2026". */
expect fun monthLabel(month: YearMonth): String

/** "Wed, 4 Nov". */
expect fun dayLabel(millis: Long): String

/** "9:41 pm". */
expect fun timeLabel(millis: Long): String

/** "4 Nov", for tables. */
expect fun shortDayLabel(millis: Long): String

/** "2026-11", for file names. */
expect fun fileMonth(month: YearMonth): String

/** Midnight at the start of the day holding [millis]. */
expect fun startOfDay(millis: Long): Long