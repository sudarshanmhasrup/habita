package dev.habita.app.shared.gym

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * A plan review: the check-in text was copied for an AI at [at]. Only the latest one matters; it
 * decides when the next is due.
 */
@Serializable
data class CheckIn(
    val id: String,
    val at: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** An exercise with no new best for [CheckIns.WINDOW_MILLIS], over [sessions] sessions. */
data class Plateau(val exerciseId: String, val sessions: Int)

enum class CheckInReason {
    /** About three months since the last check-in, or since the first workout. */
    SCHEDULED,

    /** Some exercise stopped improving. */
    PLATEAU,
}

/** Everything the check-in text is written from. */
data class CheckInData(
    val program: Program,
    val sessions: List<WorkoutSession>,
    val sets: List<SetLog>,
    val body: List<BodyLog>,
    /** Exercises swapped at this gym, by the planned exercise they replace. */
    val swaps: Map<String, String>,
    val lastCheckIn: Long?,
    val now: Long,
    /** Today's local date, `2026-11-07`. */
    val today: String,
    /** A date for people, such as "7 Nov 2026". */
    val formatDate: (Long) -> String,
    /** The diet goal picked in the app. */
    val goal: BodyGoal = BodyGoal.LOSE_FAT,
)

/**
 * When the plan needs a look, and the text to paste into an AI chat for it. The app never talks to
 * an AI itself: the text carries the data and the plan, and asks for changes in the plan's format.
 */
object CheckIns {
    private const val DAY = 24L * 60 * 60 * 1000
    const val WINDOW_MILLIS = 42 * DAY
    const val MIN_SESSIONS = 4
    const val EVERY_MILLIS = 84 * DAY

    /** After a check-in, a plateau waits this long before asking again, so changes get a chance. */
    const val QUIET_MILLIS = 28 * DAY

    /**
     * Exercises done at least [MIN_SESSIONS] times in the last six weeks, with history before
     * that, whose best in those six weeks is no better than before.
     */
    fun plateaus(program: Program, sessions: List<WorkoutSession>, sets: List<SetLog>, now: Long): List<Plateau> {
        val windowStart = now - WINDOW_MILLIS
        return sets.map { it.exerciseId }.distinct().mapNotNull { id ->
            val exercise = program.exercise(id) ?: return@mapNotNull null
            val done = History.performances(id, sessions, sets).filter { it.hasCompletedSets }
            val before = done.filter { it.performedAt < windowStart }
            val recent = done.filter { it.performedAt >= windowStart }
            if (before.isEmpty() || recent.size < MIN_SESSIONS) return@mapNotNull null
            val bestBefore = before.maxOf { best(it, exercise) }
            val bestRecent = recent.maxOf { best(it, exercise) }
            if (bestRecent > bestBefore) null else Plateau(id, recent.size)
        }
    }

    fun due(lastCheckIn: Long?, sessions: List<WorkoutSession>, plateaus: List<Plateau>, now: Long): CheckInReason? {
        val firstWorkout = sessions.filter { !it.deleted && it.endedAt != null }.minOfOrNull { it.startedAt } ?: return null
        val since = lastCheckIn ?: firstWorkout
        return when {
            now - since >= EVERY_MILLIS -> CheckInReason.SCHEDULED
            plateaus.isNotEmpty() && (lastCheckIn == null || now - lastCheckIn >= QUIET_MILLIS) -> CheckInReason.PLATEAU
            else -> null
        }
    }

    /**
     * The best of one session: the heaviest weight lifted for a set's full target, or the most
     * reps or seconds for bodyweight work. `0` when no set reached its target.
     */
    fun best(performance: Performance, exercise: Exercise): Int {
        val completed = performance.sets.filter { !it.isSkipped }
        return if (exercise.isWeighted) {
            completed.filter { it.achieved!! >= it.target }.maxOfOrNull { it.weightGrams } ?: 0
        } else {
            completed.maxOfOrNull { it.achieved!! } ?: 0
        }
    }

    fun text(data: CheckInData): String {
        val program = data.program
        val liveSessions = data.sessions.filter { !it.deleted }
        val finished = liveSessions.filter { it.endedAt != null }
        val from = data.lastCheckIn ?: finished.minOfOrNull { it.startedAt } ?: data.now
        val periodSessions = liveSessions.filter { it.startedAt >= from }
        val periodIds = periodSessions.map { it.id }.toSet()
        val periodSets = data.sets.filter { it.sessionId in periodIds && !it.deleted && !it.warmUp }
        val weeks = ((data.now - from) / (7 * DAY)).toInt().coerceAtLeast(1)
        val plateaus = plateaus(program, data.sessions, data.sets, data.now)

        return buildString {
            append("# Habita check-in, ${data.formatDate(data.now)}\n\n")
            append("I train on my own at local gyms, with no coach, and log every set in my workout app. ")
            append("Below is what it recorded")
            append(if (data.lastCheckIn != null) " since my last check-in" else "")
            append(", then my current plan. My goal and diet targets are in this chat's instructions; ")
            append("if you can't see them, ask me for them before answering.\n\n")
            append("Please:\n\n")
            append("1. Say in plain words what's working and what isn't.\n")
            append("2. Suggest changes for the next 12 weeks, only where the data supports them: exercises to ")
            append("replace, sets, rep targets or rest times. No change is a fine answer.\n")
            append("3. If you change the plan, reply with the complete updated tables in exactly the format ")
            append("of \"Current plan\" below. Keep every existing id, since my history is stored by id. A new ")
            append("exercise needs a new lowercase-kebab-case id. Never delete a row from Exercises: to stop doing an ")
            append("exercise, take it out of the workouts and leave its row. Step is in kg for weighted exercises.\n\n")

            append("## Summary\n\n")
            val finishedInPeriod = periodSessions.count { it.endedAt != null }
            append("- Period: ${data.formatDate(from)} to ${data.formatDate(data.now)} ($weeks ${if (weeks == 1) "week" else "weeks"})\n")
            append("- Workouts finished: $finishedInPeriod (${oneDecimal(finishedInPeriod.toDouble() / weeks)} a week)")
            val unfinished = periodSessions.count { it.endedAt == null }
            if (unfinished > 0) append(", plus $unfinished started and not finished")
            append("\n")
            append("- Diet goal set in the app: ${data.goal.name.lowercase().replace('_', ' ')}\n")
            append("- Sets done: ${periodSets.count { !it.isSkipped }}, skipped: ${periodSets.count { it.isSkipped }}\n")
            val rated = periodSets.mapNotNull { it.effort }
            if (rated.isNotEmpty()) {
                append("- How sets felt: ")
                append(Effort.entries.joinToString(", ") { effort -> "${effort.name.lowercase()} ${percent(rated.count { it == effort }, rated.size)}" })
                append("\n")
            }

            if (plateaus.isNotEmpty()) {
                append("\n## Stuck exercises\n\n")
                plateaus.forEach {
                    append("- ${program.exercise(it.exerciseId)?.name}: no new best in 6 weeks, over ${it.sessions} sessions\n")
                }
            }

            append("\n## Exercises\n\n")
            append("Weights are per dumbbell for dumbbell exercises. First and latest show the sets at the working weight.\n\n")
            append("| Exercise | Sessions | First | Latest | Best | Easy / medium / hard | Skipped sets |\n")
            append("|---|---|---|---|---|---|---|\n")
            periodSets.map { it.exerciseId }.distinct().forEach { id ->
                val exercise = program.exercise(id) ?: return@forEach
                val history = History.performances(id, periodSessions, periodSets).filter { it.hasCompletedSets }
                if (history.isEmpty()) return@forEach
                val own = periodSets.filter { it.exerciseId == id }
                val efforts = Effort.entries.map { effort -> own.count { it.effort == effort } }
                val best = history.maxOf { best(it, exercise) }
                append("| ${exercise.name} | ${history.size} | ${describe(history.last(), exercise)} | ")
                append("${describe(history.first(), exercise)} | ${if (best == 0) "–" else amount(best, exercise)} | ")
                append("${efforts.joinToString(" / ")} | ${own.count { it.isSkipped }} |\n")
            }

            val body = bodyWeeks(data.body, data.today, weeks)
            if (body.isNotEmpty()) {
                append("\n## Bodyweight\n\n")
                append("Weekly averages of morning weigh-ins, newest first.\n\n")
                append("| Weeks ago | Average | Weigh-ins | Waist |\n")
                append("|---|---|---|---|\n")
                body.forEach { (ago, average, count, waist) ->
                    append("| $ago | ${average?.let { "${oneDecimal(it / 1000.0)} kg" } ?: "–"} | $count | ")
                    append("${waist?.let { "${oneDecimal(it / 10.0)} cm" } ?: "–"} |\n")
                }
            }

            if (data.swaps.isNotEmpty()) {
                append("\n## Swapped at my current gym\n\n")
                data.swaps.forEach { (planned, replacement) ->
                    append("- ${program.exercise(planned)?.name ?: planned} is done as ${program.exercise(replacement)?.name ?: replacement}\n")
                }
            }

            append("\n## Current plan\n\n")
            append(ProgramWriter.write(program))
        }
    }

    private data class BodyWeek(val weeksAgo: Int, val averageGrams: Int?, val weighIns: Int, val waistMm: Int?)

    private fun bodyWeeks(logs: List<BodyLog>, today: String, weeks: Int): List<BodyWeek> {
        val todayEpoch = Body.epochDay(today)
        return logs.filter { !it.deleted }
            .groupBy { ((todayEpoch - Body.epochDay(it.day)) / 7).toInt() }
            .filterKeys { it in 0..weeks }
            .entries
            .sortedBy { it.key }
            .map { (ago, days) ->
                val weights = days.mapNotNull { it.weightGrams }
                BodyWeek(
                    weeksAgo = ago,
                    averageGrams = if (weights.isEmpty()) null else weights.sum() / weights.size,
                    weighIns = weights.size,
                    waistMm = days.filter { it.waistMm != null }.maxByOrNull { Body.epochDay(it.day) }?.waistMm,
                )
            }
    }

    /** "20 kg × 12, 12, 10" or "30, 35, 35 s". */
    private fun describe(performance: Performance, exercise: Exercise): String {
        val results = performance.workingResults.joinToString(", ")
        return if (exercise.isWeighted) {
            "${ProgramWriter.kilograms(performance.workingWeight)} kg × $results"
        } else {
            results + if (exercise.measure == Measure.SECONDS) " s" else " reps"
        }
    }

    private fun amount(best: Int, exercise: Exercise): String = when {
        exercise.isWeighted -> "${ProgramWriter.kilograms(best)} kg for full reps"
        exercise.measure == Measure.SECONDS -> "$best s"
        else -> "$best reps"
    }

    private fun oneDecimal(value: Double): String {
        val tenths = (value * 10).roundToInt()
        return "${tenths / 10}.${tenths % 10}"
    }

    private fun percent(part: Int, total: Int): String = "${(part * 100.0 / total).roundToInt()}%"
}