package dev.habita.app.shared.gym

import kotlinx.serialization.Serializable

/**
 * How one exercise went in one session: its sets, at the time of the earliest one.
 */
@Serializable
data class Performance(
    val sessionId: String,
    val performedAt: Long,
    val sets: List<SetLog>,
) {
    private val completed: List<SetLog> get() = sets.filter { !it.isSkipped }

    val hasCompletedSets: Boolean get() = completed.isNotEmpty()

    /** The heaviest weight used, which is the weight progression builds on. */
    val workingWeight: Int get() = completed.maxOfOrNull { it.weightGrams } ?: 0

    /** Reps or seconds done at the working weight, set by set. */
    val workingResults: List<Int> get() = completed.filter { it.weightGrams == workingWeight }.mapNotNull { it.achieved }

    val target: Int get() = sets.maxOf { it.target }

    fun hitAll(sets: Int, target: Int): Boolean = workingResults.count { it >= target } >= sets
}

@Serializable
enum class TargetReason {
    /** No history yet: pick a comfortable weight. */
    FIRST_TIME,

    /** Every set hit the target last time: go up one step. */
    INCREASE,

    /** Some sets missed the target: same weight, beat last time's reps. */
    REPEAT,

    /** Reps fell too low after a weight jump: back to the previous weight. */
    DROP_BACK,

    /** No progress for several sessions: about 10% lighter, then build back up. */
    DELOAD,

    /** Long break since last time: same weight, no increase. */
    RETURN_AFTER_BREAK,
}

/**
 * What to do today. [weightGrams] is `null` only on the first time of a weighted exercise.
 * [target] is reps or seconds per set.
 */
@Serializable
data class ExerciseTarget(
    val exerciseId: String,
    val weightGrams: Int?,
    val target: Int,
    val reason: TargetReason,
    val lastTime: Performance?,
)

/**
 * Double progression. Weighted exercises go up one step once every set hits the target, and
 * otherwise repeat the weight until they do. Bodyweight exercises grow the target instead.
 */
object Progression {
    const val BREAK_MILLIS = 21L * 24 * 60 * 60 * 1000
    const val STALL_SESSIONS = 3

    fun target(
        exercise: Exercise,
        slot: WorkoutExercise,
        step: Int,
        history: List<Performance>,
        now: Long,
    ): ExerciseTarget {
        val done = history.filter { it.hasCompletedSets }.sortedByDescending { it.performedAt }
        val last = done.firstOrNull()
            ?: return ExerciseTarget(
                exerciseId = exercise.id,
                weightGrams = if (exercise.isWeighted) null else 0,
                target = slot.target,
                reason = TargetReason.FIRST_TIME,
                lastTime = null,
            )

        fun result(weight: Int, target: Int, reason: TargetReason) =
            ExerciseTarget(exercise.id, weight, target, reason, last)

        if (!exercise.isWeighted) {
            val lastTarget = maxOf(last.target, slot.target)
            return when {
                now - last.performedAt > BREAK_MILLIS -> result(0, lastTarget, TargetReason.RETURN_AFTER_BREAK)
                last.hitAll(slot.sets, lastTarget) -> result(0, lastTarget + step, TargetReason.INCREASE)
                else -> result(0, lastTarget, TargetReason.REPEAT)
            }
        }

        val weight = last.workingWeight
        if (now - last.performedAt > BREAK_MILLIS) {
            return result(weight, slot.target, TargetReason.RETURN_AFTER_BREAK)
        }
        if (last.hitAll(slot.sets, slot.target)) {
            return result(weight + step, slot.target, TargetReason.INCREASE)
        }
        val tooHeavy = last.workingResults.any { it < floor(slot.target) }
        val previousWeight = done.drop(1).firstOrNull { it.workingWeight < weight }?.workingWeight
        if (tooHeavy && previousWeight != null) {
            return result(previousWeight, slot.target, TargetReason.DROP_BACK)
        }
        if (isStalled(done.take(STALL_SESSIONS), slot)) {
            return result(deload(weight, step), slot.target, TargetReason.DELOAD)
        }
        return result(weight, slot.target, TargetReason.REPEAT)
    }

    /** The fewest reps that still count as the right weight: 8 for a target of 12. */
    fun floor(target: Int): Int = (target * 2 + 2) / 3

    /** Removes about 10% of [weight], rounded up to whole steps. */
    fun deload(weight: Int, step: Int): Int {
        if (step <= 0) return weight * 9 / 10
        val steps = (weight + 10 * step - 1) / (10 * step)
        return maxOf(weight - steps * step, 0)
    }

    private fun isStalled(recent: List<Performance>, slot: WorkoutExercise): Boolean {
        if (recent.size < STALL_SESSIONS) return false
        if (recent.map { it.workingWeight }.distinct().size != 1) return false
        if (recent.any { it.hitAll(slot.sets, slot.target) }) return false
        return recent.first().workingResults.sum() <= recent.last().workingResults.sum()
    }
}

object History {
    /**
     * Every performance of [exerciseId], from sessions that are not deleted.
     */
    fun performances(
        exerciseId: String,
        sessions: List<WorkoutSession>,
        sets: List<SetLog>,
    ): List<Performance> {
        val liveSessions = sessions.filter { !it.deleted }.map { it.id }.toSet()
        return sets
            .filter { it.exerciseId == exerciseId && !it.deleted && it.sessionId in liveSessions }
            .groupBy { it.sessionId }
            .map { (sessionId, sessionSets) ->
                Performance(
                    sessionId = sessionId,
                    performedAt = sessionSets.minOf { it.completedAt },
                    sets = sessionSets.sortedBy { it.setNumber },
                )
            }
            .sortedByDescending { it.performedAt }
    }
}