package dev.habita.app.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The palette. Light mode is plain white with light grey cards; dark mode is graphite. Main
 * buttons, the chosen chip and the full-body card use [highlight]: black on light, white on dark.
 * [accent] is one blue, kept for live state such as the current set, the rest timer and progress.
 * The effort colors are one blue ramp, light to strong as the effort goes up (reversed on dark,
 * where strong means bright), and always come with a text label.
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

val LightColors = HabitaColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    backgroundTop = Color(0xFFFFFFFF),
    card = Color(0xFFF4F4F5),
    cardTop = Color(0xFFF4F4F5),
    border = Color(0xFFE4E4E7),
    text = Color(0xFF111113),
    muted = Color(0xFF6B6B73),
    accent = Color(0xFF1D5FD6),
    accentTint = Color(0xFFE8F0FE),
    onAccent = Color(0xFFFFFFFF),
    highlight = Color(0xFF111113),
    onHighlight = Color(0xFFFFFFFF),
    inverseCard = Color(0xFF111113),
    onInverseCard = Color(0xFFFFFFFF),
    error = Color(0xFFB42318),
    effortEasy = Color(0xFFB9D0FB),
    effortMedium = Color(0xFF5B8DEF),
    effortHard = Color(0xFF1D4FB8),
)

val DarkColors = HabitaColors(
    isDark = true,
    background = Color(0xFF0C0C0D),
    backgroundTop = Color(0xFF19191B),
    card = Color(0xFF161618),
    cardTop = Color(0xFF212124),
    border = Color(0xFF2A2A2E),
    text = Color(0xFFF4F4F5),
    muted = Color(0xFF8E8E96),
    accent = Color(0xFF6EA2FF),
    accentTint = Color(0xFF16233C),
    onAccent = Color(0xFF0C0C0D),
    highlight = Color(0xFFF4F4F5),
    onHighlight = Color(0xFF0C0C0D),
    inverseCard = Color(0xFFF4F4F5),
    onInverseCard = Color(0xFF0C0C0D),
    error = Color(0xFFF97066),
    effortEasy = Color(0xFF213F75),
    effortMedium = Color(0xFF4479E0),
    effortHard = Color(0xFF9BBFFF),
)