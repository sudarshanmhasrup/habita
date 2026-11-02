package dev.habita.app.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The palette. [inverseCard] is the dark card used for the full-body workout in light mode, and
 * flips to a light card in dark mode.
 */
@Immutable
data class HabitaColors(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentTint: Color,
    val onAccent: Color,
    val inverseCard: Color,
    val onInverseCard: Color,
    val error: Color,
)

val LightColors = HabitaColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    border = Color(0xFFE3E7E1),
    text = Color(0xFF101613),
    muted = Color(0xFF6B766F),
    accent = Color(0xFF2F6F4E),
    accentTint = Color(0xFFDFEDE4),
    onAccent = Color(0xFFFFFFFF),
    inverseCard = Color(0xFF101613),
    onInverseCard = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
)

val DarkColors = HabitaColors(
    isDark = true,
    background = Color(0xFF0E1311),
    card = Color(0xFF161C19),
    border = Color(0xFF26302B),
    text = Color(0xFFE8EEEA),
    muted = Color(0xFF8E9A93),
    accent = Color(0xFF6FBF94),
    accentTint = Color(0xFF1E3329),
    onAccent = Color(0xFF0E1311),
    inverseCard = Color(0xFFE8EEEA),
    onInverseCard = Color(0xFF0E1311),
    error = Color(0xFFF2B8B5),
)