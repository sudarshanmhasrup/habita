package dev.habita.server

/**
 * Server settings, read from environment variables so no secret lives in the repository.
 */
data class ServerConfig(
    val port: Int,
    val email: String,
    val passwordHash: String,
    val databasePath: String,
    val programPath: String,
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
            )
        }
    }
}