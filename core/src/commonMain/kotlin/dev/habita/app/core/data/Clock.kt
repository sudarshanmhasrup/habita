package dev.habita.app.core.data

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The current time in epoch milliseconds. */
@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** A new random id for a record created on this device. */
@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()