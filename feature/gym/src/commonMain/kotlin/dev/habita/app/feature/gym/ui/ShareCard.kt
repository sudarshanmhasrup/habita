package dev.habita.app.feature.gym.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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
    /** "10 NOV", the first half of [overline]. */
    val date: String,
    /** "LOWER BODY A", the second half of [overline]. */
    val workout: String,
    /** "58 min". */
    val duration: String,
    val sets: Int,
    val bests: Int,
) {
    companion object {
        fun of(report: SessionReport, date: String): ShareCardText {
            val summary = report.summary
            val lifted = summary.volumeKg >= 1
            val sets = summary.setsCompleted
            val bests = summary.personalBests.size
            val day = date.uppercase()
            val workout = report.workoutName.uppercase()
            val duration = formatDuration(summary.totalMillis)
            return ShareCardText(
                overline = "$day · $workout",
                number = if (lifted) formatVolume(summary.volumeKg) else "$sets",
                unit = if (lifted) "KG LIFTED" else if (sets == 1) "SET DONE" else "SETS DONE",
                stats = listOfNotNull(
                    duration,
                    if (sets == 1) "1 set" else "$sets sets",
                    when (bests) {
                        0 -> null
                        1 -> "1 new best"
                        else -> "$bests new bests"
                    },
                ),
                date = day,
                workout = workout,
                duration = duration,
                sets = sets,
                bests = bests,
            )
        }
    }
}

/** The two typefaces of the card: Unbounded for headings and numbers, Manrope for the rest. */
internal class CardFonts(val heading: FontFamily, val body: FontFamily)

/** Card width in pixels for posting: a full-size phone story, 1080 × 1920. */
internal const val CARD_WIDTH = 1080

/**
 * How much of the card's height Instagram covers on a story: the profile bar at the top
 * (about 250 of 1920 pixels) and the reply bar at the bottom (about 340). The photo runs under
 * them, but no words are drawn there.
 */
internal const val SAFE_TOP = 0.13f
internal const val SAFE_BOTTOM = 0.18f

/**
 * Draws the card [width] pixels wide and 16:9 tall: [photo] cropped to fill it, through [filter],
 * with the workout's words laid out the [style] way inside the safe area. [measurer] must use a
 * density of 1, so text sizes here are pixels.
 */
internal fun renderShareCard(
    photo: ImageBitmap?,
    filter: ShareFilter,
    style: ShareStyle,
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
        CardPen(this, measurer, fonts, filter.highlight).draw(style, text)
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