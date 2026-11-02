package dev.habita.app.feature.gym.ui

import androidx.compose.runtime.Composable

/** Keeps the screen awake while shown, so the rest timer stays visible between sets. */
@Composable
internal expect fun KeepScreenOn()