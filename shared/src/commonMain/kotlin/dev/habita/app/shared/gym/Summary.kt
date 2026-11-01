package dev.habita.app.shared.gym

import kotlinx.serialization.Serializable

/**
 * The end-of-workout numbers. Exercise lists hold exercise ids.
 *
 * Volume is weight × reps over weighted exercises, as logged (one dumbbell for dumbbell work), so
 * it is only meaningful compared with itself over time.
 */
@Serializable
data class WorkoutSummary(
    val sessionId: String,
    val totalMillis: Long,
    val warmUpMillis: Long,
    val mainMillis: Long,
    val volumeKg: Double,
    val previousVolumeKg: Double?,
    val volumeChangePercent: Double?,
    val exercisesProgressed: List<String>,
    val personalBests: List<String>,
    val setsCompleted: Int,
    val setsSkipped: Int,
    val nextIncreases: List<String>,
)

object Summary {
    /**
     * Summarises [session] from the full history. An unfinished session is measured up to [now].
     * [steps] holds the step chosen in the app per exercise id.
     */
    fun of(
        session: WorkoutSession,
        program: Program,
        sessions: List<WorkoutSession>,
        sets: List<SetLog>,
        steps: Map<String, Int>,
        now: Long,
    ): WorkoutSummary {
        val end = session.endedAt ?: now
        val mainStart = session.mainStartedAt ?: end
        val sessionSets = sets.filter { it.sessionId == session.id && !it.deleted }
        val previous = sessions
            .filter {
                !it.deleted && it.id != session.id && it.workoutId == session.workoutId &&
                    it.endedAt != null && it.startedAt < session.startedAt
            }
            .maxByOrNull { it.startedAt }

        val volume = volumeKg(sessionSets, program)
        val previousVolume = previous?.let { prev ->
            volumeKg(sets.filter { it.sessionId == prev.id && !it.deleted }, program)
        }

        val progressed = mutableListOf<String>()
        val bests = mutableListOf<String>()
        val increases = mutableListOf<String>()
        val workout = program.workout(session.workoutId)

        sessionSets.map { it.exerciseId }.distinct().forEach { exerciseId ->
            val exercise = program.exercise(exerciseId) ?: return@forEach
            val history = History.performances(exerciseId, sessions, sets)
            val today = history.firstOrNull { it.sessionId == session.id } ?: return@forEach
            val before = history.filter { it.performedAt < today.performedAt && it.hasCompletedSets }
            val slot = workout?.exercises?.firstOrNull { it.exerciseId == exerciseId }

            before.firstOrNull()?.let { last ->
                if (isBetter(today, last, exercise)) progressed += exerciseId
            }
            if (before.isNotEmpty() && today.hasCompletedSets) {
                val best = best(today, exercise, slot)
                if (best != null && before.all { (best(it, exercise, slot) ?: 0) < best }) bests += exerciseId
            }
            if (slot != null) {
                val step = steps[exerciseId] ?: exercise.defaultStep
                val upToToday = history.filter { it.performedAt <= today.performedAt }
                val next = Progression.target(exercise, slot, step, upToToday, today.performedAt)
                if (next.reason == TargetReason.INCREASE) increases += exerciseId
            }
        }

        return WorkoutSummary(
            sessionId = session.id,
            totalMillis = end - session.startedAt,
            warmUpMillis = mainStart - session.startedAt,
            mainMillis = end - mainStart,
            volumeKg = volume,
            previousVolumeKg = previousVolume,
            volumeChangePercent = previousVolume?.takeIf { it > 0 }?.let { (volume - it) / it * 100 },
            exercisesProgressed = progressed,
            personalBests = bests,
            setsCompleted = sessionSets.count { !it.isSkipped },
            setsSkipped = sessionSets.count { it.isSkipped },
            nextIncreases = increases,
        )
    }

    private fun volumeKg(sets: List<SetLog>, program: Program): Double =
        sets
            .filter { !it.isSkipped && program.exercise(it.exerciseId)?.isWeighted == true }
            .sumOf { it.weightGrams.toLong() * it.achieved!! } / 1000.0

    private fun isBetter(today: Performance, last: Performance, exercise: Exercise): Boolean {
        if (!today.hasCompletedSets) return false
        if (!exercise.isWeighted) return today.workingResults.max() > last.workingResults.max()
        return today.workingWeight > last.workingWeight ||
            (today.workingWeight == last.workingWeight && today.workingResults.sum() > last.workingResults.sum())
    }

    /**
     * The number a personal best is measured by: the heaviest weight lifted for the full target,
     * or the longest hold for bodyweight exercises.
     */
    private fun best(performance: Performance, exercise: Exercise, slot: WorkoutExercise?): Int? {
        val completed = performance.sets.filter { !it.isSkipped }
        if (!exercise.isWeighted) return completed.maxOfOrNull { it.achieved!! }
        val target = slot?.target ?: performance.target
        return completed.filter { it.achieved!! >= target }.maxOfOrNull { it.weightGrams }
    }
}