package dev.habita.app.feature.auth

import dev.habita.app.core.Core
import dev.habita.app.shared.api.LoginRequest
import dev.habita.app.shared.api.LoginResponse

/** Logs in against the server and keeps the token. There is no signup. */
class AuthRepository(private val core: Core) {
    suspend fun login(email: String, password: String) {
        val response: LoginResponse = core.api.post("auth/login", LoginRequest(email.trim(), password))
        core.settings.saveToken(response.token)
    }
}