package dev.habita.app.feature.gym.ui

import androidx.compose.runtime.Composable

/** Desktop screens do not sleep mid-workout, so there is nothing to do. */
@Composable
internal actual fun KeepScreenOn() = Unit