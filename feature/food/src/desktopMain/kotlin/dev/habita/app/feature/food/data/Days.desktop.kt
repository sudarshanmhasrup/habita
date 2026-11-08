package dev.habita.app.feature.food.data

import java.util.TimeZone

internal actual fun utcOffsetMillis(at: Long): Long = TimeZone.getDefault().getOffset(at).toLong()