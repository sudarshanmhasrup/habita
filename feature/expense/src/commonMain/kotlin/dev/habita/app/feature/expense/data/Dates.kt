package dev.habita.app.feature.expense.data

import dev.habita.app.core.data.nowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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

/** This month, checked every minute, so a screen left open moves on when a new month starts. */
internal val currentMonth: Flow<YearMonth> = flow {
    while (true) {
        emit(monthOf(nowMillis()))
        delay(60_000)
    }
}.distinctUntilChanged()