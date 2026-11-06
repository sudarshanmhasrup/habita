package dev.habita.app.feature.gym.workout

import dev.habita.app.shared.gym.Effort
import dev.habita.app.shared.gym.Equipment
import dev.habita.app.shared.gym.Exercise
import dev.habita.app.shared.gym.ExerciseTarget
import dev.habita.app.shared.gym.Measure
import dev.habita.app.shared.gym.TargetReason
import dev.habita.app.shared.gym.WarmUp
import dev.habita.app.shared.gym.Workout
import dev.habita.app.shared.gym.WorkoutExercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwapExerciseTest {
    private fun exercise(id: String) = Exercise(id, id, Equipment.DUMBBELL, Measure.REPS, perSide = false, defaultStep = 2000)
    private val bench = exercise("bench")
    private val fly = exercise("fly")
    private val row = exercise("row")
    private val planned = Workout(
        id = "upper-a",
        name = "Upper Body A",
        warmUpId = "upper",
        rotation = 1,
        exercises = listOf(
            WorkoutExercise("bench", sets = 2, target = 12, restSeconds = 90),
            WorkoutExercise("row", sets = 2, target = 12, restSeconds = 60),
        ),
    )
    private val noWarmUp = WarmUp("upper", "Upper", emptyList(), restSeconds = 120)

    private fun target(id: String, grams: Int?) = ExerciseTarget(id, grams, 12, TargetReason.REPEAT, lastTime = null)

    private fun startMain(workout: Workout = planned, exercises: List<Exercise> = listOf(bench, row)) =
        WorkoutEngine.endBreak(
            WorkoutEngine.start(
                "s1", workout, noWarmUp, exercises, exercises.map { target(it.id, 20_000) }, now = 0, planned = planned,
            ),
        )

    @Test
    fun `a workout started with swaps remembers the planned exercises`() {
        val swapped = planned.copy(exercises = planned.exercises.mapIndexed { i, it -> if (i == 0) it.copy(exerciseId = "fly") else it })
        val active = startMain(swapped, listOf(fly, row))

        assertEquals(mapOf(0 to "bench"), active.originals)
        assertEquals(planned, active.planned)
    }

    @Test
    fun `swapping replaces the exercise and its target`() {
        val active = WorkoutEngine.swapExercise(startMain(), 0, fly, target("fly", null))

        assertEquals("fly", active.slot(0).exerciseId)
        assertEquals(fly, active.exercises[0])
        assertEquals(null, active.targets[0].weightGrams)
        assertEquals(mapOf(0 to "bench"), active.originals)
        assertTrue(active.needsWarmUpSet(0))
    }

    @Test
    fun `swapping back to the planned exercise clears the swap`() {
        val swapped = WorkoutEngine.swapExercise(startMain(), 0, fly, target("fly", null))
        val back = WorkoutEngine.swapExercise(swapped, 0, bench, target("bench", 20_000))

        assertEquals("bench", back.slot(0).exerciseId)
        assertEquals(emptyMap(), back.originals)
    }

    @Test
    fun `a slot with logged sets can not be swapped`() {
        val (logged, _) = WorkoutEngine.logSet(startMain(), 20_000, 12, Effort.MEDIUM, 2000, "set1", now = 1)

        assertFalse(logged.canSwap(0))
        assertTrue(logged.canSwap(1))
        assertFailsWith<IllegalStateException> { WorkoutEngine.swapExercise(logged, 0, fly, target("fly", null)) }
    }

    @Test
    fun `sets of the replacement are logged under its own id`() {
        val swapped = WorkoutEngine.swapExercise(startMain(), 0, fly, target("fly", 10_000))
        val (_, log) = WorkoutEngine.logSet(swapped, 10_000, 12, Effort.MEDIUM, 2000, "set1", now = 1)

        assertEquals("fly", log.exerciseId)
    }

    @Test
    fun `a workout without swaps has no originals`() {
        assertEquals(emptyMap(), startMain().originals)
    }
}