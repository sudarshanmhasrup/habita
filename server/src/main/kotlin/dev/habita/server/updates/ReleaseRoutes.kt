package dev.habita.server.updates

import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receiveStream
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the apps and the download page ask, for each platform: is there a newer build, and the
 * build itself. Needs a login.
 */
fun Route.releaseRoutes(releases: Map<Platform, Releases>) {
    get("/app/{platform}/latest") {
        val latest = (call.releasesFor(releases) ?: return@get call.respond(HttpStatusCode.NotFound)).latest()
        if (latest == null) call.respond(HttpStatusCode.NoContent) else call.respond(latest)
    }

    get("/app/{platform}/download") {
        call.respondBuild(call.releasesFor(releases) ?: return@get call.respond(HttpStatusCode.NotFound))
    }

    // The address Android apps from before desktop builds download from.
    get("/app/android/apk") {
        call.respondBuild(releases.getValue(Platform.ANDROID))
    }
}

/**
 * Where CI publishes a new build: the file as the body, its version in headers. Needs the publish
 * token, not a login.
 */
fun Route.publishRoutes(releases: Map<Platform, Releases>, clock: () -> Long) {
    post("/app/{platform}/releases") {
        val target = call.releasesFor(releases) ?: return@post call.respond(HttpStatusCode.NotFound)
        val versionCode = call.request.headers["X-Version-Code"]?.toIntOrNull()?.takeIf { it > 0 }
            ?: throw BadRequestException("X-Version-Code must be a positive number")
        val versionName = call.request.headers["X-Version-Name"] ?: throw BadRequestException("X-Version-Name is missing")
        val length = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (length != null && length > target.platform.maxBytes) throw BadRequestException("The build is too large")
        val release = withContext(Dispatchers.IO) {
            runCatching { call.receiveStream().use { target.publish(it, versionCode, versionName, clock()) } }
        }.getOrElse { throw BadRequestException(it.message ?: "Couldn't publish") }
        call.respond(HttpStatusCode.Created, release)
    }
}

/** The platform the address names, or null for one Habita doesn't publish. */
private fun ApplicationCall.releasesFor(releases: Map<Platform, Releases>): Releases? =
    Platform.byId(parameters["platform"])?.let(releases::getValue)

private suspend fun ApplicationCall.respondBuild(releases: Releases) {
    val file = releases.latest()?.let(releases::file)?.takeIf { it.isFile }
    if (file == null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, file.name).toString(),
    )
    respondFile(file)
}