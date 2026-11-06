package dev.habita.app.shared.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckInTest {
    private val now = 200 * DAY

    /** Bench at [weightGrams] for 3 × 12 on each of [days]. */
    private fun benchOn(vararg days: Int, weightGrams: Int = 20 * KG, results: IntArray = intArrayOf(12, 12, 12)) =
        days.map { day -> session("s$day", "a", day * DAY) } to
            days.flatMap { day -> sets("s$day", "bench", day * DAY, weightGrams, 12, *results.toTypedArray()) }

    private fun plateaus(sessions: List<WorkoutSession>, sets: List<SetLog>) =
        CheckIns.plateaus(testProgram, sessions, sets, now)

    @Test
    fun `an exercise with no new best in six weeks is stuck`() {
        val (old, oldSets) = benchOn(100, 120)
        val (recent, recentSets) = benchOn(165, 175, 185, 195)

        assertEquals(listOf(Plateau("bench", 4)), plateaus(old + recent, oldSets + recentSets))
    }

    @Test
    fun `a heavier weight for full reps is progress`() {
        val (old, oldSets) = benchOn(100, 120)
        val (recent, recentSets) = benchOn(165, 175, 185, 195, weightGrams = 22 * KG)

        assertTrue(plateaus(old + recent, oldSets + recentSets).isEmpty())
    }

    @Test
    fun `a heavier weight with missed reps is not a new best`() {
        val (old, oldSets) = benchOn(100, 120)
        val (recent, recentSets) = benchOn(165, 175, 185, 195, weightGrams = 22 * KG, results = intArrayOf(10, 9, 8))

        assertEquals(1, plateaus(old + recent, oldSets + recentSets).size)
    }

    @Test
    fun `too few recent sessions or no older history is not stuck`() {
        val (old, oldSets) = benchOn(100, 120)
        val (few, fewSets) = benchOn(175, 185, 195)
        val (fresh, freshSets) = benchOn(165, 175, 185, 195)

        assertTrue(plateaus(old + few, oldSets + fewSets).isEmpty())
        assertTrue(plateaus(fresh, freshSets).isEmpty())
    }

    @Test
    fun `a check-in is due about three months after the first workout`() {
        val first = listOf(session("s1", "a", now - CheckIns.EVERY_MILLIS))

        assertEquals(CheckInReason.SCHEDULED, CheckIns.due(null, first, emptyList(), now))
        assertNull(CheckIns.due(null, listOf(session("s1", "a", now - 30 * DAY)), emptyList(), now))
        assertNull(CheckIns.due(null, emptyList(), emptyList(), now))
    }

    @Test
    fun `a plateau asks for a check-in, but not right after one`() {
        val sessions = listOf(session("s1", "a", now - 60 * DAY))
        val stuck = listOf(Plateau("bench", 4))

        assertEquals(CheckInReason.PLATEAU, CheckIns.due(null, sessions, stuck, now))
        assertNull(CheckIns.due(now - 10 * DAY, sessions, stuck, now))
        assertEquals(CheckInReason.PLATEAU, CheckIns.due(now - CheckIns.QUIET_MILLIS, sessions, stuck, now))
    }

    @Test
    fun `the text has the numbers and a plan the parser reads back`() {
        val (old, oldSets) = benchOn(100, 120)
        val (recent, recentSets) = benchOn(165, 175, 185, 195)
        val text = CheckIns.text(
            CheckInData(
                program = testProgram,
                sessions = old + recent,
                sets = oldSets + recentSets,
                body = listOf(BodyLog("1970-07-19", weightGrams = 78_300, waistMm = 852, updatedAt = 0)),
                swaps = mapOf("leg-press" to "bench"),
                lastCheckIn = null,
                now = now,
                today = "1970-07-20",
                formatDate = { "day ${it / DAY}" },
            ),
        )

        assertTrue("Period: day 100 to day 200 (14 weeks)" in text)
        assertTrue("- Workouts finished: 6 (0.4 a week)" in text)
        assertTrue("- Bench: no new best in 6 weeks, over 4 sessions" in text)
        assertTrue("| Bench | 6 | 20 kg × 12, 12, 12 | 20 kg × 12, 12, 12 | 20 kg for full reps | 0 / 0 / 0 | 0 |" in text)
        assertTrue("| 0 | 78.3 kg | 1 | 85.2 cm |" in text)
        assertTrue("- Leg Press is done as Bench" in text)
        assertEquals(testProgram, ProgramParser.parse(text.substringAfter("## Current plan")))
    }
}