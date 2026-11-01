package dev.habita.server

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthTest {
    @Test
    fun `login returns a token that opens protected routes`() = serverTest { client ->
        val token = client.token()

        assertEquals(HttpStatusCode.OK, client.get("/program") { bearerAuth(token) }.status)
    }

    @Test
    fun `email is matched without case or surrounding spaces`() = serverTest { client ->
        assertEquals(HttpStatusCode.OK, client.login(email = "  ME@Example.com ").status)
    }

    @Test
    fun `wrong password or email is rejected`() = serverTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.login(password = "wrong").status)
        assertEquals(HttpStatusCode.Unauthorized, client.login(email = "someone@example.com").status)
    }

    @Test
    fun `protected routes need a valid token`() = serverTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/program").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/program") { bearerAuth("made-up") }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/export").status)
    }

    @Test
    fun `logout revokes the token`() = serverTest { client ->
        val token = client.token()

        assertEquals(HttpStatusCode.NoContent, client.post("/auth/logout") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/program") { bearerAuth(token) }.status)
    }

    @Test
    fun `too many login attempts are rate limited`() = serverTest { client ->
        repeat(5) { client.login(password = "wrong") }

        assertEquals(HttpStatusCode.TooManyRequests, client.login().status)
    }
}