package dev.habita.app.feature.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.ui.BackLink
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.PrimaryButton
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
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            BackLink("Gym", onBack)
            Gap(12.dp)
            HText(workout.name, type.headline)
            Gap(6.dp)
            val breakMinutes = (warmUp?.restSeconds ?: 0) / 60
            HText(
                "${warmUp?.items?.size ?: 0} warm-up items, a $breakMinutes min break, then ${workout.exercises.size} exercises.",
                type.body,
                color = colors.muted,
            )
            if (warmUp != null) {
                Gap(24.dp)
                HText("Warm-up", type.label, color = colors.muted)
                Gap(8.dp)
                HabitaCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        warmUp.items.forEach { item ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                HText(item.name, type.body, modifier = Modifier.weight(1f))
                                HText(item.amount, type.body, color = colors.muted)
                            }
                        }
                    }
                }
            }
            Gap(24.dp)
            HText("Workout", type.label, color = colors.muted)
            Gap(8.dp)
            ExerciseList(program, workoutId, targets)
            Gap(16.dp)
        }
        Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            if (data.active != null) {
                HText(
                    "Finish or discard the workout in progress first.",
                    type.body,
                    color = colors.muted,
                    modifier = Modifier.align(Alignment.Center),
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
    HabitaCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            workout.exercises.forEachIndexed { index, slot ->
                val exercise = program.exercise(slot.exerciseId) ?: return@forEachIndexed
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HText(exercise.name, type.bodyStrong)
                        HText(
                            "${slot.sets} × ${exercise.formatAmount(slot.target)} · ${slot.restSeconds} s rest",
                            type.caption,
                            color = colors.muted,
                        )
                    }
                    val target = targets?.getOrNull(index)
                    if (target != null && exercise.isWeighted) {
                        HText(target.weightGrams?.let { "${formatKg(it)} kg" } ?: "New", type.bodyStrong, color = colors.accent)
                    }
                }
            }
        }
    }
}