package dev.habita.app.shared.gym

import kotlinx.serialization.Serializable

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
)

/** What to change in the diet, from the weekly averages. The app is cutting or recomping. */
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
)

object Body {
    /** A week counts once it has this many weigh-ins, so one odd morning can't swing it. */
    const val MIN_WEIGH_INS = 3

    /** Under this much loss in two weeks counts as flat. */
    const val FLAT_GRAMS = 200

    /** Waist down by at least this much over four weeks counts as shrinking. */
    const val WAIST_DOWN_MM = 5

    const val TREND_WEEKS = 8

    fun trend(logs: List<BodyLog>, today: String): BodyTrend {
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
            change!! < 0 && -change * 100 > w1 -> BodyAdvice.TOO_FAST
            w2 == null -> if (change < 0) BodyAdvice.ON_TRACK else BodyAdvice.TOO_EARLY
            w0 > w2 - FLAT_GRAMS ->
                if (waistChange != null && waistChange >= WAIST_DOWN_MM) BodyAdvice.RECOMP else BodyAdvice.STALLED
            else -> BodyAdvice.ON_TRACK
        }

        return BodyTrend(
            today = live.firstOrNull { it.day == today },
            latestWeight = weights.maxByOrNull { epochDay(it.day) },
            latestWaist = live.filter { it.waistMm != null }.maxByOrNull { epochDay(it.day) },
            weeks = weeks,
            weeklyChangeGrams = change,
            waistChangeMm = waistChange,
            advice = advice,
        )
    }

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
}