package dev.habita.app.feature.expense.data

import dev.habita.app.core.data.nowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

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

/** The latest salary day a budget month can start on, so every month has it. */
internal const val MAX_PAY_DAY = 28

internal const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/**
 * The first millisecond of the budget month named [month]: midnight on [payDay] of that month.
 * With a salary on the 5th, November runs from 5 November to the end of 4 December. A [payDay]
 * of 1 means calendar months.
 */
internal fun periodStart(month: YearMonth, payDay: Int): Long {
    val day = payDay.coerceIn(1, MAX_PAY_DAY)
    if (day == 1) return startOf(month)
    // Midday of the day, then back to its midnight, so a clock change on the way can't shift it.
    return startOfDay(startOf(month) + (day - 1) * DAY_MILLIS + DAY_MILLIS / 2)
}

/** The budget month holding [millis], with months starting on [payDay]. */
internal fun periodOf(millis: Long, payDay: Int): YearMonth {
    val month = monthOf(millis)
    return if (millis < periodStart(month, payDay)) month.previous() else month
}

/** The time, every minute, so a screen left open moves on when a new month starts. */
private val clock: Flow<Long> = flow {
    while (true) {
        emit(nowMillis())
        delay(60_000)
    }
}

/** The budget month it is now, following the clock and the salary day. */
internal fun currentPeriod(payDay: Flow<Int>): Flow<YearMonth> =
    combine(clock, payDay) { now, day -> periodOf(now, day) }.distinctUntilChanged()