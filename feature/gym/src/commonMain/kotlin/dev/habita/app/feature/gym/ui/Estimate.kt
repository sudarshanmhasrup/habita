package dev.habita.app.feature.gym.ui

import dev.habita.app.shared.gym.WarmUp
import dev.habita.app.shared.gym.Workout

/** Rough seconds per set of work, before its rest. */
private const val SET_SECONDS = 45

/** Rough seconds per warm-up item; the treadmill walk is longer but the band work is quicker. */
private const val WARM_UP_ITEM_SECONDS = 90

/** About how long [workout] takes, in minutes, rounded to 5. */
internal fun estimateMinutes(workout: Workout, warmUp: WarmUp?): Int {
    val main = workout.exercises.sumOf { it.sets * SET_SECONDS + (it.sets - 1) * it.restSeconds + 60 }
    val warm = (warmUp?.items?.size ?: 0) * WARM_UP_ITEM_SECONDS + (warmUp?.restSeconds ?: 0)
    val minutes = (main + warm) / 60
    return ((minutes + 2) / 5) * 5
}