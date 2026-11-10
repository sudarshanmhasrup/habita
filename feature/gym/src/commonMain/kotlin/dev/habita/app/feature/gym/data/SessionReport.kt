package dev.habita.app.feature.gym.data

import dev.habita.app.shared.gym.Effort
import dev.habita.app.shared.gym.Exercise
import dev.habita.app.shared.gym.History
import dev.habita.app.shared.gym.Performance
import dev.habita.app.shared.gym.Program
import dev.habita.app.shared.gym.SetLog
import dev.habita.app.shared.gym.WorkoutSession
import dev.habita.app.shared.gym.WorkoutSummary

/** One value at one point in time, for trend charts. */
data class TrendPoint(val at: Long, val value: Double, val isToday: Boolean)

/**
 * One exercise of a finished workout. [trend] is its working weight in kg (seconds for timed
 * bodyweight work) over the most recent sessions, ending with today's.
 */
data class ExerciseReport(
    val exercise: Exercise,
    val sets: List<SetLog>,
    val warmUpSet: SetLog?,
    val lastTime: Performance?,
    val trend: List<TrendPoint>,
    val progressed: Boolean,
    val personalBest: Boolean,
    val heavierNext: Boolean,
)

/** Everything the summary screen draws: the six numbers plus the data behind its charts. */
data class SessionReport(
    val summary: WorkoutSummary,
    val workoutName: String,
    val exercises: List<ExerciseReport>,
    /** This workout's volume over its recent sessions, ending with today's. */
    val volumeTrend: List<TrendPoint>,
    val efforts: Map<Effort, Int>,
    /** When the workout started, for the date on its photo card. */
    val startedAt: Long = 0,
)

internal object Reports {
    const val TREND_SESSIONS = 8

    fun of(
        summary: WorkoutSummary,
        session: WorkoutSession,
        program: Program,
        sessions: List<WorkoutSession>,
        sets: List<SetLog>,
    ): SessionReport {
        val workout = program.workout(session.workoutId)
        val allToday = sets.filter { it.sessionId == session.id && !it.deleted }
        val todaySets = allToday.filter { !it.warmUp }
        val order = workout?.exercises?.map { it.exerciseId }.orEmpty()
        val exerciseIds = todaySets.map { it.exerciseId }.distinct()
            .sortedBy { id -> order.indexOf(id).takeIf { it >= 0 } ?: Int.MAX_VALUE }

        val exercises = exerciseIds.mapNotNull { id ->
            val exercise = program.exercise(id) ?: return@mapNotNull null
            val history = History.performances(id, sessions, sets)
                .filter { it.hasCompletedSets || it.sessionId == session.id }
            val today = history.firstOrNull { it.sessionId == session.id }
            val upToToday = history.filter { today == null || it.performedAt <= today.performedAt }
            ExerciseReport(
                exercise = exercise,
                sets = todaySets.filter { it.exerciseId == id }.sortedBy { it.setNumber },
                warmUpSet = allToday.lastOrNull { it.exerciseId == id && it.warmUp },
                lastTime = upToToday.firstOrNull { it.sessionId != session.id && it.hasCompletedSets },
                trend = upToToday
                    .filter { it.hasCompletedSets }
                    .take(TREND_SESSIONS)
                    .reversed()
                    .map { performance ->
                        val value = if (exercise.isWeighted) {
                            performance.workingWeight / 1000.0
                        } else {
                            (performance.workingResults.maxOrNull() ?: 0).toDouble()
                        }
                        TrendPoint(performance.performedAt, value, performance.sessionId == session.id)
                    },
                progressed = id in summary.exercisesProgressed,
                personalBest = id in summary.personalBests,
                heavierNext = id in summary.nextIncreases,
            )
        }

        val volumeTrend = sessions
            .filter {
                !it.deleted && it.workoutId == session.workoutId && it.startedAt <= session.startedAt &&
                    (it.endedAt != null || it.id == session.id)
            }
            .sortedByDescending { it.startedAt }
            .take(TREND_SESSIONS)
            .reversed()
            .map { past ->
                val kg = sets
                    .filter { it.sessionId == past.id && !it.deleted && !it.isSkipped && !it.warmUp }
                    .filter { program.exercise(it.exerciseId)?.isWeighted == true }
                    .sumOf { it.weightGrams.toLong() * it.achieved!! } / 1000.0
                TrendPoint(past.startedAt, kg, past.id == session.id)
            }

        return SessionReport(
            summary = summary,
            workoutName = workout?.name ?: "Workout",
            exercises = exercises,
            volumeTrend = volumeTrend,
            efforts = todaySets.mapNotNull { it.effort }.groupingBy { it }.eachCount(),
            startedAt = session.startedAt,
        )
    }
}