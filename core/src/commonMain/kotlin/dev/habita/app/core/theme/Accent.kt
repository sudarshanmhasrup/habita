package dev.habita.app.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The colour the app is tinted with: main buttons, the chosen chip, progress, the timer and the
 * effort ramp. Each has a light and a dark version, chosen to read well on that page. [id] is
 * what gets saved and synced, so never rename one.
 */
enum class Accent(
    val id: String,
    val label: String,
    private val light: Color,
    private val lightTint: Color,
    private val dark: Color,
    private val darkTint: Color,
) {
    FOREST("forest", "Forest", Color(0xFF2F6F4E), Color(0xFFDFEDE4), Color(0xFF6FBF94), Color(0xFF1E3329)),
    OCEAN("ocean", "Ocean", Color(0xFF1D5FD6), Color(0xFFE3ECFC), Color(0xFF6EA2FF), Color(0xFF16233C)),
    TEAL("teal", "Teal", Color(0xFF0F766E), Color(0xFFDDF1EE), Color(0xFF5EC9BD), Color(0xFF12302C)),
    PLUM("plum", "Plum", Color(0xFF7A3E9D), Color(0xFFEFE4F5), Color(0xFFC79BE6), Color(0xFF2B1E36)),
    TERRACOTTA("terracotta", "Terracotta", Color(0xFFB4532A), Color(0xFFF6E6DC), Color(0xFFF0A07A), Color(0xFF3A2219)),
    GRAPHITE("graphite", "Graphite", Color(0xFF18181B), Color(0xFFEDEDEF), Color(0xFFE4E4E7), Color(0xFF26262A)),
    ;

    /** The swatch shown in the picker. */
    fun swatch(dark: Boolean): Color = if (dark) this.dark else light

    /** [base] with this accent applied. */
    fun applyTo(base: HabitaColors): HabitaColors {
        val accent = if (base.isDark) dark else light
        val tint = if (base.isDark) darkTint else lightTint
        return base.copy(
            accent = accent,
            accentTint = tint,
            highlight = accent,
            onHighlight = if (base.isDark) base.background else Color.White,
            onAccent = if (base.isDark) base.background else Color.White,
            // One ramp from the tint to the accent: light to strong as the effort goes up.
            effortEasy = lerp(tint, accent, if (base.isDark) 0.25f else 0.3f),
            effortMedium = lerp(tint, accent, 0.65f),
            effortHard = accent,
        )
    }

    companion object {
        val Default = FOREST

        /** The accent saved as [id], or the default for an unknown or missing one. */
        fun fromId(id: String?): Accent = entries.firstOrNull { it.id == id } ?: Default
    }
}