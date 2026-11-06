package dev.habita.app.shared.gym

import kotlinx.serialization.Serializable

/**
 * At this gym, [exerciseId] is done as [replacementId], one of its alternatives in the plan. A swap
 * stays until it is undone or a new gym clears every swap; both mark it [deleted]. [exerciseId] is
 * the record id, so an exercise has at most one swap.
 */
@Serializable
data class ExerciseSwap(
    val exerciseId: String,
    val replacementId: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

object Swaps {
    /** The swaps in use, by the exercise they replace. */
    fun active(swaps: List<ExerciseSwap>): Map<String, String> =
        swaps.filter { !it.deleted }.associate { it.exerciseId to it.replacementId }

    /**
     * [workout] with its swapped exercises replaced. A swap is ignored when the plan no longer lists
     * the replacement as an alternative, or when the replacement is already in the workout, so an
     * exercise never appears twice.
     */
    fun apply(workout: Workout, program: Program, swaps: Map<String, String>): Workout {
        val used = workout.exercises.map { it.exerciseId }.toMutableSet()
        return workout.copy(
            exercises = workout.exercises.map { slot ->
                val replacement = swaps[slot.exerciseId]
                    ?.takeIf { it in program.exercise(slot.exerciseId)?.alternatives.orEmpty() }
                    ?.takeIf { it !in used && program.exercise(it) != null }
                    ?: return@map slot
                used += replacement
                slot.copy(exerciseId = replacement)
            },
        )
    }

    /**
     * The alternatives that can replace slot [index] of [workout] (as planned, before swaps):
     * everything the plan lists, except exercises already in the workout as [current] has it.
     */
    fun choices(planned: Workout, current: Workout, index: Int, program: Program): List<Exercise> {
        val original = planned.exercises[index].exerciseId
        val inWorkout = current.exercises.filterIndexed { i, _ -> i != index }.map { it.exerciseId }.toSet()
        return program.exercise(original)?.alternatives.orEmpty()
            .filter { it !in inWorkout }
            .mapNotNull { program.exercise(it) }
    }
}