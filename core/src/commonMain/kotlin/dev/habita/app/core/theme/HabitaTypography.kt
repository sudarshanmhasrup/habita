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

/** Headings use Unbounded 600, everything else Manrope. */
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
        display = TextStyle(fontFamily = unbounded, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),
        headline = TextStyle(fontFamily = unbounded, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
        timer = TextStyle(fontFamily = unbounded, fontWeight = FontWeight.SemiBold, fontSize = 64.sp, lineHeight = 72.sp),
        title = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 26.sp),
        body = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyStrong = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
        label = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        caption = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
        button = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
        number = TextStyle(fontFamily = unbounded, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    )
}