package dev.habita.app.compose

import java.util.Calendar

internal actual fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)