package dev.habita.app.shared.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EffortTest {
    private val now = 100 * DAY

    private fun felt(performance: Performance, vararg efforts: Effort?) =
        performance.copy(sets = performance.sets.mapIndexed { index, set -> set.copy(effort = efforts.getOrNull(index)) })

    private fun bench(vararg history: Performance) =
        Progression.target(benchPress, benchSlot, benchPress.defaultStep, history.toList(), now)

    private fun set(weightGrams: Int, achieved: Int?, effort: Effort?) =
        sets("s", "bench", now, weightGrams, 12, achieved).single().copy(effort = effort)

    @Test
    fun `every set easy at target goes up two steps`() {
        val last = felt(performance("s1", now - 3 * DAY, 20 * KG, 12, 12, 12, 12), Effort.EASY, Effort.EASY, Effort.EASY)

        val target = bench(last)

        assertEquals(TargetReason.INCREASE_EASY, target.reason)
        assertEquals(24 * KG, target.weightGrams)
    }

    @Test
    fun `every set hard at target holds the weight`() {
        val last = felt(performance("s1", now - 3 * DAY, 20 * KG, 12, 12, 12, 12), Effort.HARD, Effort.HARD, Effort.HARD)

        val target = bench(last)

        assertEquals(TargetReason.HOLD_HARD, target.reason)
        assertEquals(20 * KG, target.weightGrams)
    }

    @Test
    fun `mixed effort at target goes up one step`() {
        val last = felt(performance("s1", now - 3 * DAY, 20 * KG, 12, 12, 12, 12), Effort.EASY, Effort.MEDIUM, Effort.HARD)

        assertEquals(TargetReason.INCREASE, bench(last).reason)
    }

    @Test
    fun `missed reps that all felt easy still go up one step`() {
        val last = felt(performance("s1", now - 3 * DAY, 20 * KG, 12, 10, 10, 10), Effort.EASY, Effort.EASY, Effort.EASY)

        val target = bench(last)

        assertEquals(TargetReason.FELT_EASY, target.reason)
        assertEquals(22 * KG, target.weightGrams)
    }

    @Test
    fun `a jump mid-session makes the heavier weight the one to beat`() {
        // Planned 5 kg, the first set was easy, so the rest were done at 15 kg.
        val first = set(5 * KG, 8, Effort.EASY)
        val rest = sets("s1", "bench", now - 3 * DAY + 10_000, 15 * KG, 12, 12, 12)
            .map { it.copy(setNumber = it.setNumber + 1, effort = Effort.MEDIUM) }
        val last = Performance("s1", now - 3 * DAY, listOf(first.copy(sessionId = "s1", completedAt = now - 3 * DAY)) + rest)

        val target = bench(last)

        assertEquals(15 * KG, target.weightGrams)
        assertEquals(TargetReason.REPEAT, target.reason)
    }

    @Test
    fun `an easy set suggests one step up for the next set`() {
        val advice = Progression.nextSet(benchPress, 12, 2 * KG, set(5 * KG, 12, Effort.EASY))!!

        assertEquals(7 * KG, advice.weightGrams)
        assertEquals(5 * KG, advice.previousWeightGrams)
        assertEquals(SetAdviceReason.EASY, advice.reason)
    }

    @Test
    fun `an easy set well over target suggests two steps up`() {
        val advice = Progression.nextSet(benchPress, 12, 2 * KG, set(5 * KG, 16, Effort.EASY))!!

        assertEquals(9 * KG, advice.weightGrams)
    }

    @Test
    fun `a hard set with too few reps suggests one step down`() {
        val advice = Progression.nextSet(benchPress, 12, 2 * KG, set(20 * KG, 6, Effort.HARD))!!

        assertEquals(18 * KG, advice.weightGrams)
        assertEquals(SetAdviceReason.TOO_HARD, advice.reason)
    }

    @Test
    fun `a hard set that hit the reps keeps the weight`() {
        val advice = Progression.nextSet(benchPress, 12, 2 * KG, set(20 * KG, 12, Effort.HARD))!!

        assertEquals(20 * KG, advice.weightGrams)
        assertEquals(SetAdviceReason.KEEP, advice.reason)
    }

    @Test
    fun `an easy plank adds seconds instead of weight`() {
        val advice = Progression.nextSet(plank, 30, 5, set(0, 30, Effort.EASY))!!

        assertEquals(35, advice.target)
        assertEquals(0, advice.weightGrams)
    }

    @Test
    fun `the warm-up set is about half, rounded to the step`() {
        assertEquals(8 * KG, Progression.warmUpWeight(15 * KG, 2 * KG))
        assertEquals(10 * KG, Progression.warmUpWeight(20 * KG, 5 * KG))
        assertEquals(2 * KG, Progression.warmUpWeight(2 * KG, 2 * KG))
    }

    @Test
    fun `warm-up sets never count toward progression`() {
        val working = sets("s1", "bench", now - 3 * DAY, 20 * KG, 12, 12, 12, 12)
        val warmUp = sets("s1", "bench", now - 3 * DAY - 1000, 10 * KG, 10, 10).map { it.copy(id = "w", setNumber = 0, warmUp = true) }
        val history = History.performances("bench", listOf(session("s1", "a", now - 3 * DAY)), warmUp + working)

        assertEquals(3, history.single().sets.size)
        assertEquals(TargetReason.INCREASE, bench(*history.toTypedArray()).reason)
    }

    @Test
    fun `a skipped set gives no advice`() {
        assertNull(Progression.nextSet(benchPress, 12, 2 * KG, set(20 * KG, null, null)))
    }
}