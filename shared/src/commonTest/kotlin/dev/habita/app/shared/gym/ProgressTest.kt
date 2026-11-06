package dev.habita.app.shared.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressTest {
    private val sessions = listOf(session("s1", "a", 1 * DAY), session("s2", "a", 8 * DAY), session("s3", "b", 9 * DAY))
    private val sets =
        sets("s1", "bench", 1 * DAY, 20 * KG, 12, 12, 12, 12) +
            sets("s2", "bench", 8 * DAY, 22 * KG, 12, 12, 12, 10) +
            sets("s1", "plank", 1 * DAY, 0, 30, 30, 35, 30) +
            sets("s3", "leg-press", 9 * DAY, 60 * KG, 12, 12, 12, 12) +
            sets("s3", "gone", 9 * DAY, 10 * KG, 12, 12)

    @Test
    fun `follows each exercise from its first session to its latest`() {
        val bench = Progress.exercises(testProgram, sessions, sets).first { it.exercise.id == "bench" }

        assertEquals(listOf(20 * KG, 22 * KG), bench.points.map { it.value })
        assertEquals(20 * KG, bench.first)
        assertEquals(22 * KG, bench.latest)
        assertTrue(bench.inPlan)
    }

    @Test
    fun `bodyweight exercises use the best hold`() {
        val plank = Progress.exercises(testProgram, sessions, sets).first { it.exercise.id == "plank" }

        assertEquals(listOf(35), plank.points.map { it.value })
    }

    @Test
    fun `lists exercises in plan order and skips unknown ones`() {
        val program = testProgram.copy(workouts = testProgram.workouts.filter { it.id != "b" && it.id != "full" })

        val ids = Progress.exercises(program, sessions, sets).map { it.exercise.id }

        assertEquals(listOf("bench", "plank", "leg-press"), ids)
        assertFalse(Progress.exercises(program, sessions, sets).last().inPlan)
    }

    @Test
    fun `averages the body log by week, oldest first`() {
        val logs = listOf(
            BodyLog("2026-01-28", weightGrams = 78_000, updatedAt = 0),
            BodyLog("2026-01-27", weightGrams = 78_400, waistMm = 850, updatedAt = 0),
            BodyLog("2026-01-10", weightGrams = 80_000, updatedAt = 0),
        )

        assertEquals(
            listOf(BodyWeek(2, 80_000, null), BodyWeek(0, 78_200, 850)),
            Progress.bodyWeeks(logs, "2026-01-28"),
        )
    }
}