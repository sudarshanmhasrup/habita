package dev.habita.app.shared.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressionTest {
    private val now = 100 * DAY

    private fun bench(vararg history: Performance, step: Int = benchPress.defaultStep) =
        Progression.target(benchPress, benchSlot, step, history.toList(), now)

    @Test
    fun `first time asks for a weight`() {
        val target = bench()

        assertEquals(TargetReason.FIRST_TIME, target.reason)
        assertNull(target.weightGrams)
        assertEquals(12, target.target)
        assertNull(target.lastTime)
    }

    @Test
    fun `all sets at target goes up one step`() {
        val target = bench(performance("s1", now - 3 * DAY, 20 * KG, 12, 12, 12, 12))

        assertEquals(TargetReason.INCREASE, target.reason)
        assertEquals(22 * KG, target.weightGrams)
    }

    @Test
    fun `the step chosen in the app replaces the default`() {
        val target = bench(performance("s1", now - 3 * DAY, 20 * KG, 12, 12, 12, 12), step = 2500)

        assertEquals(22_500, target.weightGrams)
    }

    @Test
    fun `a missed set repeats the weight`() {
        val target = bench(performance("s1", now - 3 * DAY, 20 * KG, 12, 12, 12, 10))

        assertEquals(TargetReason.REPEAT, target.reason)
        assertEquals(20 * KG, target.weightGrams)
    }

    @Test
    fun `a skipped set is not a completed one`() {
        val target = bench(performance("s1", now - 3 * DAY, 20 * KG, 12, 12, 12, null))

        assertEquals(TargetReason.REPEAT, target.reason)
    }

    @Test
    fun `progression builds on the heaviest weight used`() {
        val sets = sets("s1", "bench", now - 3 * DAY, 18 * KG, 12, 12) +
            sets("s1", "bench", now - 3 * DAY + 10_000, 20 * KG, 12, 12, 12, 12).map { it.copy(setNumber = it.setNumber + 1) }
        val target = bench(Performance("s1", now - 3 * DAY, sets))

        assertEquals(TargetReason.INCREASE, target.reason)
        assertEquals(22 * KG, target.weightGrams)
    }

    @Test
    fun `reps below the floor after a jump drop back to the previous weight`() {
        val target = bench(
            performance("s2", now - 3 * DAY, 22 * KG, 12, 9, 7, 6),
            performance("s1", now - 6 * DAY, 20 * KG, 12, 12, 12, 12),
        )

        assertEquals(TargetReason.DROP_BACK, target.reason)
        assertEquals(20 * KG, target.weightGrams)
    }

    @Test
    fun `reps below the floor with no lighter history repeats`() {
        val target = bench(performance("s1", now - 3 * DAY, 22 * KG, 12, 9, 7, 6))

        assertEquals(TargetReason.REPEAT, target.reason)
    }

    @Test
    fun `three sessions without progress deload by about ten percent`() {
        val target = bench(
            performance("s3", now - 3 * DAY, 30 * KG, 12, 12, 10, 9),
            performance("s2", now - 6 * DAY, 30 * KG, 12, 12, 11, 9),
            performance("s1", now - 9 * DAY, 30 * KG, 12, 12, 10, 9),
        )

        assertEquals(TargetReason.DELOAD, target.reason)
        assertEquals(26 * KG, target.weightGrams)
    }

    @Test
    fun `three sessions with rising reps are not a stall`() {
        val target = bench(
            performance("s3", now - 3 * DAY, 30 * KG, 12, 12, 11, 10),
            performance("s2", now - 6 * DAY, 30 * KG, 12, 12, 10, 9),
            performance("s1", now - 9 * DAY, 30 * KG, 12, 11, 9, 8),
        )

        assertEquals(TargetReason.REPEAT, target.reason)
        assertEquals(30 * KG, target.weightGrams)
    }

    @Test
    fun `a long break keeps the weight even after hitting every set`() {
        val target = bench(performance("s1", now - 30 * DAY, 20 * KG, 12, 12, 12, 12))

        assertEquals(TargetReason.RETURN_AFTER_BREAK, target.reason)
        assertEquals(20 * KG, target.weightGrams)
    }

    @Test
    fun `sessions with only skipped sets are ignored`() {
        val target = bench(
            performance("s2", now - 2 * DAY, 22 * KG, 12, null, null, null),
            performance("s1", now - 5 * DAY, 20 * KG, 12, 12, 12, 12),
        )

        assertEquals(TargetReason.INCREASE, target.reason)
        assertEquals(22 * KG, target.weightGrams)
    }

    @Test
    fun `bodyweight starts at the program target`() {
        val target = Progression.target(plank, plankSlot, 5, emptyList(), now)

        assertEquals(TargetReason.FIRST_TIME, target.reason)
        assertEquals(0, target.weightGrams)
        assertEquals(30, target.target)
    }

    @Test
    fun `bodyweight holding every set adds one step of time`() {
        val history = listOf(performance("s1", now - 7 * DAY, 0, 35, 35, 36, 40))
        val target = Progression.target(plank, plankSlot, 5, history, now)

        assertEquals(TargetReason.INCREASE, target.reason)
        assertEquals(40, target.target)
    }

    @Test
    fun `bodyweight missing a set keeps the time`() {
        val history = listOf(performance("s1", now - 7 * DAY, 0, 35, 35, 30, 35))
        val target = Progression.target(plank, plankSlot, 5, history, now)

        assertEquals(TargetReason.REPEAT, target.reason)
        assertEquals(35, target.target)
    }

    @Test
    fun `floor is two thirds of the target`() {
        assertEquals(8, Progression.floor(12))
    }

    @Test
    fun `deload rounds to whole steps`() {
        assertEquals(26 * KG, Progression.deload(30 * KG, 2 * KG))
        assertEquals(45 * KG, Progression.deload(50 * KG, 5 * KG))
        assertEquals(0, Progression.deload(2 * KG, 5 * KG))
    }
}