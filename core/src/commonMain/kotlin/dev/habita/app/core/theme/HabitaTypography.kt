package dev.habita.app.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.habita.app.core.resources.Res
import dev.habita.app.core.resources.manrope_bold
import dev.habita.app.core.resources.manrope_medium
import dev.habita.app.core.resources.manrope_regular
import dev.habita.app.core.resources.manrope_semibold
import dev.habita.app.core.resources.unbounded_semibold
import org.jetbrains.compose.resources.Font

/**
 * Manrope throughout, with tabular figures on numbers so they don't jump as they change.
 * Unbounded 600 is kept for the wordmark only ([wordmark]).
 */
@Immutable
data class HabitaTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val timer: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val button: TextStyle,
    val number: TextStyle,
    val overline: TextStyle,
    val stat: TextStyle,
    val wordmark: TextStyle,
)

@Composable
internal fun habitaTypography(): HabitaTypography {
    val unbounded = FontFamily(Font(Res.font.unbounded_semibold, FontWeight.SemiBold))
    val manrope = FontFamily(
        Font(Res.font.manrope_regular, FontWeight.Normal),
        Font(Res.font.manrope_medium, FontWeight.Medium),
        Font(Res.font.manrope_semibold, FontWeight.SemiBold),
        Font(Res.font.manrope_bold, FontWeight.Bold),
    )
    return HabitaTypography(
        display = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
        headline = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
        timer = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 68.sp, lineHeight = 76.sp, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
        title = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
        body = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyStrong = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
        label = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        caption = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
        button = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        number = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, fontFeatureSettings = "tnum"),
        overline = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
        stat = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp, fontFeatureSettings = "tnum"),
        wordmark = TextStyle(fontFamily = unbounded, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 48.sp),
    )
}