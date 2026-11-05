package dev.habita.server.updates

import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** What the app asks: is there a newer build, and the build itself. Needs a login. */
fun Route.releaseRoutes(releases: Releases) {
    get("/app/android/latest") {
        val latest = releases.latest()
        if (latest == null) call.respond(HttpStatusCode.NoContent) else call.respond(latest)
    }

    get("/app/android/apk") {
        val latest = releases.latest()
        val file = latest?.let(releases::apk)?.takeIf { it.isFile }
        if (file == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, file.name).toString(),
        )
        call.respondFile(file)
    }
}

/**
 * Where CI publishes a new build: the APK as the body, its version in headers. Needs the publish
 * token, not a login.
 */
fun Route.publishRoutes(releases: Releases, clock: () -> Long) {
    post("/app/android/releases") {
        val versionCode = call.request.headers["X-Version-Code"]?.toIntOrNull()?.takeIf { it > 0 }
            ?: throw BadRequestException("X-Version-Code must be a positive number")
        val versionName = call.request.headers["X-Version-Name"] ?: throw BadRequestException("X-Version-Name is missing")
        val length = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (length != null && length > MAX_APK_BYTES) throw BadRequestException("The APK is too large")
        val apk = call.receive<ByteArray>()
        if (apk.size > MAX_APK_BYTES) throw BadRequestException("The APK is too large")
        val release = runCatching { releases.publish(apk, versionCode, versionName, clock()) }
            .getOrElse { throw BadRequestException(it.message ?: "Couldn't publish") }
        call.respond(HttpStatusCode.Created, release)
    }
}

private const val MAX_APK_BYTES = 100L * 1024 * 1024