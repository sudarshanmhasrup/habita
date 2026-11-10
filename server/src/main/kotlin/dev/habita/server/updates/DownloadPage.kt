package dev.habita.server.updates

import io.ktor.http.ContentType
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.security.MessageDigest
import java.util.Base64

/**
 * The page at `/download`, where a computer gets the Habita app for its platform. It holds no
 * data: it is a login form, and the builds it lists come from [releaseRoutes], which need the
 * token it logs in for. The page may only run its own script and only talk to this server.
 */
fun Route.downloadPage() {
    val html = Releases::class.java.getResource("/download.html")!!.readText()
    val script = Regex("""<script>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)!!.groupValues[1]
    val scriptHash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(script.encodeToByteArray()))
    val policy = listOf(
        "default-src 'none'",
        "script-src 'sha256-$scriptHash'",
        "style-src 'unsafe-inline'",
        "connect-src 'self'",
        "img-src 'self' data:",
        "form-action 'none'",
        "frame-ancestors 'none'",
        "base-uri 'none'",
    ).joinToString("; ")

    get("/download") {
        call.response.header("Content-Security-Policy", policy)
        call.response.header("Cache-Control", "no-store")
        call.respondText(html, ContentType.Text.Html)
    }
}