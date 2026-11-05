package dev.habita.app.core.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalColors = staticCompositionLocalOf { Accent.Default.palette(dark = false) }
private val LocalTypography = staticCompositionLocalOf<HabitaTypography> { error("No HabitaTheme") }

/**
 * The app's look: light or dark, in the palette of [accent], which tints the page, cards, borders
 * and text as well as the actions. Switching either blends every colour to the new one instead of
 * jumping.
 */
@Composable
fun HabitaTheme(darkTheme: Boolean, accent: Accent = Accent.Default, content: @Composable () -> Unit) {
    val colors = animated(accent.palette(darkTheme))
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

@Composable
private fun animated(target: HabitaColors): HabitaColors {
    @Composable
    fun Color.blend(): Color = animateColorAsState(this, tween(450), label = "theme").value
    return HabitaColors(
        isDark = target.isDark,
        background = target.background.blend(),
        backgroundTop = target.backgroundTop.blend(),
        card = target.card.blend(),
        cardTop = target.cardTop.blend(),
        border = target.border.blend(),
        text = target.text.blend(),
        muted = target.muted.blend(),
        accent = target.accent.blend(),
        accentTint = target.accentTint.blend(),
        onAccent = target.onAccent.blend(),
        highlight = target.highlight.blend(),
        onHighlight = target.onHighlight.blend(),
        inverseCard = target.inverseCard.blend(),
        onInverseCard = target.onInverseCard.blend(),
        error = target.error.blend(),
        effortEasy = target.effortEasy.blend(),
        effortMedium = target.effortMedium.blend(),
        effortHard = target.effortHard.blend(),
    )
}