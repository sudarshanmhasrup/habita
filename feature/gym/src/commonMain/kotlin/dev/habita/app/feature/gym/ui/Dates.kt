package dev.habita.app.feature.gym.ui

import dev.habita.app.core.data.nowMillis

/** A date as "3 Nov", in the device's time zone. */
internal expect fun shortDate(millis: Long): String

/** Midnight at the start of the Monday of the week holding [millis], in the device's time zone. */
internal expect fun startOfWeek(millis: Long): Long

/** Whole calendar days from [from] to [to], in the device's time zone. */
internal expect fun daysBetween(from: Long, to: Long): Int

/** A date as "2026-11-03", for file names. */
internal expect fun backupDate(millis: Long): String

/** Today's local date as "2026-11-07", the id of the day's body log. */
internal fun today(): String = backupDate(nowMillis())