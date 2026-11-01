package dev.habita.server

import dev.habita.server.auth.LOGIN_LIMIT
import dev.habita.server.auth.Passwords
import dev.habita.server.auth.SESSION_AUTH
import dev.habita.server.auth.Session
import dev.habita.server.auth.Tokens
import dev.habita.server.auth.authRoutes
import dev.habita.server.data.Store
import dev.habita.server.data.loadProgram
import dev.habita.server.data.openDatabase
import dev.habita.server.routes.exportRoutes
import dev.habita.server.routes.gymRoutes
import dev.habita.server.routes.syncRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    val program = loadProgram(config.programPath, store, clock())
    environment.log.info("Loaded program version ${program.version} from ${config.programPath}")

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
        register(LOGIN_LIMIT) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
        }
    }
    install(Authentication) {
        bearer(SESSION_AUTH) {
            authenticate { credential ->
                val tokenHash = Tokens.hash(credential.token)
                if (store.hasToken(tokenHash)) Session(tokenHash) else null
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
            syncRoutes(store)
            exportRoutes(store, clock)
        }
    }
}