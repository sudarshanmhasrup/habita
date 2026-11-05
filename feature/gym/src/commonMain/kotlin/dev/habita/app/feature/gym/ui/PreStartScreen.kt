package dev.habita.app.feature.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.appear
import dev.habita.app.core.ui.Divider
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.LocalWideLayout
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.StatTile
import dev.habita.app.core.ui.TopBar
import dev.habita.app.core.ui.TwoPane
import dev.habita.app.shared.gym.ExerciseTarget
import dev.habita.app.shared.gym.Program

/** The whole workout before it starts: warm-up, exercises and today's targets. */
@Composable
internal fun PreStartScreen(
    workoutId: String,
    data: GymData,
    targets: List<ExerciseTarget>?,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val program = data.program ?: return
    val workout = program.workout(workoutId) ?: return
    val warmUp = program.warmUps.firstOrNull { it.id == workout.warmUpId }
    val wide = LocalWideLayout.current
    Column(Modifier.fillMaxSize()) {
        TwoPane(Modifier.weight(1f), first = {
            TopBar("Gym", onBack)
            Gap(Space.inside)
            if (workout.id == data.suggested?.id) {
                Overline("Up next", color = colors.accent)
                Gap(Space.tiny)
            }
            HText(workout.name, type.headline)
            Gap(Space.card)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.item)) {
                StatTile("Exercises", "${workout.exercises.size}", Modifier.weight(1f).appear(0))
                StatTile("Sets", "${workout.exercises.sumOf { it.sets }}", Modifier.weight(1f).appear(0))
                StatTile("Minutes", "~${estimateMinutes(workout, warmUp)}", Modifier.weight(1f).appear(0))
            }
            if (warmUp != null) {
                Gap(Space.section)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline("Warm-up", Modifier.weight(1f))
                    HText("then ${warmUp.restSeconds / 60} min break", type.caption, color = colors.muted)
                }
                Gap(Space.item)
                HabitaCard(Modifier.fillMaxWidth().appear(1)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
                        warmUp.items.forEach { item ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                HText(item.name, type.body, modifier = Modifier.weight(1f))
                                HText(item.amount, type.caption, color = colors.muted, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
        }, second = {
            // Lines the list up with the title in the first pane.
            if (wide) Gap(56.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline("Workout", Modifier.weight(1f))
                HText("today's weight", type.caption, color = colors.muted)
            }
            Gap(Space.item)
            ExerciseList(program, workoutId, targets)
            Gap(Space.card)
        })
        Box(
            Modifier
                .align(Alignment.End)
                .fillMaxWidth(if (wide) 0.5f else 1f)
                .padding(horizontal = Space.screen, vertical = Space.item),
        ) {
            if (data.active != null) {
                HText(
                    "Finish or discard the workout in progress first.",
                    type.body,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PrimaryButton("Start workout", onStart, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ExerciseList(program: Program, workoutId: String, targets: List<ExerciseTarget>?) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val workout = program.workout(workoutId) ?: return
    HabitaCard(Modifier.fillMaxWidth().appear(2)) {
        workout.exercises.forEachIndexed { index, slot ->
            val exercise = program.exercise(slot.exerciseId) ?: return@forEachIndexed
            if (index > 0) {
                Gap(Space.item)
                Divider()
                Gap(Space.item)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HText("${index + 1}", type.label, color = colors.muted, modifier = Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    HText(exercise.name, type.bodyStrong)
                    HText(
                        "${slot.sets} × ${exercise.formatAmount(slot.target)} · ${slot.restSeconds} s rest",
                        type.caption,
                        color = colors.muted,
                    )
                }
                val target = targets?.getOrNull(index)
                if (target != null) {
                    Gap(Space.inside)
                    HText(
                        when {
                            !exercise.isWeighted -> exercise.formatAmount(target.target)
                            target.weightGrams == null -> "New"
                            else -> "${formatKg(target.weightGrams!!)} kg"
                        },
                        type.bodyStrong,
                        color = if (target.reason.isIncrease) colors.accent else colors.text,
                    )
                }
            }
        }
    }
}