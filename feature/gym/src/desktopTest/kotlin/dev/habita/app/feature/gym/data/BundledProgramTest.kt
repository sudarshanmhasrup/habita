package dev.habita.app.feature.gym.data

import dev.habita.app.shared.gym.ProgramParser
import kotlin.test.Test
import kotlin.test.assertTrue

class BundledProgramTest {
    @Test
    fun `the plan built into the app parses and lists alternatives for its exercises`() {
        val program = ProgramParser.parse(BUNDLED_PROGRAM)
        val inWorkouts = program.workouts.flatMap { it.exercises }.map { it.exerciseId }.toSet()

        assertTrue(program.exercises.any { it.alternatives.isNotEmpty() })
        // Every exercise in a workout except the plank has something to swap to.
        assertTrue(inWorkouts.filter { it != "plank" }.all { program.exercise(it)!!.alternatives.isNotEmpty() })
    }
}