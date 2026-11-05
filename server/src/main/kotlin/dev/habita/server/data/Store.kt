package dev.habita.server.data

import dev.habita.app.shared.api.ExportedProgram
import dev.habita.app.shared.api.SyncChanges
import dev.habita.app.shared.gym.WorkoutSession
import dev.habita.server.auth.SessionPolicy
import dev.habita.server.db.Auth_token
import dev.habita.server.db.HabitaDatabase
import dev.habita.server.sync.toModel

/**
 * Reading the gym's records for targets, summaries and the export, plus program versions and
 * session tokens. Writing synced records goes through [dev.habita.server.sync.SyncEngine].
 */
class Store(private val database: HabitaDatabase) {
    private val sessions = database.workoutSessionQueries
    private val sets = database.setLogQueries
    private val warmUps = database.warmUpLogQueries
    private val steps = database.exerciseStepQueries

    /** Every gym record, including deleted ones. */
    fun all(): SyncChanges = database.transactionWithResult {
        SyncChanges(
            sessions = sessions.selectAll().executeAsList().map { it.toModel() },
            sets = sets.selectAll().executeAsList().map { it.toModel() },
            warmUps = warmUps.selectAll().executeAsList().map { it.toModel() },
            steps = steps.selectAll().executeAsList().map { it.toModel() },
        )
    }

    fun session(id: String): WorkoutSession? = sessions.selectById(id).executeAsOneOrNull()?.toModel()

    /** Stores [content] as a program version unless it is already known, and returns its id. */
    fun programVersion(contentHash: String, content: String, now: Long): Long = database.transactionWithResult {
        val programs = database.programVersionQueries
        programs.idByHash(contentHash).executeAsOneOrNull() ?: run {
            programs.insert(contentHash, content, now)
            programs.lastInsertedId().executeAsOne()
        }
    }

    fun programs(): List<ExportedProgram> =
        database.programVersionQueries.selectAll().executeAsList().map {
            ExportedProgram(version = it.id, content = it.content, createdAt = it.created_at)
        }

    fun saveToken(tokenHash: String, now: Long, passwordFingerprint: String) {
        database.authTokenQueries.insert(tokenHash, now, now, passwordFingerprint)
    }

    fun token(tokenHash: String): Auth_token? = database.authTokenQueries.byHash(tokenHash).executeAsOneOrNull()

    fun touchToken(tokenHash: String, now: Long) {
        database.authTokenQueries.touch(now, tokenHash)
    }

    fun deleteToken(tokenHash: String) {
        database.authTokenQueries.delete(tokenHash)
    }

    fun deleteAllTokens() {
        database.authTokenQueries.deleteAll()
    }

    /** Removes tokens that can no longer be used, so the table doesn't grow with every login. */
    fun deleteInvalidTokens(now: Long, passwordFingerprint: String) {
        database.authTokenQueries.deleteInvalid(
            createdBefore = now - SessionPolicy.maxAge,
            usedBefore = now - SessionPolicy.maxIdle,
            fingerprint = passwordFingerprint,
        )
    }
}