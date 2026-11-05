package dev.habita.server.auth

import at.favre.lib.crypto.bcrypt.BCrypt

object Passwords {
    fun hash(password: CharArray, cost: Int = 12): String =
        BCrypt.withDefaults().hashToString(cost, password)

    /**
     * Whether [password] matches [hash]. A password bcrypt can't take, such as one over 72 bytes,
     * is simply wrong, so it gets the same reply as any other wrong password.
     */
    fun verify(password: String, hash: String): Boolean =
        runCatching { BCrypt.verifyer().verify(password.toCharArray(), hash.toCharArray()).verified }.getOrDefault(false)
}