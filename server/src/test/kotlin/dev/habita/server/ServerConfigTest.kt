package dev.habita.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServerConfigTest {
    private val environment = mapOf("HABITA_EMAIL" to EMAIL, "HABITA_PASSWORD_HASH" to PASSWORD_HASH)

    @Test
    fun `reads the environment with defaults`() {
        val config = ServerConfig.fromEnvironment(environment)

        assertEquals(8080, config.port)
        assertEquals(EMAIL, config.email)
        assertEquals(".build/habita.db", config.databasePath)
        assertEquals("program/PROGRAM.md", config.programPath)
    }

    @Test
    fun `reads optional overrides`() {
        val config = ServerConfig.fromEnvironment(
            environment + mapOf("PORT" to "9000", "HABITA_DATABASE" to "/data/habita.db", "HABITA_PROGRAM" to "/app/PROGRAM.md"),
        )

        assertEquals(9000, config.port)
        assertEquals("/data/habita.db", config.databasePath)
        assertEquals("/app/PROGRAM.md", config.programPath)
    }

    @Test
    fun `fails without an email or password hash`() {
        assertFailsWith<IllegalStateException> { ServerConfig.fromEnvironment(environment - "HABITA_EMAIL") }
        assertFailsWith<IllegalStateException> { ServerConfig.fromEnvironment(environment - "HABITA_PASSWORD_HASH") }
    }

    @Test
    fun `rejects a plain text password in place of a hash`() {
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(environment + ("HABITA_PASSWORD_HASH" to PASSWORD))
        }
    }
}