package dev.habita.app.shared.gym

import kotlin.test.Test
import kotlin.test.assertEquals

class SwapTest {
    private val cableFly = Exercise("cable-fly", "Cable Fly", Equipment.CABLE, Measure.REPS, perSide = false, defaultStep = 2500)
    private val dumbbellFly = Exercise("dumbbell-fly", "Dumbbell Fly", Equipment.DUMBBELL, Measure.REPS, perSide = false, defaultStep = 2 * KG)
    private val program = testProgram.copy(
        exercises = listOf(
            benchPress.copy(alternatives = listOf("cable-fly", "dumbbell-fly", "plank")),
            legPress.copy(alternatives = listOf("cable-fly")),
            plank,
            cableFly,
            dumbbellFly,
        ),
    )
    private val workoutA = program.workout("a")!!
    private val full = program.workout("full")!!

    @Test
    fun `replaces swapped exercises and keeps the rest`() {
        val swapped = Swaps.apply(workoutA, program, mapOf("bench" to "cable-fly"))

        assertEquals(listOf("cable-fly", "plank"), swapped.exercises.map { it.exerciseId })
        assertEquals(benchSlot.copy(exerciseId = "cable-fly"), swapped.exercises[0])
    }

    @Test
    fun `ignores a swap the plan no longer allows`() {
        val swapped = Swaps.apply(workoutA, program, mapOf("bench" to "leg-press"))

        assertEquals(workoutA, swapped)
    }

    @Test
    fun `never puts the same exercise in a workout twice`() {
        val toPlank = Swaps.apply(workoutA, program, mapOf("bench" to "plank"))
        val both = Swaps.apply(full, program, mapOf("bench" to "cable-fly", "leg-press" to "cable-fly"))

        assertEquals(workoutA, toPlank)
        assertEquals(listOf("cable-fly", "leg-press"), both.exercises.map { it.exerciseId })
    }

    @Test
    fun `deleted swaps are not in use`() {
        val swaps = listOf(
            ExerciseSwap("bench", "cable-fly", updatedAt = 1),
            ExerciseSwap("leg-press", "cable-fly", updatedAt = 1, deleted = true),
        )

        assertEquals(mapOf("bench" to "cable-fly"), Swaps.active(swaps))
    }

    @Test
    fun `choices leave out exercises already in the workout`() {
        val current = Swaps.apply(workoutA, program, mapOf("bench" to "dumbbell-fly"))

        assertEquals(listOf("cable-fly", "dumbbell-fly"), Swaps.choices(workoutA, current, 0, program).map { it.id })
        assertEquals(emptyList(), Swaps.choices(workoutA, current, 1, program))
    }
}