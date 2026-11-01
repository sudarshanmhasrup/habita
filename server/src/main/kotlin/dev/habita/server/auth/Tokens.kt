package dev.habita.server.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Session tokens. The app keeps the token; the server keeps only its SHA-256 hash, so a leaked
 * database cannot be used to log in.
 */
object Tokens {
    private val random = SecureRandom()

    fun create(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(token: String): String = sha256(token)
}

fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }