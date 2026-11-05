package dev.habita.app.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every colour the app draws with. [Accent.palette] builds one for each theme colour, light and
 * dark, so the whole app takes on that colour. [accent] is the theme colour itself, used for main
 * actions and live state such as the current set, the rest timer and progress. [highlight] is the
 * colour of main buttons and the chosen chip. The effort colours are one ramp of the theme colour,
 * light to strong as the effort goes up (reversed on dark, where strong means bright), and always
 * come with a text label.
 */
@Immutable
data class HabitaColors(
    val isDark: Boolean,
    val background: Color,
    val backgroundTop: Color,
    val card: Color,
    val cardTop: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentTint: Color,
    val onAccent: Color,
    val highlight: Color,
    val onHighlight: Color,
    val inverseCard: Color,
    val onInverseCard: Color,
    val error: Color,
    val effortEasy: Color,
    val effortMedium: Color,
    val effortHard: Color,
)