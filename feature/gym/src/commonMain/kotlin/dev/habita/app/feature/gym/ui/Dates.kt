package dev.habita.app.feature.gym.ui

/** A date as "3 Nov", in the device's time zone. */
internal expect fun shortDate(millis: Long): String

/** Midnight at the start of the Monday of the week holding [millis], in the device's time zone. */
internal expect fun startOfWeek(millis: Long): Long

/** Whole calendar days from [from] to [to], in the device's time zone. */
internal expect fun daysBetween(from: Long, to: Long): Int

/** A date as "2026-11-03", for file names. */
internal expect fun backupDate(millis: Long): String