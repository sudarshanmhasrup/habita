package dev.habita.server.routes

import dev.habita.app.shared.api.Export
import dev.habita.server.data.Store
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Everything in one JSON file: every program version and every log, including deleted records.
 */
fun Route.exportRoutes(store: Store, clock: () -> Long) {
    get("/export") {
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "habita-export.json").toString(),
        )
        call.respond(Export(exportedAt = clock(), programs = store.programs(), changes = store.all()))
    }
}