package dev.habita.server.routes

import dev.habita.app.shared.api.SyncLive
import dev.habita.app.shared.api.SyncPush
import dev.habita.server.HabitaJson
import dev.habita.server.auth.Session
import dev.habita.server.sync.SyncEngine
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** How often a live socket repeats the position when nothing changes, so the app knows it's alive. */
val LIVE_HEARTBEAT = 30.seconds

/**
 * The app pushes its local changes, then pulls everything after the cursor it last saw, page by
 * page. Pushing the same records twice is harmless, and the server refuses bad records one by one
 * instead of failing the whole push.
 *
 * [loggedIn] says whether the session with a token hash still holds, so a live socket closes once
 * its token is logged out.
 */
fun Route.syncRoutes(engine: SyncEngine, clock: () -> Long, loggedIn: (tokenHash: String) -> Boolean) {
    post("/sync/push") {
        val push = call.receive<SyncPush>()
        if (push.collections.values.sumOf { it.size } > SyncEngine.MAX_PUSH) {
            throw BadRequestException("Push at most ${SyncEngine.MAX_PUSH} records at once")
        }
        call.respond(engine.push(push, clock()))
    }

    get("/sync/pull") {
        val since = call.request.queryParameters["since"]?.let {
            it.toLongOrNull() ?: throw BadRequestException("since must be a number")
        } ?: 0
        val limit = call.request.queryParameters["limit"]?.let {
            it.toIntOrNull()?.takeIf { limit -> limit > 0 } ?: throw BadRequestException("limit must be a positive number")
        } ?: SyncEngine.MAX_PULL
        call.respond(engine.pull(since, minOf(limit, SyncEngine.MAX_PULL)))
    }

    // Tells the app when there is something new to pull: only the server position, never data.
    // It sends the position on connect, after every push that saved something, and every
    // LIVE_HEARTBEAT when nothing changes. The app never sends anything.
    webSocket("/sync/live") {
        val tokenHash = call.principal<Session>()!!.tokenHash
        val updates = launch {
            var sent: Long? = null
            while (true) {
                val cursor = withTimeoutOrNull(LIVE_HEARTBEAT) { engine.position.first { it != sent } }
                    ?: engine.position.value
                if (!loggedIn(tokenHash)) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Logged out"))
                    return@launch
                }
                send(Frame.Text(HabitaJson.encodeToString(SyncLive.serializer(), SyncLive(cursor))))
                sent = cursor
            }
        }
        try {
            for (frame in incoming) Unit
        } finally {
            updates.cancel()
        }
    }
}