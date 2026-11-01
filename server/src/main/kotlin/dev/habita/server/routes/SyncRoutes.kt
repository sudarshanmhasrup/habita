package dev.habita.server.routes

import dev.habita.app.shared.api.PushResponse
import dev.habita.app.shared.api.SyncChanges
import dev.habita.server.data.Store
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * The app pushes its local changes, then pulls everything after the cursor it last saw. Pushing
 * the same records twice is harmless.
 */
fun Route.syncRoutes(store: Store) {
    post("/sync/push") {
        val changes = call.receive<SyncChanges>()
        validate(changes)
        call.respond(PushResponse(store.push(changes)))
    }

    get("/sync/pull") {
        val since = call.request.queryParameters["since"]?.let {
            requireNotNull(it.toLongOrNull()) { "since must be a number" }
        } ?: 0
        call.respond(store.pull(since))
    }
}

private fun validate(changes: SyncChanges) {
    changes.sessions.forEach {
        require(it.id.isNotBlank() && it.workoutId.isNotBlank()) { "Session ids must not be blank" }
    }
    changes.sets.forEach {
        require(it.id.isNotBlank() && it.sessionId.isNotBlank() && it.exerciseId.isNotBlank()) {
            "Set ids must not be blank"
        }
        require(it.setNumber > 0 && it.weightGrams >= 0 && it.target > 0 && (it.achieved ?: 0) >= 0) {
            "Set ${it.id} has an invalid number"
        }
    }
    changes.warmUps.forEach {
        require(it.id.isNotBlank() && it.sessionId.isNotBlank() && it.position >= 0) {
            "Warm-up ${it.id} is invalid"
        }
    }
    changes.steps.forEach {
        require(it.exerciseId.isNotBlank() && it.step > 0) { "Step for ${it.exerciseId} is invalid" }
    }
}