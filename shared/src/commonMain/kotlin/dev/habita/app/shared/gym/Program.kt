package dev.habita.app.shared.gym

import kotlinx.serialization.Serializable

/**
 * The workout plan, parsed from `program/PROGRAM.md`.
 */
@Serializable
data class Program(
    val exercises: List<Exercise>,
    val warmUps: List<WarmUp>,
    val workouts: List<Workout>,
) {
    fun exercise(id: String): Exercise? = exercises.firstOrNull { it.id == id }

    fun workout(id: String): Workout? = workouts.firstOrNull { it.id == id }
}

@Serializable
enum class Equipment { DUMBBELL, MACHINE, CABLE, BODYWEIGHT }

@Serializable
enum class Measure { REPS, SECONDS }

/**
 * An exercise. Its [id] is permanent: history is keyed by it, so the name can change safely.
 *
 * [defaultStep] is how much the target grows when progressing: grams of weight for weighted
 * exercises, reps or seconds for bodyweight ones.
 */
@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val equipment: Equipment,
    val measure: Measure,
    val perSide: Boolean,
    val defaultStep: Int,
) {
    val isWeighted: Boolean get() = equipment != Equipment.BODYWEIGHT
}

@Serializable
data class WarmUp(
    val id: String,
    val name: String,
    val items: List<WarmUpItem>,
    val restSeconds: Int,
)

@Serializable
data class WarmUpItem(
    val name: String,
    val amount: String,
)

/**
 * A workout. Workouts with a [rotation] position are suggested in that order; a `null` rotation
 * marks a fallback workout, which never moves the rotation forward.
 */
@Serializable
data class Workout(
    val id: String,
    val name: String,
    val warmUpId: String,
    val rotation: Int?,
    val exercises: List<WorkoutExercise>,
)

/**
 * An exercise inside a workout. [target] is reps, or seconds for timed exercises.
 */
@Serializable
data class WorkoutExercise(
    val exerciseId: String,
    val sets: Int,
    val target: Int,
    val restSeconds: Int,
)