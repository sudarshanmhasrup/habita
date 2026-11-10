package dev.habita.app.feature.gym.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix

/**
 * The looks a workout photo card can take. [POP] comes first and is the one picked when the card
 * opens; the owner chooses a filter fresh each day.
 *
 * These colours belong to the photo, not to the app's theme: a filter looks the same in light and
 * dark mode, and the card is a picture that leaves the app. That is why they live here and not in
 * `HabitaTheme.colors`.
 */
internal enum class ShareFilter(
    val label: String,
    /** The colour of the date line, the numbers and the wordmark dot on this filter. */
    val highlight: Color,
    /** Fine film noise on top of the photo. */
    val grain: Boolean = false,
    /** A soft bright copy of the photo screened on top, so lights bloom. */
    val glow: Boolean = false,
) {
    POP("Pop", Color(0xFFB6FF6A)),
    ORIGINAL("Original", Color(0xFF6FBF94)),
    GREEN("Green", Color(0xFF9FE0BC)),
    MONO("Mono", Color(0xFFFFFFFF), grain = true),
    FILM("Warm film", Color(0xFFFFC977), grain = true),
    NIGHT("Night", Color(0xFF7FD3FF)),
    MATTE("Matte", Color(0xFFE8EEEA)),
    GLOW("Glow", Color(0xFFFFE7A8), glow = true),
    RED("Red line", Color(0xFFFFD3C2)),
    VIOLET("Violet", Color(0xFFCFC3FF)),
    ;

    /** The colour change applied to the photo, or `null` to leave it as taken. */
    fun matrix(): ColorMatrix? = when (this) {
        POP -> Looks.of(Looks.saturation(1.7f), Looks.contrast(1.22f))
        ORIGINAL -> null
        GREEN -> Looks.of(Looks.duotone(0x0B1A13, 0xCFF3DD), Looks.contrast(1.08f))
        MONO -> Looks.of(Looks.saturation(0f), Looks.contrast(1.3f), Looks.brightness(0.92f))
        FILM -> Looks.of(Looks.sepia(0.38f), Looks.saturation(1.25f), Looks.contrast(1.08f), Looks.brightness(0.97f))
        NIGHT -> Looks.of(
            Looks.saturation(0.75f),
            Looks.brightness(0.82f),
            Looks.contrast(1.15f),
            Looks.tint(0.82f, 0.95f, 1.15f, blueLift = 22f, greenLift = 6f),
        )
        MATTE -> Looks.of(Looks.contrast(0.82f), Looks.saturation(0.7f), Looks.brightness(1.06f), Looks.lift(0.88f, 30f))
        GLOW -> Looks.of(Looks.saturation(1.1f), Looks.brightness(1.02f))
        RED -> Looks.of(Looks.duotone(0x1A0504, 0xFF8E70), Looks.contrast(1.1f))
        VIOLET -> Looks.of(Looks.duotone(0x0A0A1A, 0xC9BBFF), Looks.contrast(1.08f))
    }
}

/** Fixed colours of the card itself, the same under every filter. */
internal object CardInk {
    /** Text on the photo. */
    val text = Color(0xFFFFFFFF)

    /** The quieter line under the wordmark. */
    val soft = Color(0xBFFFFFFF)

    /** The shade behind the text at the top, so it reads on a bright photo. */
    val scrim = Color(0xFF050A08)

    /** The card before a photo is picked. */
    val empty = Color(0xFF161C19)
}

/**
 * Colour matrices in Compose's 4×5 form, offsets in 0–255. [of] chains them in the order given,
 * each one working on the result of the one before.
 */
internal object Looks {
    private const val LUM_R = 0.213f
    private const val LUM_G = 0.715f
    private const val LUM_B = 0.072f

    fun of(vararg steps: FloatArray): ColorMatrix = ColorMatrix(steps.reduce { done, next -> then(done, next) })

    /** 0 is grey, 1 leaves the photo alone, above 1 is more colourful. */
    fun saturation(amount: Float): FloatArray {
        val r = (1 - amount) * LUM_R
        val g = (1 - amount) * LUM_G
        val b = (1 - amount) * LUM_B
        return floatArrayOf(
            r + amount, g, b, 0f, 0f,
            r, g + amount, b, 0f, 0f,
            r, g, b + amount, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Stretches tones away from mid-grey; below 1 flattens them. */
    fun contrast(amount: Float): FloatArray = lift(amount, 128f * (1 - amount))

    fun brightness(amount: Float): FloatArray = lift(amount, 0f)

    /** Every channel times [scale], plus [offset]: a raised offset makes blacks look faded. */
    fun lift(scale: Float, offset: Float): FloatArray = floatArrayOf(
        scale, 0f, 0f, 0f, offset,
        0f, scale, 0f, 0f, offset,
        0f, 0f, scale, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Partway (0 to 1) towards the classic brown sepia. */
    fun sepia(amount: Float): FloatArray {
        val sepia = floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val identity = lift(1f, 0f)
        return FloatArray(20) { identity[it] + (sepia[it] - identity[it]) * amount }
    }

    /** Scales each channel separately, then lifts green and blue, for a cool cast. */
    fun tint(red: Float, green: Float, blue: Float, greenLift: Float = 0f, blueLift: Float = 0f): FloatArray = floatArrayOf(
        red, 0f, 0f, 0f, 0f,
        0f, green, 0f, 0f, greenLift,
        0f, 0f, blue, 0f, blueLift,
        0f, 0f, 0f, 1f, 0f,
    )

    /** The photo's brightness mapped from [dark] (shadows) to [light] (highlights), as 0xRRGGBB. */
    fun duotone(dark: Int, light: Int): FloatArray {
        fun channel(rgb: Int, shift: Int) = ((rgb shr shift) and 0xFF).toFloat()
        val rows = listOf(16, 8, 0).flatMap { shift ->
            val from = channel(dark, shift)
            val span = (channel(light, shift) - from) / 255f
            listOf(0.3f * span, 0.59f * span, 0.11f * span, 0f, from)
        }
        return (rows + listOf(0f, 0f, 0f, 1f, 0f)).toFloatArray()
    }

    /** [first], then [second] on its result. */
    internal fun then(first: FloatArray, second: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var sum = if (col == 4) second[row * 5 + 4] else 0f
                for (k in 0 until 4) sum += second[row * 5 + k] * first[k * 5 + col]
                out[row * 5 + col] = sum
            }
        }
        return out
    }
}