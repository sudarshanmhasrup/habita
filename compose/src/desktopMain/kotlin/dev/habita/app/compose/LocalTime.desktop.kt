package dev.habita.app.compose

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal actual fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

internal actual fun todayLabel(): String = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())