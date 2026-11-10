package dev.habita.app.feature.gym.ui

import dev.habita.app.feature.gym.data.SessionReport
import dev.habita.app.shared.gym.WorkoutSummary
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ShareCardTest {
    private fun report(volumeKg: Double, sets: Int, bests: Int, minutes: Long) = SessionReport(
        summary = WorkoutSummary(
            sessionId = "s1",
            totalMillis = minutes * 60_000,
            warmUpMillis = 0,
            mainMillis = minutes * 60_000,
            volumeKg = volumeKg,
            previousVolumeKg = null,
            volumeChangePercent = null,
            exercisesProgressed = emptyList(),
            personalBests = List(bests) { "e$it" },
            setsCompleted = sets,
            setsSkipped = 0,
            nextIncreases = emptyList(),
        ),
        workoutName = "Lower Body A",
        exercises = emptyList(),
        volumeTrend = emptyList(),
        efforts = emptyMap(),
    )

    @Test
    fun aWeightedWorkoutLeadsWithKilogramsLifted() {
        val text = ShareCardText.of(report(volumeKg = 7848.4, sets = 17, bests = 1, minutes = 58), "10 Nov")
        assertEquals("10 NOV · LOWER BODY A", text.overline)
        assertEquals("7,848", text.number)
        assertEquals("KG LIFTED", text.unit)
        assertEquals(listOf("58 min", "17 sets", "1 new best"), text.stats)
    }

    @Test
    fun aDayWithNoWeightsLeadsWithSetsAndLeavesOutZeroBests() {
        val text = ShareCardText.of(report(volumeKg = 0.0, sets = 1, bests = 0, minutes = 65), "3 Nov")
        assertEquals("1", text.number)
        assertEquals("SET DONE", text.unit)
        assertEquals(listOf("1 h 5 min", "1 set"), text.stats)
    }

    @Test
    fun popIsTheFirstFilter() {
        assertEquals(ShareFilter.POP, ShareFilter.entries.first())
        assertEquals(10, ShareFilter.entries.size)
    }

    @Test
    fun chainedLooksApplyInOrder() {
        // Brightness 0.5, then lift by 10: a channel of 200 becomes 200 × 0.5 + 10 = 110.
        val chained = Looks.then(Looks.brightness(0.5f), Looks.lift(1f, 10f))
        assertEquals(0.5f, chained[0])
        assertEquals(10f, chained[4])
        // The other way round: (200 + 10) × 0.5 = 105, so the offset is halved too.
        val reversed = Looks.then(Looks.lift(1f, 10f), Looks.brightness(0.5f))
        assertEquals(5f, reversed[4])
    }

    @Test
    fun aDuotoneMapsBlackAndWhiteToItsTwoColours() {
        val m = Looks.duotone(0x102030, 0xF0E0D0)
        // Black in: only the offsets are left, which are the dark colour.
        assertContentEquals(floatArrayOf(16f, 32f, 48f), floatArrayOf(m[4], m[9], m[14]))
        // White in (255 on every channel): the light colour.
        val white = listOf(0, 5, 10).map { row -> (m[row] + m[row + 1] + m[row + 2]) * 255 + m[row + 4] }
        assertEquals(listOf(240f, 224f, 208f), white.map { kotlin.math.round(it) })
    }
}