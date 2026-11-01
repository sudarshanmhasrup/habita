package dev.habita.server

import dev.habita.app.shared.api.LoginRequest
import dev.habita.app.shared.api.LoginResponse
import dev.habita.app.shared.api.SyncChanges
import dev.habita.app.shared.gym.SetLog
import dev.habita.app.shared.gym.WorkoutSession
import dev.habita.server.auth.Passwords
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.io.path.createTempFile

const val EMAIL = "me@example.com"
const val PASSWORD = "correct horse battery staple"
const val NOW = 1_800_000_000_000L
const val DAY = 24L * 60 * 60 * 1000
const val KG = 1000

/** A low bcrypt cost keeps tests fast; production hashes use the default of 12. */
val PASSWORD_HASH: String = Passwords.hash(PASSWORD.toCharArray(), cost = 4)

fun testConfig(
    databasePath: String = createTempFile("habita", ".db").toString(),
    programPath: String = "program/PROGRAM.md",
) = ServerConfig(
    port = 0,
    email = EMAIL,
    passwordHash = PASSWORD_HASH,
    databasePath = databasePath,
    programPath = programPath,
)

fun serverTest(
    config: ServerConfig = testConfig(),
    clock: () -> Long = { NOW },
    block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
) = testApplication {
    application { module(config, clock) }
    val client = createClient {
        install(ContentNegotiation) { json(HabitaJson) }
    }
    block(client)
}

suspend fun HttpClient.login(email: String = EMAIL, password: String = PASSWORD): HttpResponse =
    post("/auth/login") {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(email, password))
    }

suspend fun HttpClient.token(): String = login().body<LoginResponse>().token

suspend fun HttpClient.push(token: String, changes: SyncChanges): HttpResponse =
    post("/sync/push") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(changes)
    }

fun session(id: String, workoutId: String, startedAt: Long, endedAt: Long? = startedAt + 60 * 60 * 1000) =
    WorkoutSession(
        id = id,
        workoutId = workoutId,
        programVersion = 1,
        startedAt = startedAt,
        mainStartedAt = startedAt + 15 * 60 * 1000,
        endedAt = endedAt,
        updatedAt = startedAt,
    )

fun sets(sessionId: String, exerciseId: String, at: Long, weightGrams: Int, vararg results: Int?) =
    results.mapIndexed { index, achieved ->
        SetLog(
            id = "$sessionId-$exerciseId-$index",
            sessionId = sessionId,
            exerciseId = exerciseId,
            setNumber = index + 1,
            weightGrams = weightGrams,
            target = 12,
            achieved = achieved,
            completedAt = at + index * 1000,
            updatedAt = at,
        )
    }