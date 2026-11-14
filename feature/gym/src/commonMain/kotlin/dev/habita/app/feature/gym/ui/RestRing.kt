package dev.habita.app.feature.gym.ui

import dev.habita.app.feature.gym.workout.ActiveWorkout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long a rest alarm rings before it stops on its own, when nobody dismisses it. */
internal const val RING_CAP_MILLIS = 60_000L

/** A rest that ran out longer ago than this ended while the app was away, and rang there. */
internal const val RING_LATE_MILLIS = 5_000L

/**
 * Whether a rest ending at [endsAt], seen running out on screen at [now], rings: not one that ended
 * a while ago, with the app away, which the system alarm already rang for.
 */
internal fun ringsOnScreen(endsAt: Long, now: Long): Boolean = now >= endsAt && now - endsAt < RING_LATE_MILLIS

/**
 * Whether going from [before] to [after] means the lifter is back at it, so a ringing rest alarm
 * stops: a set logged or taken back, a warm-up item done, an exercise moved, or a new rest. The
 * rest running out doesn't count; it only clears the rest, and after the break, starts the main
 * workout.
 */
internal fun movedOn(before: ActiveWorkout, after: ActiveWorkout): Boolean =
    before.sessionId != after.sessionId ||
        before.loggedSets.size != after.loggedSets.size ||
        before.warmUpQueue != after.warmUpQueue ||
        before.exerciseQueue != after.exerciseQueue ||
        before.warmUpSetsDone != after.warmUpSetsDone ||
        (after.rest != null && after.rest != before.rest)

/**
 * Whether the rest alarm is ringing, shared by the platform ringers. It stops when dismissed, or on
 * its own once it has rung for [capMillis].
 */
internal class RingState(private val capMillis: Long = RING_CAP_MILLIS) {
    private val _ringing = MutableStateFlow(false)
    val ringing: StateFlow<Boolean> = _ringing.asStateFlow()

    private var startedAt = 0L

    /** Starts ringing at [now]. Returns false when it already rings, which keeps its first start. */
    fun start(now: Long): Boolean {
        if (_ringing.value) return false
        startedAt = now
        _ringing.value = true
        return true
    }

    /** Stops ringing. Returns false when it wasn't. */
    fun stop(): Boolean {
        if (!_ringing.value) return false
        _ringing.value = false
        return true
    }

    /** Stops if it has rung for the whole cap by [now]. Returns true when it stopped here. */
    fun expire(now: Long): Boolean = _ringing.value && now - startedAt >= capMillis && stop()

    /** How long until it stops on its own; 0 when it isn't ringing. */
    fun remainingMillis(now: Long): Long =
        if (_ringing.value) (startedAt + capMillis - now).coerceAtLeast(0) else 0
}