package dev.habita.server

import dev.habita.server.auth.LOGIN_GLOBAL_LIMIT
import dev.habita.server.auth.LOGIN_LIMIT
import dev.habita.server.auth.PUBLISH_AUTH
import dev.habita.server.auth.Passwords
import dev.habita.server.auth.SESSION_AUTH
import dev.habita.server.auth.Session
import dev.habita.server.auth.Tokens
import dev.habita.server.auth.authRoutes
import dev.habita.server.auth.checkSession
import dev.habita.server.auth.passwordFingerprint
import dev.habita.server.data.Store
import dev.habita.server.data.loadProgram
import dev.habita.server.data.openDatabase
import dev.habita.server.routes.exportRoutes
import dev.habita.server.routes.gymRoutes
import dev.habita.server.routes.syncRoutes
import dev.habita.server.sync.SyncEngine
import dev.habita.server.updates.Releases
import dev.habita.server.updates.publishRoutes
import dev.habita.server.updates.releaseRoutes
import dev.habita.server.sync.gymTables
import dev.habita.server.sync.recordTables
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import kotlin.time.Duration.Companion.minutes

@Serializable
data class ErrorResponse(val error: String)

val HabitaJson = Json { ignoreUnknownKeys = true }

/**
 * Starts the server, or with `hash-password` prints a bcrypt hash for `HABITA_PASSWORD_HASH`.
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "hash-password") {
        val password = System.console()?.readPassword("Password: ")
            ?: readlnOrNull()?.toCharArray()
            ?: error("No password given")
        println(Passwords.hash(password))
        return
    }
    val config = ServerConfig.fromEnvironment()
    embeddedServer(Netty, port = config.port) { module(config) }.start(wait = true)
}

fun Application.module(config: ServerConfig, clock: () -> Long = System::currentTimeMillis) {
    val (driver, database) = openDatabase(config.databasePath)
    monitor.subscribe(ApplicationStopped) { driver.close() }
    val store = Store(database)
    val records = recordTables(database)
    // The position file sits next to the database but outside it, so a restored backup can't
    // bring an old one back with it.
    val sync = SyncEngine(database, gymTables(database) + records, File(config.databasePath + ".position"))
    val program = loadProgram(config.programPath, store, clock()) { environment.log.warn(it) }
    environment.log.info("Loaded program version ${program.version} from ${config.programPath}")
    val fingerprint = passwordFingerprint(config.passwordHash)
    store.deleteInvalidTokens(clock(), fingerprint)

    // Caddy appends the address it saw to X-Forwarded-For and drops what the client sent, so the
    // last entry is the real client. This is only safe because the server port is never published
    // (deploy/compose.yaml): every request comes through Caddy.
    install(XForwardedHeaders) {
        useLastProxy()
    }

    install(ContentNegotiation) {
        json(HabitaJson)
    }
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
        }
    }
    install(RateLimit) {
        // Per address, so a stranger can't lock the owner out. The overall ceiling only keeps a
        // spread-out attack from tying the CPU up in bcrypt (about 0.25 s a try): 120 a minute
        // takes two dozen addresses working together to fill, and phones already logged in
        // never need to log in, so filling it only delays a new device.
        register(LOGIN_LIMIT) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteAddress }
        }
        register(LOGIN_GLOBAL_LIMIT) {
            rateLimiter(limit = 120, refillPeriod = 1.minutes)
        }
    }
    val releases = Releases(File(config.releasesPath))
    install(Authentication) {
        // CI publishes builds with its own token, compared in constant time. No token, no publishing.
        bearer(PUBLISH_AUTH) {
            authenticate { credential ->
                val expected = config.publishToken ?: return@authenticate null
                if (MessageDigest.isEqual(credential.token.toByteArray(), expected.toByteArray())) UserIdPrincipal("ci") else null
            }
        }
        bearer(SESSION_AUTH) {
            authenticate { credential ->
                val tokenHash = Tokens.hash(credential.token)
                if (checkSession(store, tokenHash, fingerprint, clock())) Session(tokenHash) else null
            }
        }
    }

    routing {
        get("/health") {
            call.respondText("OK")
        }
        authRoutes(config, store, clock)
        authenticate(SESSION_AUTH) {
            gymRoutes(program, store, clock)
            syncRoutes(sync, clock)
            exportRoutes(store, records, clock)
            releaseRoutes(releases)
        }
        authenticate(PUBLISH_AUTH) {
            publishRoutes(releases, clock)
        }
    }
}