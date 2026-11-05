package dev.habita.server

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.habita.server.auth.Passwords
import dev.habita.server.db.HabitaDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import dev.habita.app.shared.api.LoginRequest
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionTest {
    private suspend fun HttpClient.loginFrom(address: String, password: String = PASSWORD) =
        post("/auth/login") {
            header("X-Forwarded-For", address)
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(EMAIL, password))
        }

    @Test
    fun `one address hitting the login limit doesn't lock out another`() = serverTest { client ->
        repeat(5) { client.loginFrom("203.0.113.7", password = "wrong") }

        assertEquals(HttpStatusCode.TooManyRequests, client.loginFrom("203.0.113.7").status)
        assertEquals(HttpStatusCode.OK, client.loginFrom("198.51.100.20").status)
    }

    @Test
    fun `the last forwarded address is the one limited`() = serverTest { client ->
        // A client can put anything first; Caddy appends the address it really saw.
        repeat(5) { index ->
            client.post("/auth/login") {
                header("X-Forwarded-For", "10.0.0.$index, 203.0.113.7")
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(EMAIL, "wrong"))
            }
        }

        assertEquals(HttpStatusCode.TooManyRequests, client.loginFrom("203.0.113.7").status)
    }

    @Test
    fun `logins from many addresses share a ceiling`() = serverTest { client ->
        repeat(120) { index -> client.loginFrom("203.0.${index / 100}.${index % 100}", password = "wrong") }

        assertEquals(HttpStatusCode.TooManyRequests, client.loginFrom("198.51.100.20").status)
    }

    @Test
    fun `a password too long for bcrypt is just wrong`() = serverTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.login(password = "x".repeat(200)).status)
    }

    @Test
    fun `a token ends 90 days after login even when used daily`() {
        var now = NOW
        serverTest(clock = { now }) { client ->
            val token = client.token()
            repeat(89) {
                now += DAY
                assertEquals(HttpStatusCode.OK, client.get("/program") { bearerAuth(token) }.status)
            }
            now += DAY

            assertEquals(HttpStatusCode.Unauthorized, client.get("/program") { bearerAuth(token) }.status)
        }
    }

    @Test
    fun `a token ends after 30 days unused`() {
        var now = NOW
        serverTest(clock = { now }) { client ->
            val token = client.token()
            now += 29 * DAY
            assertEquals(HttpStatusCode.OK, client.get("/program") { bearerAuth(token) }.status)
            now += 30 * DAY

            assertEquals(HttpStatusCode.Unauthorized, client.get("/program") { bearerAuth(token) }.status)
        }
    }

    @Test
    fun `changing the password ends every session`() {
        val config = testConfig()
        lateinit var token: String
        serverTest(config) { client -> token = client.token() }

        val newPassword = "a whole new password"
        val changed = config.copy(passwordHash = Passwords.hash(newPassword.toCharArray(), cost = 4))
        serverTest(changed) { client ->
            assertEquals(HttpStatusCode.Unauthorized, client.get("/program") { bearerAuth(token) }.status)
            assertEquals(HttpStatusCode.OK, client.login(password = newPassword).status)
        }
    }

    @Test
    fun `logging out everywhere ends every token`() = serverTest { client ->
        val phone = client.token()
        val tablet = client.token()

        assertEquals(HttpStatusCode.NoContent, client.post("/auth/logout-all") { bearerAuth(phone) }.status)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/program") { bearerAuth(phone) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/program") { bearerAuth(tablet) }.status)
    }

    @Test
    fun `tokens from before the upgrade migrate and then need a new login`() {
        val path = createTempFile("habita", ".db").toString()
        // The token table as version 2 of the schema had it.
        JdbcSqliteDriver("jdbc:sqlite:$path").use { driver ->
            driver.execute(null, "CREATE TABLE auth_token (token_hash TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL)", 0)
            driver.execute(null, "INSERT INTO auth_token VALUES ('old', $NOW)", 0)
            HabitaDatabase.Schema.migrate(driver, 2, 3)
            val row = HabitaDatabase(driver).authTokenQueries.byHash("old").executeAsOne()

            assertEquals(NOW, row.last_used_at)
            assertEquals(null, row.password_fingerprint)
        }
    }
}