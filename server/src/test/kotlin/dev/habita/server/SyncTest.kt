package dev.habita.server

import dev.habita.app.shared.api.PullResponse
import dev.habita.app.shared.api.PushResponse
import dev.habita.app.shared.api.SyncChanges
import dev.habita.app.shared.gym.ExerciseStep
import dev.habita.app.shared.gym.WarmUpLog
import dev.habita.app.shared.gym.WarmUpStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncTest {
    private val session = session("s1", "upper-a", NOW)
    private val changes = SyncChanges(
        sessions = listOf(session),
        sets = sets("s1", "dumbbell-bench-press", NOW, 20 * KG, 12, 12, null),
        warmUps = listOf(WarmUpLog("w1", "s1", 0, WarmUpStatus.DONE, NOW, NOW)),
        steps = listOf(ExerciseStep("dumbbell-bench-press", 2500, NOW)),
    )

    private suspend fun HttpClient.pull(token: String, since: Long = 0) =
        get("/sync/pull?since=$since") { bearerAuth(token) }.body<PullResponse>()

    @Test
    fun `pushed records come back on pull`() = serverTest { client ->
        val token = client.token()

        val cursor = client.push(token, changes).body<PushResponse>().cursor
        val pulled = client.pull(token)

        assertEquals(6, cursor)
        assertEquals(cursor, pulled.cursor)
        assertEquals(changes, pulled.changes)
        assertNull(pulled.changes.sets.last().achieved)
    }

    @Test
    fun `pushing the same records twice changes nothing`() = serverTest { client ->
        val token = client.token()

        val first = client.push(token, changes).body<PushResponse>().cursor
        val second = client.push(token, changes).body<PushResponse>().cursor

        assertEquals(first, second)
        assertEquals(changes, client.pull(token).changes)
    }

    @Test
    fun `the newest update wins`() = serverTest { client ->
        val token = client.token()
        client.push(token, changes)

        val newer = session.copy(endedAt = NOW + 2 * 60 * 60 * 1000, updatedAt = NOW + 10)
        val older = session.copy(workoutId = "lower-a", updatedAt = NOW - 10)
        client.push(token, SyncChanges(sessions = listOf(newer)))
        client.push(token, SyncChanges(sessions = listOf(older)))

        assertEquals(newer, client.pull(token).changes.sessions.single())
    }

    @Test
    fun `pull returns only what changed after the cursor`() = serverTest { client ->
        val token = client.token()
        val cursor = client.push(token, changes).body<PushResponse>().cursor

        val deleted = changes.sets.first().copy(deleted = true, updatedAt = NOW + 1)
        client.push(token, SyncChanges(sets = listOf(deleted)))
        val pulled = client.pull(token, since = cursor)

        assertEquals(SyncChanges(sets = listOf(deleted)), pulled.changes)
        assertEquals(cursor + 1, pulled.cursor)
    }

    @Test
    fun `invalid records are rejected`() = serverTest { client ->
        val token = client.token()
        val invalid = SyncChanges(sets = listOf(changes.sets.first().copy(weightGrams = -1)))

        assertEquals(HttpStatusCode.BadRequest, client.push(token, invalid).status)
        assertEquals(SyncChanges(), client.pull(token).changes)
    }

    @Test
    fun `malformed json and cursors are rejected`() = serverTest { client ->
        val token = client.token()
        val response = client.post("/sync/push") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("{ not json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/sync/pull?since=abc") { bearerAuth(token) }.status)
    }

    @Test
    fun `data survives a restart`() {
        val config = testConfig()
        serverTest(config) { client -> client.push(client.token(), changes) }

        serverTest(config) { client ->
            assertEquals(changes, client.pull(client.token()).changes)
        }
    }
}