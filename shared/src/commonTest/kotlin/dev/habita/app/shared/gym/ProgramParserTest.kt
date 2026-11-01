package dev.habita.app.shared.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgramParserTest {
    private val valid = """
        # Program

        Notes are ignored.

        ## Exercises

        | Id    | Name  | Equipment  | Measure | Per side | Step |
        |-------|-------|------------|---------|----------|------|
        | bench | Bench | dumbbell   | reps    | no       | 2.5  |
        | row   | Row   | dumbbell   | reps    | yes      | 2    |
        | plank | Plank | bodyweight | seconds | no       | 5    |

        ## Warm-ups

        ### Upper

        - Id: upper
        - Rest: 120

        | Name | Amount |
        |------|--------|
        | Walk | 5 min  |

        ## Workouts

        ### Upper A

        - Id: upper-a
        - Warm-up: upper
        - Rotation: 1

        | Exercise | Sets | Target | Rest |
        |----------|------|--------|------|
        | bench    | 3    | 12     | 90   |
        | plank    | 3    | 30     | 60   |

        ### Full Body

        - Id: full-body
        - Warm-up: upper
        - Rotation: fallback

        | Exercise | Sets | Target | Rest |
        |----------|------|--------|------|
        | row      | 3    | 12     | 90   |
    """.trimIndent()

    @Test
    fun `parses exercises, warm-ups and workouts`() {
        val program = ProgramParser.parse(valid)

        assertEquals(
            Exercise("bench", "Bench", Equipment.DUMBBELL, Measure.REPS, perSide = false, defaultStep = 2500),
            program.exercise("bench"),
        )
        assertTrue(program.exercise("row")!!.perSide)
        assertEquals(5, program.exercise("plank")!!.defaultStep)
        assertEquals(
            WarmUp("upper", "Upper", listOf(WarmUpItem("Walk", "5 min")), restSeconds = 120),
            program.warmUps.single(),
        )
        assertEquals(1, program.workout("upper-a")!!.rotation)
        assertEquals(WorkoutExercise("plank", 3, 30, 60), program.workout("upper-a")!!.exercises[1])
        assertNull(program.workout("full-body")!!.rotation)
    }

    @Test
    fun `rejects a workout that uses an unknown exercise`() {
        val error = assertFailsWith<ProgramFormatException> {
            ProgramParser.parse(valid.replace("| row      | 3", "| squat    | 3"))
        }
        assertTrue("unknown exercise \"squat\"" in error.message!!)
    }

    @Test
    fun `rejects wrong table columns with the line number`() {
        val error = assertFailsWith<ProgramFormatException> {
            ProgramParser.parse(valid.replace("| Exercise | Sets | Target | Rest |", "| Exercise | Sets | Reps | Rest |"))
        }
        assertEquals(32, error.line)
    }

    @Test
    fun `rejects duplicate exercise ids`() {
        assertFailsWith<ProgramFormatException> {
            ProgramParser.parse(valid.replace("| row   | Row  ", "| bench | Row  "))
        }
    }

    @Test
    fun `rejects ids that are not kebab-case`() {
        assertFailsWith<ProgramFormatException> {
            ProgramParser.parse(valid.replace("- Id: upper-a", "- Id: Upper A"))
        }
    }

    @Test
    fun `rejects values that are not numbers`() {
        val error = assertFailsWith<ProgramFormatException> {
            ProgramParser.parse(valid.replace("| bench    | 3    | 12 ", "| bench    | 3    | ten"))
        }
        assertEquals(34, error.line)
    }

    @Test
    fun `rejects an unknown equipment type`() {
        assertFailsWith<ProgramFormatException> {
            ProgramParser.parse(valid.replace("| dumbbell   | reps    | no       | 2.5", "| barbell    | reps    | no       | 2.5"))
        }
    }

    @Test
    fun `rejects unknown and missing keys`() {
        assertFailsWith<ProgramFormatException> {
            ProgramParser.parse(valid.replace("- Rotation: 1", "- Rotation: 1\n- Day: monday"))
        }
        assertFailsWith<ProgramFormatException> {
            ProgramParser.parse(valid.replace("- Warm-up: upper\n- Rotation: 1", "- Rotation: 1"))
        }
    }

    @Test
    fun `rejects a missing warm-up`() {
        assertFailsWith<ProgramFormatException> {
            ProgramParser.parse(valid.replace("- Warm-up: upper\n- Rotation: fallback", "- Warm-up: lower\n- Rotation: fallback"))
        }
    }

    @Test
    fun `rejects a program without a rotation`() {
        assertFailsWith<ProgramFormatException> {
            ProgramParser.parse(valid.replace("- Rotation: 1", "- Rotation: fallback"))
        }
    }
}