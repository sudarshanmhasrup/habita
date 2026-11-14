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

    /** The completed sets at the working weight. */
    val workingSets: List<SetLog> get() = completed.filter { it.weightGrams == workingWeight }

    /** Reps or seconds done at the working weight, set by set. */
    val workingResults: List<Int> get() = workingSets.mapNotNull { it.achieved }

    /** Every working set was rated, and every one as [effort]. */
    fun allFelt(effort: Effort): Boolean = workingSets.isNotEmpty() && workingSets.all { it.effort == effort }

    val target: Int get() = sets.maxOf { it.target }

    fun hitAll(sets: Int, target: Int): Boolean = workingResults.count { it >= target } >= sets
}

@Serializable
enum class TargetReason {
    /** No history yet: pick a comfortable weight. */
    FIRST_TIME,

    /** Every set hit the target last time: go up one step. */
    INCREASE,

    /** Every set hit the target and felt easy: go up two steps. */
    INCREASE_EASY,

    /** Missed the target, but every set felt easy: go up one step anyway. */
    FELT_EASY,

    /** Every set hit the target but felt hard: same again before going up. */
    HOLD_HARD,

    /** Some sets missed the target: same weight, beat last time's reps. */
    REPEAT,

    /** Reps fell too low after a weight jump: back to the previous weight. */
    DROP_BACK,

    /** No progress for several sessions: about 10% lighter, then build back up. */
    DELOAD,

    /** Long break since last time: same weight, no increase. */
    RETURN_AFTER_BREAK,

    /** Over eight weeks off: about 20% lighter, to build back up safely. */
    RETURN_AFTER_LONG_BREAK,
    ;

    /** Whether today's target is above last time's. */
    val isIncrease: Boolean get() = this == INCREASE || this == INCREASE_EASY || this == FELT_EASY
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
    const val LONG_BREAK_MILLIS = 56L * 24 * 60 * 60 * 1000
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
            val hitAll = last.hitAll(slot.sets, lastTarget)
            return when {
                now - last.performedAt > LONG_BREAK_MILLIS ->
                    result(0, maxOf(minOf(slot.target, lastTarget), lastTarget * 4 / 5), TargetReason.RETURN_AFTER_LONG_BREAK)
                now - last.performedAt > BREAK_MILLIS -> result(0, lastTarget, TargetReason.RETURN_AFTER_BREAK)
                hitAll && last.allFelt(Effort.EASY) -> result(0, lastTarget + step * 2, TargetReason.INCREASE_EASY)
                hitAll && last.allFelt(Effort.HARD) -> result(0, lastTarget, TargetReason.HOLD_HARD)
                hitAll -> result(0, lastTarget + step, TargetReason.INCREASE)
                last.allFelt(Effort.EASY) -> result(0, lastTarget + step, TargetReason.FELT_EASY)
                else -> result(0, lastTarget, TargetReason.REPEAT)
            }
        }

        val weight = last.workingWeight
        if (now - last.performedAt > LONG_BREAK_MILLIS) {
            // About 20% lighter, in one go, rounded up to whole steps.
            return result(lighter(weight, step, percent = 20), slot.target, TargetReason.RETURN_AFTER_LONG_BREAK)
        }
        if (now - last.performedAt > BREAK_MILLIS) {
            return result(weight, slot.target, TargetReason.RETURN_AFTER_BREAK)
        }
        if (last.hitAll(slot.sets, slot.target)) {
            return when {
                last.allFelt(Effort.EASY) -> result(weight + step * 2, slot.target, TargetReason.INCREASE_EASY)
                last.allFelt(Effort.HARD) -> result(weight, slot.target, TargetReason.HOLD_HARD)
                else -> result(weight + step, slot.target, TargetReason.INCREASE)
            }
        }
        if (last.allFelt(Effort.EASY)) {
            return result(weight + step, slot.target, TargetReason.FELT_EASY)
        }
        val tooHeavy = last.workingResults.any { it < floor(slot.target) }
        // Back to a weight that held: a lighter one done well that same day before going up to the
        // working weight, or the weight from the session before when last time was a jump up from
        // it. Lighter sets after the working weight are back-off sets, often the app's own advice
        // after a hard set, so they never count.
        val inOrder = last.sets.sortedBy { it.setNumber }
        val firstAtWeight = inOrder.indexOfFirst { !it.isSkipped && it.weightGrams == weight }
        val heldToday = inOrder.take(maxOf(firstAtWeight, 0))
            .filter { !it.isSkipped && it.weightGrams < weight && it.achieved!! >= floor(slot.target) }
            .maxOfOrNull { it.weightGrams }
        val beforeJump = done.getOrNull(1)?.workingWeight?.takeIf { it < weight }
        val dropTo = heldToday ?: beforeJump
        if (tooHeavy && dropTo != null) {
            return result(dropTo, slot.target, TargetReason.DROP_BACK)
        }
        // A weight already at one step can't go lighter: it just repeats.
        if (isStalled(done.take(STALL_SESSIONS), slot) && deload(weight, step) < weight) {
            return result(deload(weight, step), slot.target, TargetReason.DELOAD)
        }
        return result(weight, slot.target, TargetReason.REPEAT)
    }

    /**
     * What to do on the next set of the same exercise today, from how the set just done went.
     * Easy moves the weight up (two steps when the reps were well over the target), hard with too
     * few reps moves it down, anything else keeps it. Bodyweight exercises change the target
     * instead of the weight.
     */
    fun nextSet(exercise: Exercise, target: Int, step: Int, done: SetLog): SetAdvice? {
        val achieved = done.achieved ?: return null
        val effort = done.effort ?: return null
        val change = when {
            effort == Effort.EASY && achieved >= target + 3 -> 2
            effort == Effort.EASY -> 1
            effort == Effort.HARD && achieved < floor(target) -> -1
            else -> 0
        }
        val reason = when {
            change > 0 -> SetAdviceReason.EASY
            change < 0 -> SetAdviceReason.TOO_HARD
            else -> SetAdviceReason.KEEP
        }
        return if (exercise.isWeighted) {
            SetAdvice(
                weightGrams = maxOf(done.weightGrams + change * step, 0),
                target = target,
                previousWeightGrams = done.weightGrams,
                reason = reason,
            )
        } else {
            SetAdvice(
                weightGrams = 0,
                target = maxOf(target + change * step, 1),
                previousWeightGrams = 0,
                reason = reason,
            )
        }
    }

    /**
     * The weight for warm-up set [number] before [workingGrams], rounded to the nearest [step] and
     * never less than one step: about half for the first, about three quarters for the second.
     */
    fun warmUpWeight(workingGrams: Int, step: Int, number: Int = 1): Int {
        val light = if (number >= 2) workingGrams * 3 / 4 else workingGrams / 2
        if (step <= 0) return light
        val steps = (light + step / 2) / step
        return maxOf(steps, 1) * step
    }

    /**
     * How many warm-up sets come before [workingGrams] on [equipment]: one, or two once the weight
     * is heavy (over 20 kg a dumbbell, over 40 kg on a machine or cable), where the jump from half weight
     * straight to the working weight would be too big.
     */
    fun warmUpSets(workingGrams: Int?, equipment: Equipment): Int {
        if (equipment == Equipment.BODYWEIGHT) return 0
        val heavy = if (equipment == Equipment.DUMBBELL) HEAVY_DUMBBELL_GRAMS else HEAVY_GRAMS
        return if (workingGrams != null && workingGrams > heavy) 2 else 1
    }

    /** Only the day's first exercises get warm-up sets; the rest start warm. */
    const val WARM_UP_SET_EXERCISES = 2

    private const val HEAVY_DUMBBELL_GRAMS = 20_000
    private const val HEAVY_GRAMS = 40_000

    const val WARM_UP_REPS = 10

    /** The fewest reps that still count as the right weight: 8 for a target of 12. */
    fun floor(target: Int): Int = (target * 2 + 2) / 3

    /** Removes about 10% of [weight], rounded up to whole steps, never below one step. */
    fun deload(weight: Int, step: Int): Int = lighter(weight, step, percent = 10)

    /**
     * Removes [percent] of [weight], rounded up to whole steps. It never goes below one step, the
     * lightest weight there is, so a light exercise is never set to nothing.
     */
    fun lighter(weight: Int, step: Int, percent: Int): Int {
        if (step <= 0) return weight * (100 - percent) / 100
        if (weight <= step) return weight
        val steps = (weight.toLong() * percent + 100L * step - 1) / (100L * step)
        return maxOf(weight - (steps * step).toInt(), step)
    }

    private fun isStalled(recent: List<Performance>, slot: WorkoutExercise): Boolean {
        if (recent.size < STALL_SESSIONS) return false
        if (recent.map { it.workingWeight }.distinct().size != 1) return false
        if (recent.any { it.hitAll(slot.sets, slot.target) }) return false
        return recent.first().workingResults.sum() <= recent.last().workingResults.sum()
    }
}

@Serializable
enum class SetAdviceReason { EASY, KEEP, TOO_HARD }

/**
 * The suggestion for the next set today. [previousWeightGrams] is the weight just used, so the app
 * can offer to keep it.
 */
@Serializable
data class SetAdvice(
    val weightGrams: Int,
    val target: Int,
    val previousWeightGrams: Int,
    val reason: SetAdviceReason,
)

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
            .filter { it.exerciseId == exerciseId && !it.deleted && !it.warmUp && it.sessionId in liveSessions }
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