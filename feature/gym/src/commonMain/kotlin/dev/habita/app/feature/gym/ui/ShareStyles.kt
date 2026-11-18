package dev.habita.app.feature.gym.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * The layouts a workout photo card can take. [CLASSIC] comes first and is the one picked when the
 * card opens. Every style keeps its words between [SAFE_TOP] and [SAFE_BOTTOM].
 */
internal enum class ShareStyle(val label: String) {
    CLASSIC("Classic"),
    LOWER("Lower third"),
    CENTRE("Centre"),
    SPINE("Spine"),
    RECEIPT("Receipt"),
    PANEL("Panel"),
    BLOCK("Block"),
    SCORE("Scoreboard"),
    OUTLINE("Outline"),
    SEAL("Seal"),
    COVER("Cover"),
}

private enum class Align { START, CENTER, END }

/** One line of a stacked column of text, and the space under it in card units. */
private class Line(val text: TextLayoutResult, val gapU: Float = 0f)

/**
 * Draws the words of a card. Sizes are in card units: one hundredth of the card width, so the
 * layout is the same at any size.
 */
internal class CardPen(
    private val scope: DrawScope,
    private val measurer: TextMeasurer,
    private val fonts: CardFonts,
    private val accent: Color,
) {
    private val w = scope.size.width
    private val h = scope.size.height
    private val u = w / 100f
    private val safeTop = h * SAFE_TOP
    private val safeBottom = h * (1 - SAFE_BOTTOM)
    private val ink = CardInk.scrim
    private val white = CardInk.text

    fun draw(style: ShareStyle, text: ShareCardText) = when (style) {
        ShareStyle.CLASSIC -> classic(text)
        ShareStyle.LOWER -> lower(text)
        ShareStyle.CENTRE -> centre(text)
        ShareStyle.SPINE -> spine(text)
        ShareStyle.RECEIPT -> receipt(text)
        ShareStyle.PANEL -> panel(text)
        ShareStyle.BLOCK -> block(text)
        ShareStyle.SCORE -> score(text)
        ShareStyle.OUTLINE -> outline(text)
        ShareStyle.SEAL -> seal(text)
        ShareStyle.COVER -> cover(text)
    }

    // ---- the styles ----

    /** Numbers across the top, wordmark bottom-left: the original card. */
    private fun classic(t: ShareCardText) {
        shade(0f, h * 0.55f, 0f to 0.82f, 0.55f to 0.55f, 1f to 0f)
        shade(h * 0.62f, h, 0f to 0f, 1f to 0.6f)
        column(mainLines(t), 6 * u, safeTop + 2 * u)
        signature(5 * u, safeBottom - 2 * u, Align.START)
    }

    /** Numbers just above the reply bar, wordmark at the top: the face in the photo stays clear. */
    private fun lower(t: ShareCardText) {
        shade(0f, h * 0.3f, 0f to 0.5f, 1f to 0f)
        shade(h * 0.4f, h, 0f to 0f, 0.45f to 0.85f, 1f to 0.85f)
        draw(mark(4.6f), 6 * u, safeTop + 3 * u)
        val lines = mainLines(t, statsGap = 1.2f) + Line(credit())
        column(lines, 6 * u, safeBottom - 4 * u - height(lines))
    }

    /** One huge number in the middle, the safest spot on a story. */
    private fun centre(t: ShareCardText) {
        scope.drawRect(
            Brush.radialGradient(
                0f to ink.copy(alpha = 0.75f),
                1f to ink.copy(alpha = 0.2f),
                center = Offset(w / 2, h * 0.48f),
                radius = w * 0.9f,
            ),
        )
        val lines = listOf(
            Line(head(t.overline, 3.4f, accent, 0.35f, maxU = 88f), 1.5f),
            Line(head(t.number, 24f, spacingU = -0.6f, maxU = 88f), -1.5f),
            Line(head(t.unit, 4.6f, spacingU = 0.45f), 2.4f),
            Line(body(stats(t.stats), 3.8f)),
        )
        column(lines, 0f, (safeTop + safeBottom - height(lines)) / 2 - 4 * u, Align.CENTER, w)
        signature(0f, safeBottom - 3 * u, Align.CENTER, w)
    }

    /** The number runs up the left edge like a book spine, the rest stacks beside it. */
    private fun spine(t: ShareCardText) {
        scope.drawRect(
            Brush.horizontalGradient(0f to ink.copy(alpha = 0.9f), 1f to ink.copy(alpha = 0f), endX = w * 0.4f),
            size = Size(w * 0.4f, h),
        )
        shade(0f, h * 0.5f, 0f to 0.5f, 1f to 0f)
        val number = head(t.number, 22f, spacingU = -0.6f, maxU = (safeBottom - safeTop) / u - 6f)
        val cx = 4 * u + number.size.height / 2f
        val cy = (safeTop + safeBottom) / 2
        scope.rotate(-90f, Offset(cx, cy)) {
            drawText(number, topLeft = Offset(cx - number.size.width / 2f, cy - number.size.height / 2f))
        }
        val x = 4 * u + number.size.height + 3 * u
        val maxU = (w - x) / u - 5f
        val unitWords = t.unit.split(" ")
        val lines = buildList {
            add(Line(head(t.date, 3.4f, accent, 0.35f, maxU = maxU), 0.4f))
            add(Line(head(t.workout, 3.4f, accent, 0.35f, maxU = maxU), 3f))
            unitWords.forEachIndexed { i, word ->
                add(Line(head(word, 5.2f, spacingU = 0.45f, maxU = maxU), if (i == unitWords.lastIndex) 3f else 0.2f))
            }
            t.stats.forEach { add(Line(body(stats(listOf(it)), 4.2f), 0.8f)) }
        }
        column(lines, x, safeTop + 3 * u)
        signature(0f, safeBottom - 3 * u, Align.END, w - 5 * u)
    }

    /** A torn paper slip over the photo, itemised like a bill. */
    private fun receipt(t: ShareCardText) {
        scope.drawRect(ink.copy(alpha = 0.2f))
        val left = 12 * u
        val paperW = 76 * u
        val pad = 6 * u
        val innerW = paperW - 2 * pad
        val faint = ink.copy(alpha = 0.6f)

        val header = mono(t.date, 3.3f) to mono(t.workout, 3.3f)
        val number = head(t.number, 14f, ink, -0.4f, maxU = innerW / u)
        val unit = mono(t.unit, 3.3f)
        val rows = listOf(
            mono("TIME", 3.3f) to mono(t.duration, 3.3f),
            mono("SETS", 3.3f) to mono("${t.sets}", 3.3f),
            mono(if (t.bests == 1) "NEW BEST" else "NEW BESTS", 3.3f) to mono("${t.bests}", 3.3f),
        )
        val mark = mark(4f, ink, ink)
        val credit = mono("by Sudarshan", 3f, faint)
        val rowH = rows.first().first.size.height + 2.6f * u
        val bite = 2.2f * u
        val paperH = 3 * u + pad + rowH + 1 * u + number.size.height + unit.size.height + 2 * u +
            rowH * rows.size + 3 * u + mark.size.height + 0.6f * u + credit.size.height + pad + bite
        val top = (safeTop + safeBottom - paperH) / 2
        val centre = Offset(w / 2, top + paperH / 2)

        scope.rotate(-2f, centre) {
            val paper = Path().apply { addRect(Rect(left, top, left + paperW, top + paperH)) }
            val bites = Path().apply {
                var x = left + 3 * u
                while (x < left + paperW) {
                    addOval(Rect(Offset(x, top + paperH), bite))
                    x += 6 * u
                }
            }
            drawPath(Path.combine(PathOperation.Difference, paper, bites), CardInk.paper)
            drawRect(accent, Offset(left, top), Size(paperW, 3 * u))

            var y = top + 3 * u + pad
            fun row(pair: Pair<TextLayoutResult, TextLayoutResult>) {
                drawText(pair.first, topLeft = Offset(left + pad, y))
                drawText(pair.second, topLeft = Offset(left + pad + innerW - pair.second.size.width, y))
                val lineY = y + pair.first.size.height + 1.2f * u
                drawLine(
                    faint,
                    Offset(left + pad, lineY),
                    Offset(left + pad + innerW, lineY),
                    strokeWidth = 0.3f * u,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2f * u, u)),
                )
                y += rowH
            }
            row(header)
            y += 1 * u
            drawText(number, topLeft = Offset(left + pad, y))
            y += number.size.height
            drawText(unit, topLeft = Offset(left + pad, y))
            y += unit.size.height + 2 * u
            rows.forEach(::row)
            y += 3 * u
            drawText(mark, topLeft = Offset(left + (paperW - mark.size.width) / 2, y))
            y += mark.size.height + 0.6f * u
            drawText(credit, topLeft = Offset(left + (paperW - credit.size.width) / 2, y))
        }
    }

    /** A dark glass sheet at the bottom keeps the words readable on any photo. */
    private fun panel(t: ShareCardText) {
        shade(0f, h * 0.3f, 0f to 0.5f, 1f to 0f)
        column(listOf(Line(mark(4.6f), 0.4f), Line(credit())), 6 * u, safeTop + 3 * u)

        val left = 6 * u
        val panelW = 88 * u
        val pad = 6 * u
        val lines = listOf(
            Line(head(t.overline, 3.4f, accent, 0.35f, maxU = (panelW - 2 * pad) / u), 1.2f),
            Line(head(t.number, 19f, spacingU = -0.5f, maxU = (panelW - 2 * pad) / u), -1.2f),
            Line(head(t.unit, 4.6f, spacingU = 0.45f)),
        )
        val colW = (panelW - 2 * pad) / 3
        val cols = listOf(
            t.duration to "time",
            "${t.sets}" to if (t.sets == 1) "set" else "sets",
            "${t.bests}" to if (t.bests == 1) "new best" else "new bests",
        ).map { (value, label) ->
            head(value, 5f, accent, maxU = colW / u - 2f) to body(label, 2.8f, CardInk.soft)
        }
        val colH = cols.maxOf { it.first.size.height + it.second.size.height }.toFloat()
        val panelH = pad + height(lines) + 3 * u + 3 * u + colH + pad
        val top = safeBottom - 4 * u - panelH

        scope.drawRoundRect(ink.copy(alpha = 0.55f), Offset(left, top), Size(panelW, panelH), CornerRadius(6 * u))
        scope.drawRoundRect(
            white.copy(alpha = 0.18f),
            Offset(left, top),
            Size(panelW, panelH),
            CornerRadius(6 * u),
            style = Stroke(0.3f * u),
        )
        val end = column(lines, left + pad, top + pad)
        val dividerY = end + 3 * u
        scope.drawLine(white.copy(alpha = 0.15f), Offset(left + pad, dividerY), Offset(left + panelW - pad, dividerY), 0.3f * u)
        cols.forEachIndexed { i, (value, label) ->
            val x = left + pad + i * colW
            draw(value, x, dividerY + 3 * u)
            draw(label, x, dividerY + 3 * u + value.size.height)
        }
    }

    /** Photo on top, a solid slab in the filter's colour below with the numbers in dark ink. */
    private fun block(t: ShareCardText) {
        shade(0f, h * 0.3f, 0f to 0.5f, 1f to 0f)
        column(listOf(Line(mark(4.6f), 0.4f), Line(credit())), 6 * u, safeTop + 3 * u)
        val blockTop = h * 0.52f
        scope.drawRect(accent, Offset(0f, blockTop), Size(w, h - blockTop))
        val lines = listOf(
            Line(head(t.overline, 3.4f, ink.copy(alpha = 0.7f), 0.35f, maxU = 88f), 1f),
            Line(head(t.number, 19f, ink, -0.5f, maxU = 88f), -1.2f),
            Line(head(t.unit, 4.6f, ink, 0.45f), 2f),
            Line(body(stats(t.stats, ink), 3.8f, ink)),
        )
        column(lines, 6 * u, blockTop + 5 * u)
    }

    /** Every stat in its own tile, like a match result. */
    private fun score(t: ShareCardText) {
        shade(0f, h * 0.4f, 0f to 0.6f, 1f to 0f)
        shade(h * 0.45f, h, 0f to 0f, 1f to 0.85f)
        val left = 5 * u
        val right = w - 5 * u
        val mark = mark(4.6f)
        draw(mark, left, safeTop + 3 * u)
        draw(credit(), left, safeTop + 3 * u + mark.size.height + 0.4f * u)
        val overline = head(t.overline, 2.8f, accent, 0.3f, maxU = 55f)
        draw(overline, right - overline.size.width, safeTop + 3 * u + (mark.size.height - overline.size.height) / 2f)

        val pad = 3.4f * u
        val gap = 2 * u
        val fullW = right - left
        val halfW = (fullW - gap) / 2
        fun label(value: String) = body(value, 2.6f, CardInk.soft, 0.3f)
        val lifted = label(t.unit) to head(t.number, 15f, accent, maxU = (fullW - 2 * pad) / u)
        val time = label("TIME") to head(t.duration, 7f, maxU = (halfW - 2 * pad) / u)
        val sets = label("SETS") to head("${t.sets}", 7f, maxU = (halfW - 2 * pad) / u)
        val bests = label(if (t.bests == 1) "NEW BEST" else "NEW BESTS") to head("${t.bests}", 7f, accent)
        fun tileH(tile: Pair<TextLayoutResult, TextLayoutResult>) =
            2 * pad + tile.first.size.height + 0.4f * u + tile.second.size.height
        val rowH = maxOf(tileH(time), tileH(sets))
        val bestsH = 2 * pad + maxOf(bests.first.size.height, bests.second.size.height)
        var y = safeBottom - 4 * u - (tileH(lifted) + gap + rowH + gap + bestsH)

        fun frame(x: Float, top: Float, width: Float, height: Float, color: Color) = scope.drawRoundRect(
            color,
            Offset(x, top),
            Size(width, height),
            CornerRadius(3 * u),
            style = Stroke(0.35f * u),
        )
        fun stacked(tile: Pair<TextLayoutResult, TextLayoutResult>, x: Float, top: Float) {
            draw(tile.first, x + pad, top + pad)
            draw(tile.second, x + pad, top + pad + tile.first.size.height + 0.4f * u)
        }
        frame(left, y, fullW, tileH(lifted), accent)
        stacked(lifted, left, y)
        y += tileH(lifted) + gap
        frame(left, y, halfW, rowH, white.copy(alpha = 0.35f))
        stacked(time, left, y)
        frame(left + halfW + gap, y, halfW, rowH, white.copy(alpha = 0.35f))
        stacked(sets, left + halfW + gap, y)
        y += rowH + gap
        frame(left, y, fullW, bestsH, white.copy(alpha = 0.35f))
        draw(bests.first, left + pad, y + (bestsH - bests.first.size.height) / 2)
        draw(bests.second, right - pad - bests.second.size.width, y + (bestsH - bests.second.size.height) / 2)
    }

    /** The number as a huge stroked shape across the photo; the person shows through it. */
    private fun outline(t: ShareCardText) {
        scope.drawRect(ink.copy(alpha = 0.35f))
        val ghost = head(t.number, 60f, accent, -2f, maxU = 96f, strokeU = 0.5f)
        draw(ghost, (w - ghost.size.width) / 2, (safeTop + safeBottom - ghost.size.height) / 2)
        column(
            listOf(
                Line(head(t.overline, 3.4f, accent, 0.35f, maxU = 88f), 1f),
                Line(head("${t.number} ${t.unit}", 4.6f, spacingU = 0.45f, maxU = 88f)),
            ),
            6 * u,
            safeTop + 3 * u,
        )
        val lines = listOf(Line(body(stats(t.stats), 3.8f), 3f), Line(mark(4.6f), 0.4f), Line(credit()))
        column(lines, 6 * u, safeBottom - 4 * u - height(lines))
    }

    /** A round stamp in the filter's colour, like a medal. */
    private fun seal(t: ShareCardText) {
        scope.drawRect(ink.copy(alpha = 0.25f))
        val centre = Offset(w / 2, h * 0.47f)
        val radius = 35 * u
        scope.drawCircle(accent.copy(alpha = 0.25f), radius + 2 * u, centre)
        scope.drawCircle(accent, radius, centre)
        scope.drawCircle(
            ink.copy(alpha = 0.5f),
            radius - 3 * u,
            centre,
            style = Stroke(0.4f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.4f * u, 1.2f * u))),
        )
        val inside = listOf(
            Line(head(t.overline, 3f, ink.copy(alpha = 0.7f), 0.3f, maxU = 52f), 1f),
            Line(head(t.number, 16f, ink, -0.5f, maxU = 54f), -1f),
            Line(head(t.unit, 4.6f, ink, 0.45f, maxU = 52f)),
        )
        scope.rotate(-8f, centre) {
            column(inside, centre.x - radius, centre.y - height(inside) / 2, Align.CENTER, 2 * radius)
        }
        val below = listOf(Line(body(stats(t.stats), 3.8f), 2f), Line(mark(4.6f), 0.4f), Line(credit()))
        column(below, 0f, safeBottom - 4 * u - height(below), Align.CENTER, w)
    }

    /** The wordmark as a magazine masthead, the new best as a cover sticker. */
    private fun cover(t: ShareCardText) {
        shade(0f, h * 0.35f, 0f to 0.55f, 1f to 0f)
        shade(h * 0.55f, h, 0f to 0f, 1f to 0.8f)
        val left = 5 * u
        val right = w - 5 * u
        val mast = mark(21f, spacingU = -1f, maxU = 90f)
        draw(mast, left, safeTop + u)
        val ruleY = safeTop + u + mast.size.height + u
        scope.drawLine(white, Offset(left, ruleY), Offset(right, ruleY), 0.3f * u)
        val issue = listOf(t.date, t.workout, "BY SUDARSHAN").map { body(it, 2.8f, spacingU = 0.3f) }
        val issueY = ruleY + 1.4f * u
        draw(issue[0], left, issueY)
        draw(issue[1], (w - issue[1].size.width) / 2, issueY)
        draw(issue[2], right - issue[2].size.width, issueY)

        val sticker = if (t.bests > 0) {
            body(if (t.bests == 1) "1 NEW BEST" else "${t.bests} NEW BESTS", 3.4f, ink, 0.2f)
        } else {
            null
        }
        val lines = listOf(
            Line(head(t.number, 17f, spacingU = -0.5f, maxU = 90f), -0.4f),
            Line(head(t.unit, 4.6f, spacingU = 0.45f), 1.6f),
            Line(body(stats(t.stats.take(2)), 3.8f)),
        )
        val stickerH = sticker?.let { it.size.height + 2 * u + 2 * u } ?: 0f
        var y = safeBottom - 4 * u - height(lines) - stickerH
        if (sticker != null) {
            val box = Size(sticker.size.width + 4.8f * u, sticker.size.height + 2 * u)
            scope.rotate(-3f, Offset(left + box.width / 2, y + box.height / 2)) {
                drawRect(accent, Offset(left, y), box)
                drawText(sticker, topLeft = Offset(left + 2.4f * u, y + u))
            }
            y += stickerH
        }
        column(lines, left, y)
    }

    // ---- shared pieces ----

    /** Overline, number, unit and stats: the stack the Classic card leads with. */
    private fun mainLines(t: ShareCardText, statsGap: Float = 0f) = listOf(
        Line(head(t.overline, 3.4f, accent, 0.35f, maxU = 88f), 0.6f),
        Line(head(t.number, 19f, spacingU = -0.5f, maxU = 88f), -1.2f),
        Line(head(t.unit, 4.6f, spacingU = 0.45f), 2.6f),
        Line(body(stats(t.stats), 3.8f), statsGap),
    )

    /** "habita." over "by Sudarshan", its bottom on [bottom]. */
    private fun signature(x: Float, bottom: Float, align: Align, width: Float = 0f) {
        val lines = listOf(Line(mark(4.6f), -0.4f), Line(credit()))
        column(lines, x, bottom - height(lines), align, width)
    }

    /** "habita." with the dot in [dot]. */
    private fun mark(sizeU: Float, color: Color = white, dot: Color = accent, spacingU: Float = 0.08f, maxU: Float = 100f) =
        head(
            buildAnnotatedString {
                append("habita")
                withStyle(SpanStyle(color = dot)) { append(".") }
            },
            sizeU,
            color,
            spacingU,
            maxU,
        )

    private fun credit() = body("by Sudarshan", 3f, CardInk.soft, 0.1f, FontWeight.SemiBold)

    /** [parts] with dots between and every run of digits in [digits]. */
    private fun stats(parts: List<String>, digits: Color = accent) = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) append("  ·  ")
            var start = 0
            Regex("""\d[\d,.]*""").findAll(part).forEach { match ->
                append(part.substring(start, match.range.first))
                withStyle(SpanStyle(color = digits)) { append(match.value) }
                start = match.range.last + 1
            }
            append(part.substring(start))
        }
    }

    /** A dark band from [fromY] to [toY], its opacity at each stop given as (fraction, alpha). */
    private fun shade(fromY: Float, toY: Float, vararg stops: Pair<Float, Float>) {
        scope.drawRect(
            Brush.verticalGradient(
                *stops.map { (at, alpha) -> at to ink.copy(alpha = alpha) }.toTypedArray(),
                startY = fromY,
                endY = toY,
            ),
            topLeft = Offset(0f, fromY),
            size = Size(w, toY - fromY),
        )
    }

    // ---- text ----

    /** Unbounded, shrunk to fit [maxU] card units wide; drawn as an outline when [strokeU] is above 0. */
    private fun head(
        value: CharSequence,
        sizeU: Float,
        color: Color = white,
        spacingU: Float = 0f,
        maxU: Float = 100f,
        strokeU: Float = 0f,
    ): TextLayoutResult = fitted(value, fonts.heading, FontWeight.SemiBold, sizeU, color, spacingU, maxU, strokeU)

    private fun body(
        value: CharSequence,
        sizeU: Float,
        color: Color = white,
        spacingU: Float = 0f,
        weight: FontWeight = FontWeight.Bold,
    ): TextLayoutResult = fitted(value, fonts.body, weight, sizeU, color, spacingU, maxU = 90f)

    private fun mono(value: String, sizeU: Float, color: Color = ink) =
        fitted(value, FontFamily.Monospace, FontWeight.Medium, sizeU, color, 0f, maxU = 40f)

    private fun fitted(
        value: CharSequence,
        family: FontFamily,
        weight: FontWeight,
        sizeU: Float,
        color: Color,
        spacingU: Float,
        maxU: Float,
        strokeU: Float = 0f,
    ): TextLayoutResult {
        val text = value as? AnnotatedString ?: AnnotatedString(value.toString())
        fun at(scale: Float) = measurer.measure(
            text,
            TextStyle(
                fontFamily = family,
                fontWeight = weight,
                fontSize = (sizeU * scale * u).sp,
                letterSpacing = (spacingU * scale * u).sp,
                color = color,
                drawStyle = if (strokeU > 0) Stroke(strokeU * u) else Fill,
            ),
            softWrap = false,
            maxLines = 1,
        )
        val first = at(1f)
        val max = maxU * u
        return if (first.size.width <= max) first else at(max / first.size.width)
    }

    private fun draw(text: TextLayoutResult, x: Float, y: Float) = scope.drawText(text, topLeft = Offset(x, y))

    private fun height(lines: List<Line>) =
        lines.fold(0f) { sum, line -> sum + line.text.size.height + line.gapU * u } - lines.last().gapU * u

    /** Draws [lines] down from [top] and returns where the last one ends. */
    private fun column(lines: List<Line>, x: Float, top: Float, align: Align = Align.START, width: Float = 0f): Float {
        var y = top
        lines.forEachIndexed { i, line ->
            val lineX = when (align) {
                Align.START -> x
                Align.CENTER -> x + (width - line.text.size.width) / 2
                Align.END -> x + width - line.text.size.width
            }
            draw(line.text, lineX, y)
            y += line.text.size.height + if (i < lines.lastIndex) line.gapU * u else 0f
        }
        return y
    }
}