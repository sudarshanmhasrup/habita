package dev.habita.app.feature.gym.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaColors
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.ui.HText
import dev.habita.app.feature.gym.data.TrendPoint
import dev.habita.app.shared.gym.Effort
import dev.habita.app.shared.gym.Exercise
import dev.habita.app.shared.gym.SetLog

/*
 * Small charts for the workout summary. Each mark carries a text label, so nothing is read from
 * color alone, and every bar is anchored to a baseline with a rounded data end.
 */

private val BarEnd = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)

internal fun HabitaColors.effort(effort: Effort?): Color = when (effort) {
    Effort.EASY -> effortEasy
    Effort.MEDIUM -> effortMedium
    Effort.HARD -> effortHard
    null -> border
}

internal fun Effort.label(): String = when (this) {
    Effort.EASY -> "Easy"
    Effort.MEDIUM -> "Medium"
    Effort.HARD -> "Hard"
}

/** A colored dot and a label, as used in legends. */
@Composable
internal fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        HText(text, HabitaTheme.typography.caption, color = HabitaTheme.colors.muted)
    }
}

/**
 * One horizontal bar split into parts, with a 2 dp gap between them and a legend below. Parts with
 * no value are left out.
 */
@Composable
internal fun SplitBar(parts: List<Triple<String, Double, Color>>, modifier: Modifier = Modifier) {
    val shown = parts.filter { it.second > 0 }
    val total = shown.sumOf { it.second }
    Column(modifier.fillMaxWidth()) {
        if (total <= 0) {
            Box(Modifier.fillMaxWidth().height(14.dp).background(HabitaTheme.colors.border, RoundedCornerShape(7.dp)))
        } else {
            Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                shown.forEachIndexed { index, (_, value, color) ->
                    val shape = RoundedCornerShape(
                        topStart = if (index == 0) 7.dp else 2.dp,
                        bottomStart = if (index == 0) 7.dp else 2.dp,
                        topEnd = if (index == shown.lastIndex) 7.dp else 2.dp,
                        bottomEnd = if (index == shown.lastIndex) 7.dp else 2.dp,
                    )
                    Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(color, shape))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            parts.forEach { (label, _, color) -> LegendItem(color, label) }
        }
    }
}

/**
 * Vertical bars, one per session, oldest on the left. Today's bar is in the accent and carries
 * its value; earlier ones are quiet, with their date underneath.
 */
@Composable
internal fun TrendBars(
    points: List<TrendPoint>,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val max = points.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1.0
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEach { point ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    HText(
                        format(point.value),
                        type.caption,
                        color = if (point.isToday) colors.text else colors.muted,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    val fraction = (point.value / max).toFloat().coerceIn(0.02f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((height - 24.dp) * fraction)
                            .background(if (point.isToday) colors.accent else colors.border, BarEnd),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            points.forEach { point ->
                HText(
                    if (point.isToday) "Today" else shortDate(point.at),
                    type.caption,
                    color = if (point.isToday) colors.text else colors.muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Today's sets of one exercise as bars: height is the weight (or the seconds held), the fill is
 * how the set felt, and each bar is labelled with weight above and reps below. A skipped set is a
 * dashed outline.
 */
@Composable
internal fun SetBars(exercise: Exercise, sets: List<SetLog>, modifier: Modifier = Modifier, height: Dp = 96.dp) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    fun value(set: SetLog): Int = if (exercise.isWeighted) set.weightGrams else set.achieved ?: 0
    val max = sets.maxOfOrNull(::value)?.takeIf { it > 0 } ?: 1
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            sets.forEach { set ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    HText(
                        when {
                            set.isSkipped -> "Skipped"
                            exercise.isWeighted -> "${formatKg(set.weightGrams)} kg"
                            else -> exercise.formatAmount(set.achieved ?: 0)
                        },
                        type.caption,
                        color = if (set.isSkipped) colors.muted else colors.text,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (set.isSkipped) {
                        val border = colors.border
                        Box(
                            Modifier.fillMaxWidth().height(height - 24.dp).drawBehind {
                                drawRoundRect(
                                    color = border,
                                    cornerRadius = CornerRadius(4.dp.toPx()),
                                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                                )
                            },
                        )
                    } else {
                        val fraction = (value(set).toFloat() / max).coerceIn(0.06f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((height - 24.dp) * fraction)
                                .background(colors.effort(set.effort), BarEnd),
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            sets.forEach { set ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    HText(
                        if (exercise.isWeighted && !set.isSkipped) "× ${set.achieved}" else "Set ${set.setNumber}",
                        type.bodyStrong,
                        maxLines = 1,
                    )
                    HText(set.effort?.label() ?: if (set.isSkipped) "" else "–", type.caption, color = colors.muted, maxLines = 1)
                }
            }
        }
    }
}

/**
 * A line through recent sessions, today's point marked with a ringed dot. Shows nothing for fewer
 * than two points, since one point is not a trend.
 */
@Composable
internal fun Sparkline(points: List<TrendPoint>, modifier: Modifier = Modifier, height: Dp = 44.dp) {
    if (points.size < 2) return
    val colors = HabitaTheme.colors
    Canvas(modifier.fillMaxWidth().height(height)) {
        val min = points.minOf { it.value }
        val max = points.maxOf { it.value }
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val pad = 6.dp.toPx()
        fun at(index: Int): Offset {
            val x = pad + (size.width - 2 * pad) * index / (points.size - 1)
            val y = if (max == min) size.height / 2 else pad + (size.height - 2 * pad) * (1 - ((points[index].value - min) / span).toFloat())
            return Offset(x, y)
        }
        val path = Path().apply {
            moveTo(at(0).x, at(0).y)
            for (i in 1 until points.size) lineTo(at(i).x, at(i).y)
        }
        drawPath(path, colors.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = at(points.lastIndex)
        drawCircle(colors.card, radius = 6.dp.toPx(), center = last)
        drawCircle(colors.accent, radius = 4.dp.toPx(), center = last)
    }
}