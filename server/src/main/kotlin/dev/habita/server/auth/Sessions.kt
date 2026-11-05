package dev.habita.server.auth

import dev.habita.server.data.Store
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * When a session token stops working: 90 days after login, after 30 days unused, or once the
 * password hash it was issued under changes. Mobile apps stay logged in through normal use and
 * only ask again after a long break. This is longer than NIST's 30-day reauthentication on purpose,
 * because the app works offline for weeks.
 */
object SessionPolicy {
    val maxAge = 90.days.inWholeMilliseconds
    val maxIdle = 30.days.inWholeMilliseconds

    /** How often to record use, so normal requests don't write to the database every time. */
    val touchEvery = 1.hours.inWholeMilliseconds
}

/** Ties tokens to the configured password hash, without storing the hash itself. */
fun passwordFingerprint(passwordHash: String): String = sha256("habita-session:$passwordHash")

/**
 * Whether the token with [tokenHash] may be used now. An expired token, or one from another
 * password, is deleted on the way.
 */
fun checkSession(store: Store, tokenHash: String, fingerprint: String, now: Long): Boolean {
    val token = store.token(tokenHash) ?: return false
    val valid = token.password_fingerprint == fingerprint &&
        now - token.created_at < SessionPolicy.maxAge &&
        now - token.last_used_at < SessionPolicy.maxIdle
    if (!valid) {
        store.deleteToken(tokenHash)
        return false
    }
    if (now - token.last_used_at >= SessionPolicy.touchEvery) store.touchToken(tokenHash, now)
    return true
}