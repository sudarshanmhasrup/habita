package dev.habita.app.shared.gym

import kotlin.test.Test
import kotlin.test.assertEquals

class RetiredExercisesTest {
    private val squat = Exercise("squat", "Squat", Equipment.DUMBBELL, Measure.REPS, perSide = false, defaultStep = 2 * KG)

    @Test
    fun `keeps exercises older plans had, newest definition first`() {
        val current = testProgram
        val older = listOf(
            testProgram.copy(exercises = testProgram.exercises + squat.copy(name = "Goblet Squat", alternatives = listOf("bench", "gone"))),
            testProgram.copy(exercises = testProgram.exercises + squat),
        )

        val kept = current.keepingRetired(older)

        assertEquals(current.exercises.size + 1, kept.exercises.size)
        assertEquals("Goblet Squat", kept.exercise("squat")?.name)
        assertEquals(listOf("bench"), kept.exercise("squat")?.alternatives)
        assertEquals(current.workouts, kept.workouts)
    }

    @Test
    fun `a plan with nothing dropped is unchanged`() {
        assertEquals(testProgram, testProgram.keepingRetired(listOf(testProgram)))
    }
}