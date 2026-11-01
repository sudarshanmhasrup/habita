package dev.habita.server.auth

import at.favre.lib.crypto.bcrypt.BCrypt

object Passwords {
    fun hash(password: CharArray, cost: Int = 12): String =
        BCrypt.withDefaults().hashToString(cost, password)

    fun verify(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash.toCharArray()).verified
}