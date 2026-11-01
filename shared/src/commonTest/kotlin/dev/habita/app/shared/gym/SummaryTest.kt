package dev.habita.app.shared.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SummaryTest {
    private val first = session("s1", "a", 1 * DAY)
    private val second = session("s2", "a", 5 * DAY)
    private val firstSets = sets("s1", "bench", 1 * DAY, 20 * KG, 12, 12, 12, 10) +
        sets("s1", "plank", 1 * DAY + 1000, 0, 30, 30, 30, 30)
    private val secondSets = sets("s2", "bench", 5 * DAY, 20 * KG, 12, 12, 12, 12) +
        sets("s2", "plank", 5 * DAY + 1000, 0, 35, 35, 35, null)

    private fun summary(
        session: WorkoutSession = second,
        sessions: List<WorkoutSession> = listOf(first, second),
        sets: List<SetLog> = firstSets + secondSets,
    ) = Summary.of(session, testProgram, sessions, sets, steps = emptyMap(), now = 10 * DAY)

    @Test
    fun `measures total, warm-up and main time`() {
        val result = summary()

        assertEquals(60 * 60 * 1000L, result.totalMillis)
        assertEquals(15 * 60 * 1000L, result.warmUpMillis)
        assertEquals(45 * 60 * 1000L, result.mainMillis)
    }

    @Test
    fun `compares volume with the last session of the same workout`() {
        val result = summary()

        assertEquals(720.0, result.volumeKg)
        assertEquals(680.0, result.previousVolumeKg)
        assertEquals(720.0 / 680.0 * 100 - 100, result.volumeChangePercent!!, 0.0001)
    }

    @Test
    fun `the first session has nothing to compare with`() {
        val result = summary(session = first, sessions = listOf(first), sets = firstSets)

        assertNull(result.previousVolumeKg)
        assertNull(result.volumeChangePercent)
        assertEquals(emptyList(), result.exercisesProgressed)
        assertEquals(emptyList(), result.personalBests)
    }

    @Test
    fun `lists exercises that beat last time`() {
        assertEquals(listOf("bench", "plank"), summary().exercisesProgressed)
    }

    @Test
    fun `a personal best is the heaviest weight for the full target`() {
        // 20 kg was already lifted for 12 reps in the first session, so bench is not a best.
        assertEquals(listOf("plank"), summary().personalBests)

        val heavier = sets("s2", "bench", 5 * DAY, 22 * KG, 12, 12, 11, 10)
        val result = summary(sets = firstSets + heavier)
        assertEquals(listOf("bench"), result.personalBests)
    }

    @Test
    fun `counts completed and skipped sets`() {
        val result = summary()

        assertEquals(5, result.setsCompleted)
        assertEquals(1, result.setsSkipped)
    }

    @Test
    fun `previews the exercises going up next time`() {
        assertEquals(listOf("bench"), summary().nextIncreases)
    }

    @Test
    fun `an unfinished session is measured up to now`() {
        val running = second.copy(endedAt = null)
        val result = summary(session = running, sessions = listOf(first, running))

        assertEquals(10 * DAY - 5 * DAY, result.totalMillis)
    }
}