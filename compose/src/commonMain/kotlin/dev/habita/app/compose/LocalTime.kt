package dev.habita.app.compose

/** The hour of the day, 0 to 23, in the device's time zone. */
internal expect fun currentHour(): Int

/** Today as "Tuesday, 3 November", in the device's language. */
internal expect fun todayLabel(): String