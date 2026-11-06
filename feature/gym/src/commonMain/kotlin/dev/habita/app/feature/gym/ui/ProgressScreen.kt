package dev.habita.app.feature.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.LocalWideLayout
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.TopBar
import dev.habita.app.core.ui.TwoPane
import dev.habita.app.core.ui.appear
import dev.habita.app.feature.gym.data.TrendPoint
import dev.habita.app.shared.gym.BodyWeek
import dev.habita.app.shared.gym.Exercise
import dev.habita.app.shared.gym.ExerciseProgress
import dev.habita.app.shared.gym.Measure

/** Everything the progress screen shows, over the whole history. */
internal data class ProgressData(val exercises: List<ExerciseProgress>, val body: List<BodyWeek>)

/**
 * The long view: every exercise from its first session to its latest, and bodyweight and waist
 * since the first weigh-in. Summaries show recent sessions; this shows the years.
 */
@Composable
internal fun ProgressScreen(data: ProgressData?, onBack: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val wide = LocalWideLayout.current
    TwoPane(
        first = {
            TopBar("Gym", onBack)
            Gap(Space.inside)
            HText("Progress", type.headline)
            Gap(Space.tiny)
            HText("Since your first workout.", type.body, color = colors.muted)
            Gap(Space.section)
            if (data == null) {
                HText("Loading…", type.body, color = colors.muted)
                return@TwoPane
            }
            BodyProgress(data.body)
        },
        second = {
            if (data == null) return@TwoPane
            if (wide) Gap(56.dp)
            Overline("Exercises")
            Gap(Space.item)
            if (data.exercises.isEmpty()) {
                HText("Finish a workout and each exercise shows up here.", type.body, color = colors.muted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
                data.exercises.forEachIndexed { index, progress ->
                    if (index > 0 && !progress.inPlan && data.exercises[index - 1].inPlan) {
                        Gap(Space.inside)
                        Overline("No longer in the plan, or swapped in")
                    }
                    ExerciseCard(progress)
                }
            }
            Gap(Space.card)
        },
    )
}

@Composable
private fun BodyProgress(weeks: List<BodyWeek>) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    HabitaCard(Modifier.fillMaxWidth().appear(1)) {
        Overline("Bodyweight")
        Gap(Space.inside)
        val weights = weeks.filter { it.weightGrams != null }
        if (weights.isEmpty()) {
            HText("Log your weight at the gym and its weekly average shows up here.", type.body, color = colors.muted)
        } else {
            FromTo(
                first = "${oneDecimalKg(weights.first().weightGrams!!)} kg",
                latest = "${oneDecimalKg(weights.last().weightGrams!!)} kg",
                detail = "Weekly average, over ${weeksLabel(weights.first().weeksAgo)}",
            )
            Gap(Space.item)
            Sparkline(weights.map { TrendPoint(-it.weeksAgo.toLong(), it.weightGrams!! / 1000.0, it.weeksAgo == 0) })
        }
        val waists = weeks.filter { it.waistMm != null }
        if (waists.isNotEmpty()) {
            Gap(Space.card)
            Overline("Waist")
            Gap(Space.inside)
            FromTo(
                first = "${oneDecimalKg(waists.first().waistMm!! * 100)} cm",
                latest = "${oneDecimalKg(waists.last().waistMm!! * 100)} cm",
                detail = "Over ${weeksLabel(waists.first().weeksAgo)}",
            )
            Gap(Space.item)
            Sparkline(waists.map { TrendPoint(-it.weeksAgo.toLong(), it.waistMm!! / 10.0, it.weeksAgo == 0) })
        }
    }
}

@Composable
private fun ExerciseCard(progress: ExerciseProgress) {
    val exercise = progress.exercise
    HabitaCard(Modifier.fillMaxWidth().appear(2)) {
        HText(exercise.name, HabitaTheme.typography.title)
        Gap(Space.inside)
        FromTo(
            first = amount(exercise, progress.first),
            latest = amount(exercise, progress.latest),
            detail = "${progress.points.size} ${if (progress.points.size == 1) "session" else "sessions"} since " +
                monthYear(progress.points.first().at) + " · best ${amount(exercise, progress.best)}",
        )
        if (progress.points.size >= 2) {
            Gap(Space.item)
            Sparkline(progress.points.map { TrendPoint(it.at, it.value.toDouble(), it == progress.points.last()) })
        }
    }
}

/** "20 kg → 32.5 kg" with a line under it; the latest is in the accent when it is higher. */
@Composable
private fun FromTo(first: String, latest: String, detail: String) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    Row(verticalAlignment = Alignment.CenterVertically) {
        HText(first, type.bodyStrong, color = colors.muted)
        Gap(Space.inside)
        HText("→", type.bodyStrong, color = colors.muted)
        Gap(Space.inside)
        HText(latest, type.bodyStrong)
    }
    HText(detail, type.caption, color = colors.muted)
}

private fun amount(exercise: Exercise, value: Int): String = when {
    exercise.isWeighted -> "${formatKg(value)} kg"
    exercise.measure == Measure.SECONDS -> "$value s"
    else -> "$value reps"
}

/** 78_350 → "78.4". */
private fun oneDecimalKg(thousandths: Int): String {
    val tenths = (thousandths + 50) / 100
    return "${tenths / 10}.${tenths % 10}"
}

private fun weeksLabel(weeks: Int): String = when {
    weeks <= 1 -> "the last week"
    weeks < 9 -> "$weeks weeks"
    else -> "${weeks / 4} months"
}

private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Mar 2026", from the local date. */
private fun monthYear(millis: Long): String {
    val (year, month) = backupDate(millis).split('-')
    return "${months[month.toInt() - 1]} $year"
}