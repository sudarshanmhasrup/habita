package dev.habita.server

import java.io.File

/**
 * Server settings, read from environment variables so no secret lives in the repository.
 */
data class ServerConfig(
    val port: Int,
    val email: String,
    val passwordHash: String,
    val databasePath: String,
    val programPath: String,
    /** Where published Android builds are kept. */
    val releasesPath: String = File(databasePath).absoluteFile.parentFile.resolve("releases").path,
    /** The token CI publishes builds with; without one, publishing is off. */
    val publishToken: String? = null,
) {
    companion object {
        private val bcryptPattern = Regex("^\\$2[aby]\\$\\d{2}\\$.{53}$")

        fun fromEnvironment(environment: Map<String, String> = System.getenv()): ServerConfig {
            fun required(name: String) = environment[name]?.takeIf { it.isNotBlank() }
                ?: error("Missing environment variable $name")

            val passwordHash = required("HABITA_PASSWORD_HASH")
            require(bcryptPattern.matches(passwordHash)) {
                "HABITA_PASSWORD_HASH must be a bcrypt hash. Create one with the hash-password command."
            }
            return ServerConfig(
                port = environment["PORT"]?.toInt() ?: 8080,
                email = required("HABITA_EMAIL"),
                passwordHash = passwordHash,
                databasePath = environment["HABITA_DATABASE"] ?: ".build/habita.db",
                programPath = environment["HABITA_PROGRAM"] ?: "program/PROGRAM.md",
            ).let { config ->
                config.copy(
                    releasesPath = environment["HABITA_RELEASES"] ?: config.releasesPath,
                    publishToken = environment["HABITA_PUBLISH_TOKEN"]?.takeIf { it.isNotBlank() }?.also {
                        require(it.length >= 32) { "HABITA_PUBLISH_TOKEN must be at least 32 characters" }
                    },
                )
            }
        }
    }
}