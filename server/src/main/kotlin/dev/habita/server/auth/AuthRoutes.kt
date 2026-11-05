package dev.habita.server.auth

import dev.habita.app.shared.api.LoginRequest
import dev.habita.app.shared.api.LoginResponse
import dev.habita.server.ErrorResponse
import dev.habita.server.ServerConfig
import dev.habita.server.data.Store
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.security.MessageDigest

const val SESSION_AUTH = "session"

/** CI publishing a new Android build. */
const val PUBLISH_AUTH = "publish"

/** Login attempts from one client address. */
val LOGIN_LIMIT = RateLimitName("login")

/** Login attempts from everyone together, a ceiling for attacks spread over many addresses. */
val LOGIN_GLOBAL_LIMIT = RateLimitName("login-global")

/** The authenticated caller, identified by the hash of the token they sent. */
data class Session(val tokenHash: String)

/**
 * Login checks the email and the bcrypt hash from the environment. There is no signup: the one
 * account exists only in the server's configuration.
 */
fun Route.authRoutes(config: ServerConfig, store: Store, clock: () -> Long) {
    val fingerprint = passwordFingerprint(config.passwordHash)
    rateLimit(LOGIN_GLOBAL_LIMIT) {
        rateLimit(LOGIN_LIMIT) {
            post("/auth/login") {
                val request = call.receive<LoginRequest>()
                val emailMatches = MessageDigest.isEqual(
                    request.email.trim().lowercase().toByteArray(),
                    config.email.trim().lowercase().toByteArray(),
                )
                // Always run bcrypt, so a wrong email takes as long as a wrong password.
                val passwordMatches = Passwords.verify(request.password, config.passwordHash)
                if (!emailMatches || !passwordMatches) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Wrong email or password"))
                    return@post
                }
                val token = Tokens.create()
                store.saveToken(Tokens.hash(token), clock(), fingerprint)
                call.respond(LoginResponse(token))
            }
        }
    }

    authenticate(SESSION_AUTH) {
        post("/auth/logout") {
            store.deleteToken(call.principal<Session>()!!.tokenHash)
            call.respond(HttpStatusCode.NoContent)
        }

        // Ends every session, on every device, for example after losing a phone.
        post("/auth/logout-all") {
            store.deleteAllTokens()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}