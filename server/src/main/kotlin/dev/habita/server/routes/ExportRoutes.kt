package dev.habita.server.routes

import dev.habita.app.shared.api.Export
import dev.habita.server.data.Store
import dev.habita.server.sync.RecordTable
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Everything in one JSON file: every program version, every gym log and every other synced record,
 * including deleted ones.
 */
fun Route.exportRoutes(store: Store, records: List<RecordTable>, clock: () -> Long) {
    get("/export") {
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "habita-export.json").toString(),
        )
        call.respond(
            Export(
                exportedAt = clock(),
                programs = store.programs(),
                changes = store.all(),
                records = records.associate { it.collection to it.all() },
            ),
        )
    }
}