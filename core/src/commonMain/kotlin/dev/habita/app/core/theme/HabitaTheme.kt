package dev.habita.app.core.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalColors = staticCompositionLocalOf { LightColors }
private val LocalTypography = staticCompositionLocalOf<HabitaTypography> { error("No HabitaTheme") }

@Composable
fun HabitaTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides habitaTypography(),
        LocalIndication provides PressIndication(colors.text),
        LocalTextSelectionColors provides TextSelectionColors(colors.accent, colors.accent.copy(alpha = 0.3f)),
        content = content,
    )
}

object HabitaTheme {
    val colors: HabitaColors
        @Composable @ReadOnlyComposable get() = LocalColors.current

    val typography: HabitaTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current
}