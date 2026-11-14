package dev.habita.app.feature.gym.ui

import dev.habita.app.feature.gym.workout.ActiveWorkout
import dev.habita.app.feature.gym.workout.Phase
import dev.habita.app.feature.gym.workout.WorkoutEngine
import dev.habita.app.shared.gym.Effort
import dev.habita.app.shared.gym.Equipment
import dev.habita.app.shared.gym.Exercise
import dev.habita.app.shared.gym.ExerciseTarget
import dev.habita.app.shared.gym.Measure
import dev.habita.app.shared.gym.TargetReason
import dev.habita.app.shared.gym.WarmUp
import dev.habita.app.shared.gym.WarmUpItem
import dev.habita.app.shared.gym.WarmUpStatus
import dev.habita.app.shared.gym.Workout
import dev.habita.app.shared.gym.WorkoutExercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestRingTest {
    @Test
    fun startsRinging() {
        val state = RingState(capMillis = 60_000)
        assertFalse(state.ringing.value)
        assertTrue(state.start(now = 1_000))
        assertTrue(state.ringing.value)
        assertEquals(60_000, state.remainingMillis(now = 1_000))
    }

    @Test
    fun aSecondStartKeepsTheFirst() {
        val state = RingState(capMillis = 60_000)
        state.start(now = 0)
        assertFalse(state.start(now = 30_000))
        assertEquals(30_000, state.remainingMillis(now = 30_000))
    }

    @Test
    fun dismissingStopsIt() {
        val state = RingState()
        state.start(now = 0)
        assertTrue(state.stop())
        assertFalse(state.ringing.value)
        assertFalse(state.stop())
        assertEquals(0, state.remainingMillis(now = 0))
    }

    @Test
    fun stopsOnItsOwnAfterTheCap() {
        val state = RingState(capMillis = 60_000)
        state.start(now = 0)
        assertFalse(state.expire(now = 59_999))
        assertTrue(state.ringing.value)
        assertTrue(state.expire(now = 60_000))
        assertFalse(state.ringing.value)
        assertFalse(state.expire(now = 61_000))
    }

    @Test
    fun anOldCapDoesNotCutANewRingShort() {
        val state = RingState(capMillis = 60_000)
        state.start(now = 0)
        state.stop()
        state.start(now = 50_000)
        // The first ring's timer fires at 60 s; the second ring has 50 s left.
        assertFalse(state.expire(now = 60_000))
        assertTrue(state.ringing.value)
        assertTrue(state.expire(now = 110_000))
    }

    @Test
    fun ringsOnlyForARestThatRanOutJustNow() {
        assertFalse(ringsOnScreen(endsAt = 10_000, now = 9_999))
        assertTrue(ringsOnScreen(endsAt = 10_000, now = 10_000))
        assertTrue(ringsOnScreen(endsAt = 10_000, now = 14_999))
        // Ended a while ago with the app away: the system alarm rang then.
        assertFalse(ringsOnScreen(endsAt = 10_000, now = 15_000))
    }

    private val exercises = listOf("bench", "row").map {
        Exercise(it, it, Equipment.DUMBBELL, Measure.REPS, perSide = false, defaultStep = 2000)
    }
    private val workout = Workout(
        id = "upper-a",
        name = "Upper Body A",
        warmUpId = "upper",
        rotation = 1,
        exercises = listOf(
            WorkoutExercise("bench", sets = 2, target = 12, restSeconds = 90),
            WorkoutExercise("row", sets = 2, target = 12, restSeconds = 60),
        ),
    )
    private val warmUp = WarmUp("upper", "Upper", listOf("a", "b").map { WarmUpItem(it, "10") }, restSeconds = 120)
    private val targets = workout.exercises.map {
        ExerciseTarget(it.exerciseId, 20_000, it.target, TargetReason.REPEAT, lastTime = null)
    }

    private fun start(): ActiveWorkout = WorkoutEngine.start("s1", workout, warmUp, exercises, targets, now = 0)

    /** In the main workout, resting after the first working set. */
    private fun resting(): ActiveWorkout {
        var active = start()
        while (active.phase == Phase.WARM_UP) {
            active = WorkoutEngine.completeWarmUp(active, WarmUpStatus.DONE, "w", 0).first
        }
        active = WorkoutEngine.endBreak(active)
        active = WorkoutEngine.logWarmUpSet(active, 10_000, null, "warm", 0).first
        return WorkoutEngine.logSet(active, 20_000, 12, Effort.MEDIUM, 2000, "set", 0).first
    }

    @Test
    fun theRestRunningOutDoesNotStopTheAlarm() {
        val rest = resting()
        assertTrue(rest.rest != null)
        assertFalse(movedOn(rest, WorkoutEngine.endRest(rest)))
    }

    @Test
    fun theBreakRunningOutDoesNotStopTheAlarm() {
        var active = start()
        while (active.phase == Phase.WARM_UP) {
            active = WorkoutEngine.completeWarmUp(active, WarmUpStatus.DONE, "w", 0).first
        }
        assertEquals(Phase.BREAK, active.phase)
        assertFalse(movedOn(active, WorkoutEngine.endBreak(active)))
    }

    @Test
    fun theNextSetStopsTheAlarm() {
        val ready = WorkoutEngine.endRest(resting())
        val logged = WorkoutEngine.logSet(ready, 20_000, 12, Effort.MEDIUM, 2000, "set2", 1_000).first
        assertTrue(movedOn(ready, logged))
    }

    @Test
    fun movingAnExerciseStopsTheAlarm() {
        val ready = WorkoutEngine.endRest(resting())
        assertTrue(movedOn(ready, WorkoutEngine.exerciseMachineTaken(ready)))
    }

    @Test
    fun aWarmUpItemDoneStopsTheAlarm() {
        val active = start()
        assertTrue(movedOn(active, WorkoutEngine.completeWarmUp(active, WarmUpStatus.DONE, "w", 0).first))
    }

    @Test
    fun nothingChangedLeavesItRinging() {
        val ready = WorkoutEngine.endRest(resting())
        assertFalse(movedOn(ready, ready))
    }
}