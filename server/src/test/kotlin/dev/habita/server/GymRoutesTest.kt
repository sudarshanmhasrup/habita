package dev.habita.server

import dev.habita.app.shared.api.Export
import dev.habita.app.shared.api.NextWorkoutResponse
import dev.habita.app.shared.api.SyncChanges
import dev.habita.app.shared.api.WorkoutTargetsResponse
import dev.habita.app.shared.gym.ExerciseStep
import dev.habita.app.shared.gym.TargetReason
import dev.habita.app.shared.gym.WorkoutSummary
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GymRoutesTest {
    private val lastWeek = session("s1", "upper-a", NOW - 7 * DAY)
    private val history = SyncChanges(
        sessions = listOf(lastWeek),
        sets = sets("s1", "dumbbell-bench-press", NOW - 7 * DAY, 20 * KG, 12, 12, 12) +
            sets("s1", "seated-cable-row", NOW - 7 * DAY + 60_000, 40 * KG, 12, 11, 10),
    )

    @Test
    fun `suggests the next workout in the rotation`() = serverTest { client ->
        val token = client.token()
        suspend fun next() = client.get("/workouts/next") { bearerAuth(token) }.body<NextWorkoutResponse>().workoutId

        assertEquals("upper-a", next())
        client.push(token, history)
        assertEquals("lower-a", next())
    }

    @Test
    fun `targets follow the progression rules`() = serverTest { client ->
        val token = client.token()
        suspend fun targets() = client.get("/workouts/upper-a/targets") { bearerAuth(token) }
            .body<WorkoutTargetsResponse>().targets.associateBy { it.exerciseId }

        assertTrue(targets().values.all { it.reason == TargetReason.FIRST_TIME })

        client.push(token, history)
        val afterHistory = targets()
        assertEquals(TargetReason.INCREASE, afterHistory.getValue("dumbbell-bench-press").reason)
        assertEquals(22 * KG, afterHistory.getValue("dumbbell-bench-press").weightGrams)
        assertEquals(TargetReason.REPEAT, afterHistory.getValue("seated-cable-row").reason)
        assertEquals(40 * KG, afterHistory.getValue("seated-cable-row").weightGrams)

        client.push(token, SyncChanges(steps = listOf(ExerciseStep("dumbbell-bench-press", 2500, NOW))))
        assertEquals(22_500, targets().getValue("dumbbell-bench-press").weightGrams)
    }

    @Test
    fun `unknown workouts and sessions are not found`() = serverTest { client ->
        val token = client.token()

        assertEquals(HttpStatusCode.NotFound, client.get("/workouts/leg-day/targets") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/sessions/nope/summary") { bearerAuth(token) }.status)
    }

    @Test
    fun `summarises a finished session`() = serverTest { client ->
        val token = client.token()
        client.push(token, history)
        val today = session("s2", "upper-a", NOW)
        client.push(
            token,
            SyncChanges(
                sessions = listOf(today),
                sets = sets("s2", "dumbbell-bench-press", NOW, 22 * KG, 12, 10, 9) +
                    sets("s2", "seated-cable-row", NOW + 60_000, 40 * KG, 12, 12, null),
            ),
        )

        val summary = client.get("/sessions/s2/summary") { bearerAuth(token) }.body<WorkoutSummary>()

        assertEquals(60 * 60 * 1000L, summary.totalMillis)
        assertEquals(5, summary.setsCompleted)
        assertEquals(1, summary.setsSkipped)
        assertEquals(listOf("dumbbell-bench-press"), summary.exercisesProgressed)
        assertEquals(listOf("dumbbell-bench-press"), summary.personalBests)
        assertEquals(emptyList(), summary.nextIncreases)
        // Last week: 20 kg × 36 reps + 40 kg × 33 reps.
        assertEquals(2040.0, summary.previousVolumeKg)
    }

    @Test
    fun `export holds programs and every record`() = serverTest { client ->
        val token = client.token()
        client.push(token, history)

        val response = client.get("/export") { bearerAuth(token) }
        val export = response.body<Export>()

        assertTrue(response.headers[HttpHeaders.ContentDisposition]!!.contains("habita-export.json"))
        assertEquals(NOW, export.exportedAt)
        assertTrue(export.programs.single().content.contains("## Exercises"))
        assertEquals(history, export.changes)
    }
}