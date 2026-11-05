package dev.habita.server.routes

import dev.habita.app.shared.api.SyncPush
import dev.habita.server.sync.SyncEngine
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * The app pushes its local changes, then pulls everything after the cursor it last saw, page by
 * page. Pushing the same records twice is harmless, and the server refuses bad records one by one
 * instead of failing the whole push.
 */
fun Route.syncRoutes(engine: SyncEngine, clock: () -> Long) {
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
}