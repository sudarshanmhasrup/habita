package dev.habita.app.feature.gym.workout

import dev.habita.app.shared.gym.Exercise
import dev.habita.app.shared.gym.ExerciseTarget
import dev.habita.app.shared.gym.SetAdvice
import dev.habita.app.shared.gym.SetLog
import dev.habita.app.shared.gym.WarmUp
import dev.habita.app.shared.gym.Workout
import dev.habita.app.shared.gym.WorkoutExercise
import kotlinx.serialization.Serializable

@Serializable
enum class Phase {
    /** Warm-up items, one at a time. */
    WARM_UP,

    /** The break between the warm-up and the main workout. */
    BREAK,

    /** The main workout, set by set. */
    MAIN,
}

/** A running countdown, stored as times so it survives the app being closed. */
@Serializable
data class Rest(val startedAt: Long, val endsAt: Long) {
    val totalMillis: Long get() = endsAt - startedAt

    fun remainingMillis(now: Long): Long = (endsAt - now).coerceAtLeast(0)
}

/**
 * The workout in progress. It is saved after every step, so the app can resume it after a crash.
 *
 * Exercises are referred to by slot: their index in [Workout.exercises]. [exercises] and [targets]
 * are indexed by slot too. The workout and its exercises are copied in at the start, so a new
 * program version arriving mid-workout changes nothing.
 */
@Serializable
data class ActiveWorkout(
    val sessionId: String,
    val startedAt: Long,
    val workout: Workout,
    val warmUp: WarmUp,
    val exercises: List<Exercise>,
    val targets: List<ExerciseTarget>,
    val phase: Phase,
    /** Warm-up item positions still to do, current first. */
    val warmUpQueue: List<Int>,
    /** Slots still to do, current first. */
    val exerciseQueue: List<Int>,
    /** Sets logged per slot, skipped ones included. */
    val setsLogged: List<Int>,
    /** The weight of the latest set per slot today, to prefill the next set. */
    val lastWeights: Map<Int, Int> = emptyMap(),
    /** What the next set of a slot should be, from how its last set felt. */
    val advice: Map<Int, SetAdvice> = emptyMap(),
    /** Every set logged today, oldest first, warm-up sets included, to show what was done so far. */
    val loggedSets: List<SetLog> = emptyList(),
    /** Slots whose warm-up set is done or skipped. */
    val warmUpSetsDone: Set<Int> = emptySet(),
    val rest: Rest? = null,
) {
    val isFinished: Boolean get() = phase == Phase.MAIN && exerciseQueue.isEmpty()

    val currentWarmUp: Int? get() = warmUpQueue.firstOrNull()

    val currentSlot: Int? get() = exerciseQueue.firstOrNull()

    val nextSlot: Int? get() = exerciseQueue.getOrNull(1)

    fun slot(index: Int): WorkoutExercise = workout.exercises[index]

    /** The set number about to be done in [slot], starting at 1. */
    fun setNumber(slot: Int): Int = setsLogged[slot] + 1

    /** The next set of [slot]: today's target, adjusted by how the previous set felt. */
    fun nextSet(slot: Int): ExerciseTarget {
        val target = targets[slot]
        val advice = advice[slot] ?: return lastWeights[slot]?.let { target.copy(weightGrams = it) } ?: target
        return target.copy(
            weightGrams = if (exercises[slot].isWeighted) advice.weightGrams else target.weightGrams,
            target = advice.target,
        )
    }

    /** The working sets logged today for [slot]. */
    fun setsFor(slot: Int): List<SetLog> =
        loggedSets.filter { it.exerciseId == workout.exercises[slot].exerciseId && !it.warmUp }

    /** The warm-up set logged today for [slot], if it was done. */
    fun warmUpSetFor(slot: Int): SetLog? =
        loggedSets.lastOrNull { it.exerciseId == workout.exercises[slot].exerciseId && it.warmUp }

    /** Weighted exercises start with a lighter warm-up set, unless it was done or skipped. */
    fun needsWarmUpSet(slot: Int): Boolean =
        exercises[slot].isWeighted && slot !in warmUpSetsDone && setsLogged[slot] == 0

    /** Fraction of all sets logged, for the progress bar. */
    val progress: Float
        get() {
            val total = workout.exercises.sumOf { it.sets }
            return if (total == 0) 0f else setsLogged.sum().toFloat() / total
        }

    /** How many exercises have all their sets logged. */
    val exercisesDone: Int get() = workout.exercises.indices.count { setsLogged[it] >= slot(it).sets }
}