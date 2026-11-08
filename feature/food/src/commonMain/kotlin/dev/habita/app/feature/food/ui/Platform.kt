package dev.habita.app.feature.food.ui

import androidx.compose.runtime.Composable

/** Returns a function that puts text on the clipboard, for pasting into another app. */
@Composable
internal expect fun rememberTextCopier(): (String) -> Unit