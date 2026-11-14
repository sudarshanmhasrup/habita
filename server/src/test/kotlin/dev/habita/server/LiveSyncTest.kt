package dev.habita.server

import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.SyncLive
import dev.habita.app.shared.api.SyncPushResult
import dev.habita.app.shared.api.SyncRecord
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class LiveSyncTest {
    private fun liveTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application { module(testConfig()) { NOW } }
        val client = createClient {
            install(ContentNegotiation) { json(HabitaJson) }
            install(WebSockets)
        }
        block(client)
    }

    private val expense = SyncRecord(
        id = "e1",
        updatedAt = NOW,
        data = buildJsonObject {
            put("amountPaise", 25_000)
            put("description", "Lunch")
        },
    )

    private suspend fun WebSocketSession.nextCursor(): Long = withTimeout(10_000) {
        val frame = incoming.receive()
        HabitaJson.decodeFromString(SyncLive.serializer(), (frame as Frame.Text).readText()).cursor
    }

    @Test
    fun `a socket hears the new position when another device pushes`() = liveTest { client ->
        val phone = client.token()
        val laptop = client.token()

        client.webSocket("/sync/live", request = { bearerAuth(phone) }) {
            assertEquals(0, nextCursor())

            val pushed = client.push(laptop, mapOf(SyncCollections.EXPENSES to listOf(expense))).body<SyncPushResult>()

            assertEquals(pushed.cursor, nextCursor())
        }
    }

    @Test
    fun `a push that saves nothing sends nothing new`() = liveTest { client ->
        val token = client.token()
        val first = client.push(token, mapOf(SyncCollections.EXPENSES to listOf(expense))).body<SyncPushResult>()

        client.webSocket("/sync/live", request = { bearerAuth(token) }) {
            assertEquals(first.cursor, nextCursor())
            // The same record again doesn't win, so the position stays and no frame follows it.
            client.push(token, mapOf(SyncCollections.EXPENSES to listOf(expense)))
            val second = client.push(token, mapOf(SyncCollections.EXPENSES to listOf(expense.copy(updatedAt = NOW + 1))))
                .body<SyncPushResult>()

            assertEquals(first.cursor + 1, second.cursor)
            assertEquals(second.cursor, nextCursor())
        }
    }

    @Test
    fun `a socket without a valid token is refused`() = liveTest { client ->
        // A plain request isn't an upgrade, so it never reaches the socket.
        assertFalse(client.get("/sync/live").status.isSuccess())
        assertFailsWith<Exception> {
            client.webSocket("/sync/live") { incoming.receive() }
        }
        assertFailsWith<Exception> {
            client.webSocket("/sync/live", request = { bearerAuth("not-a-token") }) { incoming.receive() }
        }
    }

    @Test
    fun `a socket closes once its token is logged out`() = liveTest { client ->
        val phone = client.token()
        val laptop = client.token()

        client.webSocket("/sync/live", request = { bearerAuth(phone) }) {
            nextCursor()
            assertEquals(HttpStatusCode.NoContent, client.post("/auth/logout") { bearerAuth(phone) }.status)
            client.push(laptop, mapOf(SyncCollections.EXPENSES to listOf(expense)))

            val reason = withTimeout(10_000) { closeReason.await() }
            assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code)
        }
    }
}