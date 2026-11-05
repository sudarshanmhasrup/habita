package dev.habita.app.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The theme colour. Each one is a whole palette, not just a button colour: the page, cards,
 * borders and text all take a quiet tint of its hue, so the app feels made of that colour. The
 * tones are deep and muted on purpose; bright, flat colours look cheap. [id] is what gets saved
 * and synced, so never rename one.
 */
enum class Accent(
    val id: String,
    val label: String,
    /** The colour of main actions on a light page. */
    private val light: Color,
    /** The same hue, lifted to read on a dark page. */
    private val dark: Color,
) {
    FOREST("forest", "Forest", Color(0xFF2E5E4A), Color(0xFF8CC7A7)),
    OCEAN("ocean", "Navy", Color(0xFF23466E), Color(0xFF93B6E2)),
    TEAL("teal", "Deep teal", Color(0xFF1D5C60), Color(0xFF80C3C1)),
    PLUM("plum", "Aubergine", Color(0xFF5B3960), Color(0xFFCBA7D3)),
    TERRACOTTA("terracotta", "Clay", Color(0xFF94472F), Color(0xFFE2A68C)),
    GRAPHITE("graphite", "Graphite", Color(0xFF2A2C30), Color(0xFFD8D9DC)),
    ;

    /** The swatch shown in the picker. */
    fun swatch(dark: Boolean): Color = if (dark) this.dark else light

    /** The whole palette for a light or dark page, built from this colour's hue. */
    fun palette(dark: Boolean): HabitaColors {
        val (hue, saturation) = hueAndSaturation(light)
        // How much of the hue goes into the neutrals: enough to feel tinted, never coloured.
        fun tone(chroma: Float, lightness: Float) = Color.hsl(hue, (saturation * chroma).coerceIn(0f, 1f), lightness)
        return if (!dark) {
            val accent = light
            val tint = tone(0.45f, 0.91f)
            HabitaColors(
                isDark = false,
                background = tone(0.22f, 0.975f),
                backgroundTop = tone(0.30f, 0.955f),
                card = tone(0.26f, 0.935f),
                cardTop = tone(0.26f, 0.935f),
                border = tone(0.20f, 0.865f),
                text = tone(0.40f, 0.12f),
                muted = tone(0.14f, 0.42f),
                accent = accent,
                accentTint = tint,
                onAccent = Color.White,
                highlight = accent,
                onHighlight = Color.White,
                inverseCard = tone(0.40f, 0.15f),
                onInverseCard = tone(0.22f, 0.975f),
                error = Color(0xFFB42318),
                effortEasy = lerp(tint, accent, 0.3f),
                effortMedium = lerp(tint, accent, 0.65f),
                effortHard = accent,
            )
        } else {
            val accent = this.dark
            val background = tone(0.30f, 0.065f)
            val tint = tone(0.35f, 0.17f)
            HabitaColors(
                isDark = true,
                background = background,
                backgroundTop = tone(0.32f, 0.095f),
                card = tone(0.26f, 0.11f),
                cardTop = tone(0.26f, 0.135f),
                border = tone(0.20f, 0.19f),
                text = tone(0.16f, 0.92f),
                muted = tone(0.10f, 0.62f),
                accent = accent,
                accentTint = tint,
                onAccent = background,
                highlight = accent,
                onHighlight = background,
                inverseCard = tone(0.16f, 0.92f),
                onInverseCard = background,
                error = Color(0xFFF97066),
                effortEasy = lerp(tint, accent, 0.3f),
                effortMedium = lerp(tint, accent, 0.65f),
                effortHard = accent,
            )
        }
    }

    companion object {
        val Default = FOREST

        /** The accent saved as [id], or the default for an unknown or missing one. */
        fun fromId(id: String?): Accent = entries.firstOrNull { it.id == id } ?: Default
    }
}

/** The hue (0 to 360) and HSL saturation (0 to 1) of [color]. */
private fun hueAndSaturation(color: Color): Pair<Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val high = max(r, max(g, b))
    val low = min(r, min(g, b))
    val delta = high - low
    if (delta == 0f) return 0f to 0f
    val lightness = (high + low) / 2
    val saturation = delta / (1 - abs(2 * lightness - 1))
    val hue = when (high) {
        r -> 60 * (((g - b) / delta) % 6)
        g -> 60 * ((b - r) / delta + 2)
        else -> 60 * ((r - g) / delta + 4)
    }
    return (if (hue < 0) hue + 360 else hue) to saturation.coerceIn(0f, 1f)
}