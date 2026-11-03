package dev.habita.app.feature.gym.workout

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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutEngineTest {
    private val exercises = listOf("bench", "row", "curl").map {
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
            WorkoutExercise("curl", sets = 1, target = 10, restSeconds = 60),
        ),
    )
    private val warmUp = WarmUp("upper", "Upper", listOf("a", "b", "c").map { WarmUpItem(it, "10") }, restSeconds = 120)
    private val targets = workout.exercises.map {
        ExerciseTarget(it.exerciseId, 20_000, it.target, TargetReason.REPEAT, lastTime = null)
    }

    private fun start(warmUp: WarmUp = this.warmUp) =
        WorkoutEngine.start("s1", workout, warmUp, exercises, targets, now = 0)

    private fun ActiveWorkout.inMain(): ActiveWorkout {
        var active = this
        while (active.phase == Phase.WARM_UP) {
            active = WorkoutEngine.completeWarmUp(active, WarmUpStatus.DONE, "w", 0).first
        }
        return WorkoutEngine.endBreak(active)
    }

    @Test
    fun startsWithTheWarmUp() {
        val active = start()
        assertEquals(Phase.WARM_UP, active.phase)
        assertEquals(0, active.currentWarmUp)
        assertNull(active.rest)
    }

    @Test
    fun anEmptyWarmUpGoesStraightToTheBreak() {
        val active = start(warmUp.copy(items = emptyList()))
        assertEquals(Phase.BREAK, active.phase)
        assertEquals(120_000, active.rest?.totalMillis)
    }

    @Test
    fun theLastWarmUpItemStartsTheBreak() {
        var active = start()
        val (afterFirst, log) = WorkoutEngine.completeWarmUp(active, WarmUpStatus.SKIPPED, "w1", now = 5)
        assertEquals(0, log.position)
        assertEquals(WarmUpStatus.SKIPPED, log.status)
        active = WorkoutEngine.completeWarmUp(afterFirst, WarmUpStatus.DONE, "w2", 6).first
        active = WorkoutEngine.completeWarmUp(active, WarmUpStatus.DONE, "w3", now = 7).first
        assertEquals(Phase.BREAK, active.phase)
        assertEquals(Rest(7, 120_007), active.rest)
        val main = WorkoutEngine.endBreak(active)
        assertEquals(Phase.MAIN, main.phase)
        assertNull(main.rest)
    }

    @Test
    fun machineTakenMovesTheWarmUpItemBehindTheNextOne() {
        val active = WorkoutEngine.warmUpMachineTaken(start())
        assertEquals(listOf(1, 0, 2), active.warmUpQueue)
        val (_, log) = WorkoutEngine.completeWarmUp(active, WarmUpStatus.DONE, "w", 0)
        assertEquals(1, log.position)
    }

    @Test
    fun machineTakenOnTheLastItemChangesNothing() {
        val active = start().copy(warmUpQueue = listOf(2))
        assertEquals(listOf(2), WorkoutEngine.warmUpMachineTaken(active).warmUpQueue)
    }

    @Test
    fun eachSetStartsItsRestAndTheLastSetMovesOn() {
        var active = start().inMain()
        val (afterSet, log) = WorkoutEngine.logSet(active, 20_000, 12, Effort.MEDIUM, 2_000, "set1", now = 1_000)
        assertEquals(1, log.setNumber)
        assertEquals("bench", log.exerciseId)
        assertEquals(12, log.target)
        assertEquals(Rest(1_000, 91_000), afterSet.rest)
        assertEquals(0, afterSet.currentSlot)
        assertEquals(2, afterSet.setNumber(0))
        assertFailsWith<IllegalStateException> { WorkoutEngine.logSet(afterSet, 20_000, 12, Effort.MEDIUM, 2_000, "x", 0) }

        active = WorkoutEngine.endRest(afterSet)
        active = WorkoutEngine.logSet(active, 22_000, 11, Effort.MEDIUM, 2_000, "set2", 2_000).first
        assertEquals(1, active.currentSlot)
        assertEquals(1, active.exercisesDone)
        assertEquals(22_000, active.lastWeights[0])
        assertEquals(Rest(2_000, 92_000), active.rest)
    }

    @Test
    fun aWarmUpSetComesFirstAndDoesNotCountAsASet() {
        var active = start().inMain()
        assertTrue(active.needsWarmUpSet(0))

        val (afterWarmUp, log) = WorkoutEngine.logWarmUpSet(active, 10_000, 10, "w", now = 1_000)
        assertTrue(log!!.warmUp)
        assertEquals(0, log.setNumber)
        assertEquals(Rest(1_000, 61_000), afterWarmUp.rest)
        assertEquals(1, afterWarmUp.setNumber(0))
        assertFalse(afterWarmUp.needsWarmUpSet(0))
        assertEquals(log, afterWarmUp.warmUpSetFor(0))
        assertTrue(afterWarmUp.setsFor(0).isEmpty())

        // Skipping logs nothing and starts no rest.
        active = WorkoutEngine.endRest(afterWarmUp)
        repeat(2) { active = WorkoutEngine.endRest(WorkoutEngine.logSet(active, 20_000, 12, Effort.MEDIUM, 2_000, "s$it", 0).first) }
        val (skipped, none) = WorkoutEngine.logWarmUpSet(active, 0, null, "x", 0)
        assertNull(none)
        assertNull(skipped.rest)
        assertFalse(skipped.needsWarmUpSet(1))
    }

    @Test
    fun anEasySetPrefillsAHeavierNextSet() {
        var active = start().inMain()
        val (afterSet, log) = WorkoutEngine.logSet(active, 5_000, 12, Effort.EASY, 2_000, "a", 0)
        assertEquals(Effort.EASY, log.effort)
        active = WorkoutEngine.endRest(afterSet)
        assertEquals(7_000, active.nextSet(0).weightGrams)

        // The weight typed for the next set wins, and how that set felt sets the one after.
        active = WorkoutEngine.endRest(WorkoutEngine.logSet(active, 15_000, 12, Effort.MEDIUM, 2_000, "b", 0).first)
        assertEquals(15_000, active.nextSet(0).weightGrams)
    }

    @Test
    fun aSkippedSetCountsButKeepsThePreviousWeight() {
        var active = start().inMain()
        active = WorkoutEngine.endRest(WorkoutEngine.logSet(active, 20_000, 12, Effort.MEDIUM, 2_000, "a", 0).first)
        val (next, log) = WorkoutEngine.logSet(active, 0, null, null, 2_000, "b", 0)
        assertTrue(log.isSkipped)
        assertEquals(20_000, next.lastWeights[0])
        assertEquals(1, next.currentSlot)
    }

    @Test
    fun machineTakenInTheMainWorkoutKeepsCompletedSets() {
        var active = start().inMain()
        active = WorkoutEngine.endRest(WorkoutEngine.logSet(active, 20_000, 12, Effort.MEDIUM, 2_000, "a", 0).first)
        active = WorkoutEngine.exerciseMachineTaken(active)
        assertEquals(listOf(1, 0, 2), active.exerciseQueue)
        assertEquals(1, active.setsLogged[0])

        repeat(2) { active = WorkoutEngine.endRest(WorkoutEngine.logSet(active, 30_000, 12, Effort.MEDIUM, 2_000, "r$it", 0).first) }
        assertEquals(0, active.currentSlot)
        assertEquals(2, active.setNumber(0))
    }

    @Test
    fun theLastSetFinishesWithoutRest() {
        var active = start().inMain()
        var sets = 0
        while (!active.isFinished) {
            active = WorkoutEngine.logSet(active, 20_000, 12, Effort.MEDIUM, 2_000, "s${sets++}", 0).first
            if (!active.isFinished) {
                assertTrue(active.rest != null)
                active = WorkoutEngine.endRest(active)
            }
        }
        assertEquals(5, sets)
        assertNull(active.rest)
        assertEquals(3, active.exercisesDone)
    }

    @Test
    fun updatedTargetsApplyOnlyToUnstartedSlots() {
        var active = start().inMain()
        active = WorkoutEngine.endRest(WorkoutEngine.logSet(active, 20_000, 12, Effort.MEDIUM, 2_000, "a", 0).first)
        val newTarget = targets[0].copy(weightGrams = 25_000)
        val updated = WorkoutEngine.updateTargets(active, mapOf(0 to newTarget, 2 to newTarget.copy(exerciseId = "curl")))
        assertEquals(20_000, updated.targets[0].weightGrams)
        assertEquals(25_000, updated.targets[2].weightGrams)
        assertFalse(updated.isFinished)
    }
}