package dev.habita.app.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The palette: calm, with one deep green. Light mode is a plain white page with soft warm-grey
 * cards; dark mode is a deep green-black. [accent] is one deep green, used for the main
 * actions and live state such as the current set, the rest timer and progress. [highlight] is the
 * colour of main buttons and the chosen chip. The effort colours are one green ramp, light to
 * strong as the effort goes up (reversed on dark, where strong means bright), and always come
 * with a text label.
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
    card = Color(0xFFF5F4F0),
    cardTop = Color(0xFFF5F4F0),
    border = Color(0xFFE6E4DD),
    text = Color(0xFF15201A),
    muted = Color(0xFF6B766F),
    accent = Color(0xFF2F6F4E),
    accentTint = Color(0xFFDFEDE4),
    onAccent = Color(0xFFFFFFFF),
    highlight = Color(0xFF2F6F4E),
    onHighlight = Color(0xFFFFFFFF),
    inverseCard = Color(0xFF1C2E24),
    onInverseCard = Color(0xFFF3F1EA),
    error = Color(0xFFB42318),
    effortEasy = Color(0xFFC9E2D3),
    effortMedium = Color(0xFF6FAE8A),
    effortHard = Color(0xFF2F6F4E),
)

val DarkColors = HabitaColors(
    isDark = true,
    background = Color(0xFF0E1311),
    backgroundTop = Color(0xFF141C18),
    card = Color(0xFF161C19),
    cardTop = Color(0xFF1B231F),
    border = Color(0xFF26302B),
    text = Color(0xFFE8EEEA),
    muted = Color(0xFF8E9A93),
    accent = Color(0xFF6FBF94),
    accentTint = Color(0xFF1E3329),
    onAccent = Color(0xFF0E1311),
    highlight = Color(0xFF6FBF94),
    onHighlight = Color(0xFF0E1311),
    inverseCard = Color(0xFFE8EEEA),
    onInverseCard = Color(0xFF0E1311),
    error = Color(0xFFF97066),
    effortEasy = Color(0xFF244232),
    effortMedium = Color(0xFF3F8A63),
    effortHard = Color(0xFF8FD3AE),
)