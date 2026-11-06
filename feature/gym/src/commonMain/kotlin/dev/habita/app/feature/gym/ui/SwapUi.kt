package dev.habita.app.feature.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.TextAction
import dev.habita.app.feature.gym.workout.ActiveWorkout
import dev.habita.app.shared.gym.Equipment
import dev.habita.app.shared.gym.Exercise
import dev.habita.app.shared.gym.Program
import dev.habita.app.shared.gym.Swaps
import dev.habita.app.shared.gym.Workout

/**
 * "Not at this gym" for slot [index]: the alternatives to pick from, and a way back to the planned
 * exercise when it's swapped. [onSwap] gets the planned exercise id and the pick, `null` to undo.
 * Shows nothing when there is nothing to swap to.
 */
@Composable
internal fun ColumnScope.SwapAction(
    program: Program,
    planned: Workout,
    current: Workout,
    index: Int,
    onSwap: (exerciseId: String, replacementId: String?) -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val plannedId = planned.exercises[index].exerciseId
    val swapped = current.exercises[index].exerciseId != plannedId
    val choices = Swaps.choices(planned, current, index, program).filter { it.id != current.exercises[index].exerciseId }
    if (choices.isEmpty() && !swapped) return
    var open by remember(plannedId) { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (swapped) {
            HText(
                "Instead of ${program.exercise(plannedId)?.name ?: plannedId} at this gym",
                type.caption,
                color = colors.accent,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        TextAction(if (open) "Close" else if (swapped) "Change" else "Not at this gym", { open = !open }, color = colors.muted)
    }
    if (open) {
        Gap(Space.inside)
        Column(verticalArrangement = Arrangement.spacedBy(Space.inside)) {
            choices.forEach { choice ->
                SecondaryButton(
                    "${choice.name} · ${choice.equipmentName()}",
                    { onSwap(plannedId, choice.id); open = false },
                    Modifier.fillMaxWidth(),
                )
            }
            if (swapped) {
                SecondaryButton(
                    "Back to ${program.exercise(plannedId)?.name ?: plannedId}",
                    { onSwap(plannedId, null); open = false },
                    Modifier.fillMaxWidth(),
                )
            }
        }
        Gap(Space.tiny)
        HText("Kept for next time, until undone or you start a new gym.", type.caption, color = colors.muted)
    }
}

/** [SwapAction] for the exercise about to be done, until its first working set is logged. */
@Composable
internal fun ColumnScope.SwapAction(
    active: ActiveWorkout,
    program: Program?,
    slotIndex: Int,
    onSwap: (exerciseId: String, replacementId: String?) -> Unit,
) {
    if (program == null || !active.canSwap(slotIndex)) return
    Gap(Space.inside)
    SwapAction(program, active.planned, active.workout, slotIndex, onSwap)
}

private fun Exercise.equipmentName(): String = when (equipment) {
    Equipment.DUMBBELL -> "dumbbells"
    Equipment.MACHINE -> "machine"
    Equipment.CABLE -> "cable"
    Equipment.BODYWEIGHT -> "bodyweight"
}