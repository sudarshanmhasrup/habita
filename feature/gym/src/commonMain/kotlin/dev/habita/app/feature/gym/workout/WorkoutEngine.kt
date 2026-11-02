package dev.habita.app.feature.gym.workout

import dev.habita.app.shared.gym.Exercise
import dev.habita.app.shared.gym.ExerciseTarget
import dev.habita.app.shared.gym.SetLog
import dev.habita.app.shared.gym.WarmUp
import dev.habita.app.shared.gym.WarmUpLog
import dev.habita.app.shared.gym.WarmUpStatus
import dev.habita.app.shared.gym.Workout

/**
 * The workout flow as pure functions: each step takes the current [ActiveWorkout] and returns the
 * next one, plus the record to save. Saving is left to the caller.
 */
object WorkoutEngine {
    fun start(
        sessionId: String,
        workout: Workout,
        warmUp: WarmUp,
        exercises: List<Exercise>,
        targets: List<ExerciseTarget>,
        now: Long,
    ): ActiveWorkout {
        require(exercises.size == workout.exercises.size && targets.size == workout.exercises.size) {
            "Exercises and targets must match the workout's slots"
        }
        val noWarmUp = warmUp.items.isEmpty()
        return ActiveWorkout(
            sessionId = sessionId,
            startedAt = now,
            workout = workout,
            warmUp = warmUp,
            exercises = exercises,
            targets = targets,
            phase = if (noWarmUp) Phase.BREAK else Phase.WARM_UP,
            warmUpQueue = warmUp.items.indices.toList(),
            exerciseQueue = workout.exercises.indices.toList(),
            setsLogged = List(workout.exercises.size) { 0 },
            rest = if (noWarmUp) breakRest(warmUp, now) else null,
        )
    }

    /** Marks the current warm-up item done or skipped. The last one starts the break. */
    fun completeWarmUp(active: ActiveWorkout, status: WarmUpStatus, id: String, now: Long): Pair<ActiveWorkout, WarmUpLog> {
        check(active.phase == Phase.WARM_UP) { "Not warming up" }
        val position = active.warmUpQueue.first()
        val log = WarmUpLog(
            id = id,
            sessionId = active.sessionId,
            position = position,
            status = status,
            completedAt = now,
            updatedAt = now,
        )
        val queue = active.warmUpQueue.drop(1)
        val next = if (queue.isEmpty()) {
            active.copy(warmUpQueue = queue, phase = Phase.BREAK, rest = breakRest(active.warmUp, now))
        } else {
            active.copy(warmUpQueue = queue)
        }
        return next to log
    }

    /** The machine is taken: the current warm-up item comes back after the next one. */
    fun warmUpMachineTaken(active: ActiveWorkout): ActiveWorkout =
        active.copy(warmUpQueue = moveFirstBehindNext(active.warmUpQueue))

    /** Ends the break and starts the main workout. */
    fun endBreak(active: ActiveWorkout): ActiveWorkout {
        check(active.phase == Phase.BREAK) { "Not on the break" }
        return active.copy(phase = Phase.MAIN, rest = null)
    }

    /**
     * Logs the current set: [achieved] reps or seconds, or `null` when skipped. Starts the set's
     * rest, unless it was the last set of the workout.
     */
    fun logSet(active: ActiveWorkout, weightGrams: Int, achieved: Int?, id: String, now: Long): Pair<ActiveWorkout, SetLog> {
        check(active.phase == Phase.MAIN && active.rest == null) { "No set to log" }
        val slotIndex = active.exerciseQueue.first()
        val slot = active.slot(slotIndex)
        val log = SetLog(
            id = id,
            sessionId = active.sessionId,
            exerciseId = slot.exerciseId,
            setNumber = active.setNumber(slotIndex),
            weightGrams = weightGrams,
            target = active.targets[slotIndex].target,
            achieved = achieved,
            completedAt = now,
            updatedAt = now,
        )
        val setsLogged = active.setsLogged.toMutableList().also { it[slotIndex]++ }
        val queue = if (setsLogged[slotIndex] >= slot.sets) active.exerciseQueue.drop(1) else active.exerciseQueue
        val next = active.copy(
            exerciseQueue = queue,
            setsLogged = setsLogged,
            lastWeights = if (achieved != null) active.lastWeights + (slotIndex to weightGrams) else active.lastWeights,
            rest = if (queue.isEmpty()) null else Rest(now, now + slot.restSeconds * 1000L),
        )
        return next to log
    }

    /** Ends the rest between sets early or on time. */
    fun endRest(active: ActiveWorkout): ActiveWorkout = active.copy(rest = null)

    /** The machine is taken: the current exercise comes back after the next one, keeping its sets. */
    fun exerciseMachineTaken(active: ActiveWorkout): ActiveWorkout =
        active.copy(exerciseQueue = moveFirstBehindNext(active.exerciseQueue))

    /** Replaces the targets of slots that have no sets yet, for example after a step change. */
    fun updateTargets(active: ActiveWorkout, targets: Map<Int, ExerciseTarget>): ActiveWorkout =
        active.copy(
            targets = active.targets.mapIndexed { slot, target ->
                targets[slot]?.takeIf { active.setsLogged[slot] == 0 } ?: target
            },
        )

    private fun breakRest(warmUp: WarmUp, now: Long) = Rest(now, now + warmUp.restSeconds * 1000L)

    private fun moveFirstBehindNext(queue: List<Int>): List<Int> =
        if (queue.size < 2) queue else listOf(queue[1], queue[0]) + queue.drop(2)
}