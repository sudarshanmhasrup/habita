package dev.habita.app.feature.gym.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import dev.habita.app.feature.gym.data.SessionReport
import kotlin.math.roundToInt
import kotlin.random.Random

/** The words on a workout photo card. */
internal data class ShareCardText(
    /** "10 NOV · LOWER BODY A". */
    val overline: String,
    /** The big number: kilograms lifted, or sets on a day with no weights. */
    val number: String,
    /** What [number] counts, under it. */
    val unit: String,
    /** "58 min", "17 sets", "1 new best", drawn with dots between: the digits are drawn in the filter's highlight. */
    val stats: List<String>,
) {
    companion object {
        fun of(report: SessionReport, date: String): ShareCardText {
            val summary = report.summary
            val lifted = summary.volumeKg >= 1
            val sets = summary.setsCompleted
            val bests = summary.personalBests.size
            return ShareCardText(
                overline = "${date.uppercase()} · ${report.workoutName.uppercase()}",
                number = if (lifted) formatVolume(summary.volumeKg) else "$sets",
                unit = if (lifted) "KG LIFTED" else if (sets == 1) "SET DONE" else "SETS DONE",
                stats = listOfNotNull(
                    formatDuration(summary.totalMillis),
                    if (sets == 1) "1 set" else "$sets sets",
                    when (bests) {
                        0 -> null
                        1 -> "1 new best"
                        else -> "$bests new bests"
                    },
                ),
            )
        }
    }
}

/** The two typefaces of the card: Unbounded for headings and numbers, Manrope for the rest. */
internal class CardFonts(val heading: FontFamily, val body: FontFamily)

/** Card width in pixels for posting: a full-size phone story, 1080 × 1920. */
internal const val CARD_WIDTH = 1080

/**
 * Draws the card [width] pixels wide and 16:9 tall: [photo] cropped to fill it, through [filter],
 * with the workout's numbers across the top and the wordmark bottom-left. [measurer] must use a
 * density of 1, so text sizes here are pixels.
 */
internal fun renderShareCard(
    photo: ImageBitmap?,
    filter: ShareFilter,
    text: ShareCardText,
    measurer: TextMeasurer,
    fonts: CardFonts,
    width: Int = CARD_WIDTH,
): ImageBitmap {
    val height = width * 16 / 9
    val bitmap = ImageBitmap(width, height)
    val size = Size(width.toFloat(), height.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
        drawPhoto(photo, filter)
        drawText(text, filter, measurer, fonts)
    }
    return bitmap
}

private fun DrawScope.drawPhoto(photo: ImageBitmap?, filter: ShareFilter) {
    if (photo == null) {
        drawRect(CardInk.empty)
        return
    }
    // Centre crop: the part of the photo with the card's shape, as large as fits.
    val cardRatio = size.width / size.height
    val photoRatio = photo.width.toFloat() / photo.height
    val srcSize = if (photoRatio > cardRatio) {
        IntSize((photo.height * cardRatio).roundToInt(), photo.height)
    } else {
        IntSize(photo.width, (photo.width / cardRatio).roundToInt())
    }
    val srcOffset = IntOffset((photo.width - srcSize.width) / 2, (photo.height - srcSize.height) / 2)
    val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
    drawImage(
        photo,
        srcOffset,
        srcSize,
        dstSize = dstSize,
        colorFilter = filter.matrix()?.let { ColorFilter.colorMatrix(it) },
        filterQuality = FilterQuality.High,
    )
    if (filter.glow) {
        drawImage(
            photo,
            srcOffset,
            srcSize,
            dstSize = dstSize,
            alpha = 0.5f,
            colorFilter = ColorFilter.colorMatrix(Looks.of(Looks.saturation(0.6f), Looks.lift(1.5f, 18f))),
            blendMode = BlendMode.Screen,
            filterQuality = FilterQuality.Low,
        )
    }
    if (filter.grain) drawGrain()
}

/** Light and dark specks over the photo. A fixed seed keeps the grain the same on every redraw. */
private fun DrawScope.drawGrain() {
    val random = Random(7)
    val speck = size.width / 400f
    val count = (size.width * size.height / 140f).toInt()
    fun specks() = List(count / 2) { Offset(random.nextFloat() * size.width, random.nextFloat() * size.height) }
    drawPoints(specks(), PointMode.Points, CardInk.text.copy(alpha = 0.10f), strokeWidth = speck)
    drawPoints(specks(), PointMode.Points, CardInk.scrim.copy(alpha = 0.14f), strokeWidth = speck)
}

private fun DrawScope.drawText(text: ShareCardText, filter: ShareFilter, measurer: TextMeasurer, fonts: CardFonts) {
    // One hundredth of the card width, so the layout is the same at any size.
    val u = size.width / 100f
    val accent = filter.highlight

    drawRect(
        Brush.verticalGradient(
            0f to CardInk.scrim.copy(alpha = 0.82f),
            0.55f to CardInk.scrim.copy(alpha = 0.55f),
            1f to CardInk.scrim.copy(alpha = 0f),
            endY = size.height * 0.42f,
        ),
        size = Size(size.width, size.height * 0.42f),
    )

    fun style(family: FontFamily, sizeU: Float, weight: FontWeight, spacingU: Float = 0f) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = (sizeU * u).sp,
        letterSpacing = (spacingU * u).sp,
        color = CardInk.text,
    )

    val left = 6 * u
    var y = 7 * u
    fun line(value: AnnotatedString, style: TextStyle, gapAfterU: Float) {
        val layout = measurer.measure(value, style, softWrap = false, maxLines = 1)
        drawText(layout, topLeft = Offset(left, y))
        y += layout.size.height + gapAfterU * u
    }

    line(AnnotatedString(text.overline), style(fonts.heading, 3.4f, FontWeight.SemiBold, 0.35f).copy(color = accent), 0.6f)
    line(AnnotatedString(text.number), style(fonts.heading, 19f, FontWeight.SemiBold, -0.5f), -1.2f)
    line(AnnotatedString(text.unit), style(fonts.heading, 4.6f, FontWeight.SemiBold, 0.45f), 2.6f)
    val stats = buildAnnotatedString {
        text.stats.forEachIndexed { index, stat ->
            if (index > 0) append("  ·  ")
            withDigits(stat, accent)
        }
    }
    line(stats, style(fonts.body, 3.8f, FontWeight.Bold), 0f)

    // The wordmark, bottom-left: "habita." with the dot in the highlight, and the maker under it.
    val credit = measurer.measure("by Sudarshan", style(fonts.body, 3f, FontWeight.SemiBold, 0.1f).copy(color = CardInk.soft))
    val creditTop = size.height - 5 * u - credit.size.height
    val mark = measurer.measure(
        buildAnnotatedString {
            append("habita")
            withStyle(SpanStyle(color = accent)) { append(".") }
        },
        style(fonts.heading, 4.6f, FontWeight.SemiBold, 0.08f),
    )
    drawText(mark, topLeft = Offset(5 * u, creditTop - mark.size.height + 0.4f * u))
    drawText(credit, topLeft = Offset(5 * u, creditTop))
}

/** Appends [value] with every run of digits in [color]. */
private fun AnnotatedString.Builder.withDigits(value: String, color: Color) {
    var start = 0
    Regex("""\d[\d,.]*""").findAll(value).forEach { match ->
        append(value.substring(start, match.range.first))
        withStyle(SpanStyle(color = color)) { append(match.value) }
        start = match.range.last + 1
    }
    append(value.substring(start))
}