package dev.habita.app.shared.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgramWriterTest {
    @Test
    fun `a written plan parses back to the same plan`() {
        val program = testProgram.copy(
            exercises = testProgram.exercises.map {
                if (it.id == "bench") it.copy(defaultStep = 2500, alternatives = listOf("leg-press")) else it
            },
        )

        assertEquals(program, ProgramParser.parse(ProgramWriter.write(program)))
    }

    @Test
    fun `writes steps in kg for weights and as numbers for bodyweight`() {
        val text = ProgramWriter.write(testProgram)

        assertEquals("2", ProgramWriter.kilograms(2000))
        assertEquals("2.5", ProgramWriter.kilograms(2500))
        assertEquals("1.25", ProgramWriter.kilograms(1250))
        assertTrue("| plank     | Plank     | bodyweight | seconds | no       | 5    |" in text, text)
    }
}