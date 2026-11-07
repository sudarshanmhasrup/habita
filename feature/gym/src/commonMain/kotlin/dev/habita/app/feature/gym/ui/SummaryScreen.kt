package dev.habita.app.feature.gym.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.LocalWideLayout
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.Pill
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.StatTile
import dev.habita.app.core.ui.TextAction
import dev.habita.app.core.ui.TwoPane
import dev.habita.app.core.ui.appear
import dev.habita.app.feature.gym.data.ExerciseReport
import dev.habita.app.feature.gym.data.SessionReport
import dev.habita.app.shared.gym.Effort
import dev.habita.app.shared.gym.WorkoutSummary
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The end of a workout: the headline numbers, then charts of how it went. */
@Composable
internal fun SummaryScreen(report: SessionReport?, onDone: () -> Unit, onUndo: (() -> Unit)? = null) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val wide = LocalWideLayout.current
    Column(Modifier.fillMaxSize()) {
        TwoPane(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.card),
            first = {
                Gap(Space.card)
                DoneBadge()
                Gap(Space.card)
                Overline("Workout done", color = colors.accent)
                Gap(Space.inside)
                HText(report?.workoutName ?: "Nice work", type.headline)
                Gap(Space.card)
                if (report == null) {
                    HText("Adding it up…", type.body, color = colors.muted)
                } else {
                    Overview(report)
                }
            },
            second = {
                if (report != null) {
                    // Lines the exercises up with the stats in the first pane.
                    if (wide) Gap(108.dp)
                    Exercises(report)
                }
            },
        )
        if (onUndo != null) {
            // The last set finished the workout; taking it back reopens the workout on it.
            TextAction(
                "Undo last set",
                onUndo,
                Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
                color = HabitaTheme.colors.muted,
            )
        }
        PrimaryButton(
            "Done",
            onDone,
            Modifier
                .align(Alignment.End)
                .fillMaxWidth(if (wide) 0.5f else 1f)
                .padding(horizontal = Space.screen, vertical = Space.item),
        )
    }
}

@Composable
private fun ColumnScope.Overview(report: SessionReport) {
    val summary = report.summary
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    Row(horizontalArrangement = Arrangement.spacedBy(Space.item)) {
        StatTile("Time", formatDuration(summary.totalMillis), Modifier.weight(1f).appear(0))
        StatTile(
            "Volume",
            "${formatVolume(summary.volumeKg)} kg",
            Modifier.weight(1f),
            detail = volumeChange(summary) ?: "First one, trend next time",
        )
    }
    Gap(Space.item)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.item)) {
        StatTile("Sets done", "${summary.setsCompleted}", Modifier.weight(1f).appear(0), detail = summary.setsSkipped.takeIf { it > 0 }?.let { "$it skipped" })
        StatTile("New bests", "${summary.personalBests.size}", Modifier.weight(1f).appear(0))
    }
    Gap(Space.item)

    ChartCard("Time", "Where the ${formatDuration(summary.totalMillis)} went") {
        SplitBar(
            listOf(
                Triple("Warm-up ${formatDuration(summary.warmUpMillis)}", summary.warmUpMillis.toDouble(), colors.effortEasy),
                Triple("Main ${formatDuration(summary.mainMillis)}", summary.mainMillis.toDouble(), colors.effortMedium),
            ),
        )
    }

    if (report.efforts.isNotEmpty()) {
        Gap(Space.item)
        val total = report.efforts.values.sum()
        ChartCard("Effort", "How your $total rated sets felt") {
            SplitBar(
                Effort.entries.map { effort ->
                    val count = report.efforts[effort] ?: 0
                    Triple("${effort.label()} $count", count.toDouble(), colors.effort(effort))
                },
            )
        }
    }

    if (report.volumeTrend.size >= 2) {
        Gap(Space.item)
        ChartCard("Volume", "Weight × reps, your last ${report.volumeTrend.size} ${report.workoutName} sessions") {
            TrendBars(report.volumeTrend, format = { formatCompactKg(it) })
        }
    }

    if (summary.nextIncreases.isNotEmpty()) {
        Gap(Space.item)
        HabitaCard(Modifier.fillMaxWidth().appear(1), borderColor = colors.accent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HIcon(HabitaIcons.TrendUp, contentDescription = null, tint = colors.accent)
                Gap(Space.item)
                HText("Heavier next time", type.title)
            }
            Gap(Space.inside)
            val names = report.exercises.filter { it.heavierNext }.map { it.exercise.name }
            HText(names.joinToString(" · "), type.body, color = colors.text)
        }
    }
}

@Composable
private fun ColumnScope.Exercises(report: SessionReport) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    Overline("Exercise by exercise")
    Gap(Space.item)
    Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
        report.exercises.forEach { ExerciseCard(it) }
    }
    if (report.efforts.isNotEmpty()) {
        Gap(Space.item)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Effort.entries.forEach { LegendItem(colors.effort(it), it.label()) }
        }
        Gap(Space.tiny)
        HText("Bar height is the weight; color is how the set felt.", type.caption, color = colors.muted)
    }
}

@Composable
private fun ChartCard(title: String, caption: String, content: @Composable () -> Unit) {
    HabitaCard(Modifier.fillMaxWidth().appear(2)) {
        HText(title, HabitaTheme.typography.title)
        HText(caption, HabitaTheme.typography.caption, color = HabitaTheme.colors.muted)
        Gap(Space.card)
        content()
    }
}

@Composable
private fun ExerciseCard(report: ExerciseReport) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val exercise = report.exercise
    HabitaCard(Modifier.fillMaxWidth().appear(3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HText(exercise.name, type.bodyStrong, modifier = Modifier.weight(1f))
            when {
                report.personalBest -> Pill("Best")
                report.progressed -> Pill("Up")
                report.heavierNext -> Pill("Heavier next")
            }
        }
        HText(
            report.lastTime?.let { "Last time ${it.describe(exercise)}" } ?: "First time",
            type.caption,
            color = colors.muted,
        )
        report.warmUpSet?.let { warmUp ->
            HText("Warm-up set ${formatKg(warmUp.weightGrams)} kg × ${warmUp.achieved}", type.caption, color = colors.muted)
        }
        Gap(Space.item)
        SetBars(exercise, report.sets)
        if (report.trend.size >= 2) {
            Gap(Space.item)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    HText(
                        if (exercise.isWeighted) "Working weight, last ${report.trend.size}" else "Best hold, last ${report.trend.size}",
                        type.caption,
                        color = colors.muted,
                    )
                    val first = report.trend.first().value
                    val last = report.trend.last().value
                    HText(
                        if (exercise.isWeighted) "${formatCompactKg(first)} → ${formatCompactKg(last)} kg" else "${first.toInt()} → ${last.toInt()} s",
                        type.bodyStrong,
                    )
                }
                Gap(Space.item)
                Sparkline(report.trend, Modifier.weight(1f))
            }
        }
    }
}

private fun volumeChange(summary: WorkoutSummary): String? {
    val change = summary.volumeChangePercent ?: return null
    val rounded = change.roundToInt()
    return when {
        rounded > 0 -> "+$rounded% vs last"
        rounded < 0 -> "−${abs(rounded)}% vs last"
        else -> "Same as last"
    }
}

/** 4320.0 → "4.3k", 850.0 → "850", 22.5 → "22.5". */
private fun formatCompactKg(kg: Double): String = when {
    kg >= 10_000 -> "${(kg / 1000).roundToInt()}k"
    kg >= 1000 -> "${(kg / 100).roundToInt() / 10.0}k"
    else -> formatKg((kg * 1000).roundToInt())
}

/** A green check in a faint green ring, at the top of the summary. It pops in, ring first. */
@Composable
private fun DoneBadge() {
    val colors = HabitaTheme.colors
    val ring = remember { Animatable(0.6f) }
    val check = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { ring.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
        delay(120)
        check.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    Box(
        Modifier
            .size(76.dp)
            .graphicsLayer {
                scaleX = ring.value
                scaleY = ring.value
            }
            .background(colors.accent.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .graphicsLayer {
                    scaleX = check.value
                    scaleY = check.value
                }
                .background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            HIcon(HabitaIcons.Check, contentDescription = null, modifier = Modifier.size(26.dp), tint = colors.onAccent)
        }
    }
}