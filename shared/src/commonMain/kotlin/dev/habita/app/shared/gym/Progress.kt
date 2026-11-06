package dev.habita.app.shared.gym

/** One point of a long-term chart. */
data class ProgressPoint(val at: Long, val value: Int)

/**
 * One exercise over its whole history. [values] are the working weight in grams for weighted
 * exercises, and the best reps or seconds for bodyweight ones, one point per session, oldest first.
 */
data class ExerciseProgress(
    val exercise: Exercise,
    val points: List<ProgressPoint>,
    /** Whether the current plan has it in a workout; retired and swapped-in ones are listed after. */
    val inPlan: Boolean,
) {
    val first: Int get() = points.first().value
    val latest: Int get() = points.last().value
    val best: Int get() = points.maxOf { it.value }
}

/** A week of the body log, for the all-time chart. [weeksAgo] 0 is the last 7 days. */
data class BodyWeek(val weeksAgo: Int, val weightGrams: Int?, val waistMm: Int?)

/** Progress over the whole history, for the progress screen. */
object Progress {
    fun exercises(program: Program, sessions: List<WorkoutSession>, sets: List<SetLog>): List<ExerciseProgress> {
        val planOrder = program.workouts
            .sortedWith(compareBy(nullsLast()) { it.rotation })
            .flatMap { workout -> workout.exercises.map { it.exerciseId } }
            .distinct()
        return sets.map { it.exerciseId }.distinct().mapNotNull { id ->
            val exercise = program.exercise(id) ?: return@mapNotNull null
            val points = History.performances(id, sessions, sets)
                .filter { it.hasCompletedSets }
                .sortedBy { it.performedAt }
                .map { ProgressPoint(it.performedAt, value(it, exercise)) }
            if (points.isEmpty()) null else ExerciseProgress(exercise, points, id in planOrder)
        }.sortedWith(
            compareBy<ExerciseProgress> { !it.inPlan }
                .thenBy { planOrder.indexOf(it.exercise.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                .thenByDescending { it.points.last().at },
        )
    }

    /** Every week since the first log, oldest first: average weight and the latest waist. */
    fun bodyWeeks(logs: List<BodyLog>, today: String): List<BodyWeek> {
        val todayEpoch = Body.epochDay(today)
        return logs.filter { !it.deleted && Body.epochDay(it.day) <= todayEpoch }
            .groupBy { ((todayEpoch - Body.epochDay(it.day)) / 7).toInt() }
            .entries
            .sortedByDescending { it.key }
            .map { (ago, days) ->
                val weights = days.mapNotNull { it.weightGrams }
                BodyWeek(
                    weeksAgo = ago,
                    weightGrams = if (weights.isEmpty()) null else weights.sum() / weights.size,
                    waistMm = days.filter { it.waistMm != null }.maxByOrNull { Body.epochDay(it.day) }?.waistMm,
                )
            }
    }

    private fun value(performance: Performance, exercise: Exercise): Int =
        if (exercise.isWeighted) performance.workingWeight else performance.workingResults.maxOrNull() ?: 0
}