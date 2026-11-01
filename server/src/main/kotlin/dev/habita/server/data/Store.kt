package dev.habita.server.data

import dev.habita.app.shared.api.ExportedProgram
import dev.habita.app.shared.api.PullResponse
import dev.habita.app.shared.api.SyncChanges
import dev.habita.app.shared.gym.ExerciseStep
import dev.habita.app.shared.gym.SetLog
import dev.habita.app.shared.gym.WarmUpLog
import dev.habita.app.shared.gym.WarmUpStatus
import dev.habita.app.shared.gym.WorkoutSession
import dev.habita.server.db.Exercise_step
import dev.habita.server.db.HabitaDatabase
import dev.habita.server.db.Set_log
import dev.habita.server.db.Warm_up_log
import dev.habita.server.db.Workout_session

/**
 * All database access. Synced records use last-write-wins on `updatedAt`, and every accepted change
 * gets the next server sequence number so the app can pull what changed after its cursor.
 */
class Store(private val database: HabitaDatabase) {
    private val sessions = database.workoutSessionQueries
    private val sets = database.setLogQueries
    private val warmUps = database.warmUpLogQueries
    private val steps = database.exerciseStepQueries

    /** Saves [changes] and returns the cursor after them. */
    fun push(changes: SyncChanges): Long = database.transactionWithResult {
        changes.sessions.forEach {
            if (isNewer(sessions.updatedAt(it.id).executeAsOneOrNull(), it.updatedAt)) {
                sessions.save(
                    it.id, it.workoutId, it.programVersion, it.startedAt, it.mainStartedAt, it.endedAt,
                    it.updatedAt, it.deleted.toLong(), nextSeq(),
                )
            }
        }
        changes.sets.forEach {
            if (isNewer(sets.updatedAt(it.id).executeAsOneOrNull(), it.updatedAt)) {
                sets.save(
                    it.id, it.sessionId, it.exerciseId, it.setNumber.toLong(), it.weightGrams.toLong(),
                    it.target.toLong(), it.achieved?.toLong(), it.completedAt, it.updatedAt,
                    it.deleted.toLong(), nextSeq(),
                )
            }
        }
        changes.warmUps.forEach {
            if (isNewer(warmUps.updatedAt(it.id).executeAsOneOrNull(), it.updatedAt)) {
                warmUps.save(
                    it.id, it.sessionId, it.position.toLong(), it.status.name, it.completedAt,
                    it.updatedAt, it.deleted.toLong(), nextSeq(),
                )
            }
        }
        changes.steps.forEach {
            if (isNewer(steps.updatedAt(it.exerciseId).executeAsOneOrNull(), it.updatedAt)) {
                steps.save(it.exerciseId, it.step.toLong(), it.updatedAt, nextSeq())
            }
        }
        database.syncQueries.current().executeAsOne()
    }

    fun pull(since: Long): PullResponse = database.transactionWithResult {
        PullResponse(
            cursor = database.syncQueries.current().executeAsOne(),
            changes = SyncChanges(
                sessions = sessions.changedSince(since).executeAsList().map { it.toModel() },
                sets = sets.changedSince(since).executeAsList().map { it.toModel() },
                warmUps = warmUps.changedSince(since).executeAsList().map { it.toModel() },
                steps = steps.changedSince(since).executeAsList().map { it.toModel() },
            ),
        )
    }

    /** Every synced record, including deleted ones. */
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

    fun saveToken(tokenHash: String, now: Long) {
        database.authTokenQueries.insert(tokenHash, now)
    }

    fun hasToken(tokenHash: String): Boolean = database.authTokenQueries.exists(tokenHash).executeAsOne() > 0

    fun deleteToken(tokenHash: String) {
        database.authTokenQueries.delete(tokenHash)
    }

    private fun isNewer(existing: Long?, incoming: Long) = existing == null || incoming > existing

    private fun nextSeq(): Long {
        database.syncQueries.increment()
        return database.syncQueries.current().executeAsOne()
    }
}

private fun Boolean.toLong() = if (this) 1L else 0L

private fun Workout_session.toModel() = WorkoutSession(
    id = id,
    workoutId = workout_id,
    programVersion = program_version,
    startedAt = started_at,
    mainStartedAt = main_started_at,
    endedAt = ended_at,
    updatedAt = updated_at,
    deleted = deleted == 1L,
)

private fun Set_log.toModel() = SetLog(
    id = id,
    sessionId = session_id,
    exerciseId = exercise_id,
    setNumber = set_number.toInt(),
    weightGrams = weight_grams.toInt(),
    target = target.toInt(),
    achieved = achieved?.toInt(),
    completedAt = completed_at,
    updatedAt = updated_at,
    deleted = deleted == 1L,
)

private fun Warm_up_log.toModel() = WarmUpLog(
    id = id,
    sessionId = session_id,
    position = position.toInt(),
    status = WarmUpStatus.valueOf(status),
    completedAt = completed_at,
    updatedAt = updated_at,
    deleted = deleted == 1L,
)

private fun Exercise_step.toModel() = ExerciseStep(
    exerciseId = exercise_id,
    step = step.toInt(),
    updatedAt = updated_at,
)