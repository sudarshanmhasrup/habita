package dev.habita.app.shared.gym

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * One day's body measurements. [day] is the local date as `2026-11-07` and is the record id, so a
 * second weigh-in the same day replaces the first. Either value can be missing: weight is logged
 * most mornings, waist about once a week.
 */
@Serializable
data class BodyLog(
    val day: String,
    val weightGrams: Int? = null,
    val waistMm: Int? = null,
    val updatedAt: Long,
    val deleted: Boolean = false,
    /** When the weight was last set or cleared, so two devices merge it on its own. */
    val weightAt: Long? = null,
    /** When the waist was last set or cleared. */
    val waistAt: Long? = null,
)

/** A gym choice that syncs, by [key], such as [GOAL_KEY]. */
@Serializable
data class GymSetting(val key: String, val value: String, val updatedAt: Long) {
    companion object {
        /** The diet goal, a [BodyGoal] name. */
        const val GOAL_KEY = "body.goal"
    }
}

/** What the diet is for right now. The advice follows it. */
@Serializable
enum class BodyGoal { LOSE_FAT, MAINTAIN, BUILD_MUSCLE }

/** What to change in the diet, from the weekly averages and the [BodyGoal]. */
enum class BodyAdvice {
    /** Fewer than two weeks of weigh-ins: no advice yet. */
    NEED_MORE_DATA,

    /** Two weeks of data, and this week didn't go down: one week can be water, wait. */
    TOO_EARLY,

    /** Losing at a steady pace: keep eating the same. */
    ON_TRACK,

    /** Losing more than 1% of bodyweight a week: eat a little more to keep muscle. */
    TOO_FAST,

    /** Flat for two weeks: eat a little less or move more. */
    STALLED,

    /** Flat for two weeks, but the waist is going down: muscle is replacing fat, keep going. */
    RECOMP,

    /** Maintaining, but up over half a kilo in two weeks: eat a little less. */
    DRIFTING_UP,

    /** Maintaining, but down over half a kilo in two weeks: eat a little more. */
    DRIFTING_DOWN,

    /** Building muscle, but under 0.2 kg gained in two weeks: eat a little more. */
    NOT_GAINING,

    /** Building muscle, but gaining over 0.5% a week, which is mostly fat: eat a little less. */
    GAINING_FAST,
}

/** The average of one week's weigh-ins. Week 0 is the last 7 days, week 1 the 7 before, and so on. */
data class WeekAverage(val weeksAgo: Int, val grams: Int, val weighIns: Int)

data class BodyTrend(
    val today: BodyLog?,
    val latestWeight: BodyLog?,
    val latestWaist: BodyLog?,
    /** Newest first, only weeks with enough weigh-ins. */
    val weeks: List<WeekAverage>,
    /** This week's average minus last week's, in grams. */
    val weeklyChangeGrams: Int?,
    /** How much the waist went down over the last four weeks, in mm, or `null` without two readings. */
    val waistChangeMm: Int?,
    val advice: BodyAdvice,
    val goal: BodyGoal = BodyGoal.LOSE_FAT,
)

object Body {
    /** A week counts once it has this many weigh-ins, so one odd morning can't swing it. */
    const val MIN_WEIGH_INS = 3

    /** Under this much loss in two weeks counts as flat. */
    const val FLAT_GRAMS = 200

    /** Waist down by at least this much over four weeks counts as shrinking. */
    const val WAIST_DOWN_MM = 5

    const val TREND_WEEKS = 8

    /** Maintaining: more than this either way in two weeks is drifting. */
    const val DRIFT_GRAMS = 500

    /** Building: less than this gained in two weeks is not gaining. */
    const val MIN_GAIN_GRAMS = 200

    fun trend(logs: List<BodyLog>, today: String, goal: BodyGoal = BodyGoal.LOSE_FAT): BodyTrend {
        val live = logs.filter { !it.deleted }
        val todayEpoch = epochDay(today)
        fun weeksAgo(log: BodyLog) = ((todayEpoch - epochDay(log.day)) / 7).toInt()
        val weights = live.filter { it.weightGrams != null && epochDay(it.day) <= todayEpoch }
        val weeks = weights
            .groupBy(::weeksAgo)
            .filter { (ago, days) -> ago < TREND_WEEKS && days.size >= MIN_WEIGH_INS }
            .map { (ago, days) -> WeekAverage(ago, days.sumOf { it.weightGrams!! } / days.size, days.size) }
            .sortedBy { it.weeksAgo }
        val byWeek = weeks.associateBy { it.weeksAgo }
        val w0 = byWeek[0]?.grams
        val w1 = byWeek[1]?.grams
        val w2 = byWeek[2]?.grams
        val change = if (w0 != null && w1 != null) w0 - w1 else null

        val waists = live
            .filter { it.waistMm != null && epochDay(it.day) in todayEpoch - 27..todayEpoch }
            .sortedBy { epochDay(it.day) }
        val waistChange = if (waists.size >= 2 && epochDay(waists.last().day) - epochDay(waists.first().day) >= 14) {
            waists.first().waistMm!! - waists.last().waistMm!!
        } else {
            null
        }

        val advice = when {
            w0 == null || w1 == null -> BodyAdvice.NEED_MORE_DATA
            goal == BodyGoal.MAINTAIN -> maintainAdvice(w0, w1, w2)
            goal == BodyGoal.BUILD_MUSCLE -> buildAdvice(w0, w1, w2)
            change!! < 0 && -change * 100 > w1 -> BodyAdvice.TOO_FAST
            w2 == null -> if (change < 0) BodyAdvice.ON_TRACK else BodyAdvice.TOO_EARLY
            w0 > w2 - FLAT_GRAMS ->
                if (waistChange != null && waistChange >= WAIST_DOWN_MM) BodyAdvice.RECOMP else BodyAdvice.STALLED
            else -> BodyAdvice.ON_TRACK
        }

        return BodyTrend(
            today = live.firstOrNull { it.day == today },
            latestWeight = weights.maxByOrNull { epochDay(it.day) },
            latestWaist = live.filter { it.waistMm != null && epochDay(it.day) <= todayEpoch }.maxByOrNull { epochDay(it.day) },
            weeks = weeks,
            weeklyChangeGrams = change,
            waistChangeMm = waistChange,
            advice = advice,
            goal = goal,
        )
    }

    private fun maintainAdvice(w0: Int, w1: Int, w2: Int?): BodyAdvice = when {
        w2 == null -> if (abs(w0 - w1) <= DRIFT_GRAMS) BodyAdvice.ON_TRACK else BodyAdvice.TOO_EARLY
        w0 - w2 > DRIFT_GRAMS -> BodyAdvice.DRIFTING_UP
        w2 - w0 > DRIFT_GRAMS -> BodyAdvice.DRIFTING_DOWN
        else -> BodyAdvice.ON_TRACK
    }

    private fun buildAdvice(w0: Int, w1: Int, w2: Int?): BodyAdvice = when {
        (w0 - w1) * 200 > w1 -> BodyAdvice.GAINING_FAST
        w2 == null -> if (w0 > w1) BodyAdvice.ON_TRACK else BodyAdvice.TOO_EARLY
        w0 - w2 < MIN_GAIN_GRAMS -> BodyAdvice.NOT_GAINING
        else -> BodyAdvice.ON_TRACK
    }

    /**
     * [existing] with [weightGrams] and [waistMm] set at [now]. A value that changed, cleared ones
     * included, gets [now] as its time; one that didn't keeps its time, and a value that was never
     * set claims nothing, so it can't wipe one logged on another device.
     */
    fun update(existing: BodyLog?, day: String, weightGrams: Int?, waistMm: Int?, now: Long): BodyLog {
        val live = existing?.takeIf { !it.deleted }
        fun at(value: Int?, old: Int?, oldAt: Long?): Long? = when {
            value == old && live != null -> oldAt ?: live.updatedAt.takeIf { old != null }
            value == null && old == null -> null
            else -> now
        }
        return BodyLog(
            day = day,
            weightGrams = weightGrams,
            waistMm = waistMm,
            // Always after the copy it changes, even on a device whose clock is behind, so the
            // change wins wherever it goes.
            updatedAt = maxOf(now, (existing?.updatedAt ?: Long.MIN_VALUE) + 1),
            weightAt = at(weightGrams, live?.weightGrams, live?.weightAt),
            waistAt = at(waistMm, live?.waistMm, live?.waistAt),
        )
    }

    /**
     * Two copies of the same day, merged value by value: each of weight and waist comes from the
     * copy that set it last, so a weight from the phone and a waist from the desktop both survive.
     * A deletion newer than both wins whole. The result is the same whichever side merges, and it
     * carries a later time than both when it differs from each, so it spreads to every device.
     */
    fun merge(a: BodyLog, b: BodyLog): BodyLog {
        val (older, newer) = if (b.updatedAt > a.updatedAt || (b.updatedAt == a.updatedAt && b.toString() > a.toString())) a to b else b to a
        // A record from before value times, from an older app, set all its values, empty ones
        // included, when it was saved. Otherwise a value without its own time claims nothing.
        fun legacy(log: BodyLog) = log.weightAt == null && log.waistAt == null && (log.weightGrams != null || log.waistMm != null)
        fun weightTime(log: BodyLog) = log.weightAt ?: if (log.weightGrams != null || legacy(log)) log.updatedAt else Long.MIN_VALUE
        fun waistTime(log: BodyLog) = log.waistAt ?: if (log.waistMm != null || legacy(log)) log.updatedAt else Long.MIN_VALUE
        if (newer.deleted) return newer
        if (older.deleted) {
            // Values set before the deletion stay deleted; only ones set after it come back.
            val kept = newer.copy(
                weightGrams = newer.weightGrams.takeIf { weightTime(newer) > older.updatedAt },
                weightAt = newer.weightAt.takeIf { weightTime(newer) > older.updatedAt },
                waistMm = newer.waistMm.takeIf { waistTime(newer) > older.updatedAt },
                waistAt = newer.waistAt.takeIf { waistTime(newer) > older.updatedAt },
            )
            return when {
                kept == newer -> newer
                kept.weightGrams == null && kept.waistMm == null -> older.copy(updatedAt = newer.updatedAt + 1)
                else -> kept.copy(updatedAt = newer.updatedAt + 1)
            }
        }
        val weightFrom = if (weightTime(older) > weightTime(newer)) older else newer
        val waistFrom = if (waistTime(older) > waistTime(newer)) older else newer
        val merged = newer.copy(
            weightGrams = weightFrom.weightGrams,
            weightAt = weightTime(weightFrom).takeIf { it != Long.MIN_VALUE },
            waistMm = waistFrom.waistMm,
            waistAt = waistTime(waistFrom).takeIf { it != Long.MIN_VALUE },
        )
        return if (merged == newer || merged == older) merged else merged.copy(updatedAt = newer.updatedAt + 1)
    }

    /** Whether [today] is Sunday and the waist hasn't been measured in the last six days. */
    fun waistDue(logs: List<BodyLog>, today: String): Boolean {
        val todayEpoch = epochDay(today)
        if (dayOfWeek(today) != 6) return false
        return logs.none { !it.deleted && it.waistMm != null && epochDay(it.day) in todayEpoch - 6..todayEpoch }
    }

    /**
     * The next [count] Sundays, from [today] on, that need a waist reminder: a Sunday whose week
     * already has a measurement is skipped. For platforms that queue reminders ahead of time.
     */
    fun waistReminderDays(logs: List<BodyLog>, today: String, count: Int): List<String> {
        val todayEpoch = epochDay(today)
        val firstSunday = todayEpoch + (6 - dayOfWeek(today))
        return generateSequence(firstSunday) { it + 7 }
            .map { dayString(it) }
            .filter { waistDue(logs, it) }
            .take(count)
            .toList()
    }

    /** 0 for Monday up to 6 for Sunday. 1970-01-01 was a Thursday. */
    fun dayOfWeek(day: String): Int = ((epochDay(day) + 3) % 7 + 7).toInt() % 7

    /**
     * Days since 1970-01-01 for a date written as `2026-11-07`, so week windows need no time zone
     * or calendar library.
     */
    fun epochDay(day: String): Long {
        val (y, m, d) = day.split('-').map { it.toInt() }
        // Days from civil, from Howard Hinnant's date algorithms.
        val year = if (m <= 2) y - 1 else y
        val era = (if (year >= 0) year else year - 399) / 400
        val yearOfEra = year - era * 400
        val dayOfYear = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146097L + dayOfEra - 719468
    }

    /** The date [epochDay] days after 1970-01-01, written as `2026-11-07`. */
    fun dayString(epochDay: Long): String {
        // Civil from days, from Howard Hinnant's date algorithms.
        val z = epochDay + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val dayOfEra = z - era * 146097
        val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val mp = (5 * dayOfYear + 2) / 153
        val d = dayOfYear - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yearOfEra + era * 400 + if (m <= 2) 1 else 0
        return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }

    /** The last [count] days up to [today], newest first. */
    fun lastDays(today: String, count: Int): List<String> {
        val todayEpoch = epochDay(today)
        return (0 until count).map { dayString(todayEpoch - it) }
    }
}