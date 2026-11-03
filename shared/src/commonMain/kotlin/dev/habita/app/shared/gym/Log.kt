package dev.habita.app.shared.gym

import kotlinx.serialization.Serializable

/*
 * Raw workout logs. Ids are created on the device, times are epoch milliseconds, and [updatedAt]
 * decides which copy wins when the same record is synced twice. Records are never removed, only
 * marked [deleted], so a deletion syncs like any other change.
 */

@Serializable
data class WorkoutSession(
    val id: String,
    val workoutId: String,
    val programVersion: Long,
    val startedAt: Long,
    val mainStartedAt: Long? = null,
    val endedAt: Long? = null,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** How a set felt, picked when it is logged. It steers the next set and the next session. */
@Serializable
enum class Effort { EASY, MEDIUM, HARD }

/**
 * One set. [weightGrams] is the weight of one dumbbell for dumbbell exercises and `0` for
 * bodyweight ones. [target] is what the app asked for; [achieved] is what was done, in reps or
 * seconds, and `null` when the set was skipped. [effort] is how it felt, `null` when skipped or
 * logged before effort existed. A [warmUp] set is the lighter set before the working sets: it is
 * kept in the log but never counts toward progression, volume or bests.
 */
@Serializable
data class SetLog(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val setNumber: Int,
    val weightGrams: Int,
    val target: Int,
    val achieved: Int?,
    val completedAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val effort: Effort? = null,
    val warmUp: Boolean = false,
) {
    val isSkipped: Boolean get() = achieved == null
}

@Serializable
enum class WarmUpStatus { DONE, SKIPPED }

/**
 * The outcome of one warm-up item, by its [position] in the warm-up.
 */
@Serializable
data class WarmUpLog(
    val id: String,
    val sessionId: String,
    val position: Int,
    val status: WarmUpStatus,
    val completedAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/**
 * The progression step chosen in the app for an exercise, replacing its default from the program.
 */
@Serializable
data class ExerciseStep(
    val exerciseId: String,
    val step: Int,
    val updatedAt: Long,
)