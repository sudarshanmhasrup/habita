package dev.habita.app.feature.gym.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.habita.app.feature.gym.db.GymDatabase
import dev.habita.app.feature.gym.db.Set_log
import dev.habita.app.feature.gym.db.Warm_up_log
import dev.habita.app.feature.gym.db.Workout_session
import dev.habita.app.feature.gym.workout.ActiveWorkout
import dev.habita.app.shared.api.ProgramResponse
import dev.habita.app.shared.api.SyncChanges
import dev.habita.app.shared.gym.ExerciseStep
import dev.habita.app.shared.gym.SetLog
import dev.habita.app.shared.gym.WarmUpLog
import dev.habita.app.shared.gym.WarmUpStatus
import dev.habita.app.shared.gym.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * The gym's local database. Local writes are marked dirty until the server has them. Records from
 * the server replace local ones unless the local copy is newer, the same last-write-wins rule the
 * server uses.
 */
class GymStore(private val database: GymDatabase) {
    private val sessions = database.workoutSessionQueries
    private val sets = database.setLogQueries
    private val warmUps = database.warmUpLogQueries
    private val steps = database.exerciseStepQueries
    private val values = database.gymValueQueries
    private val json = Json { ignoreUnknownKeys = true }

    fun <T> transaction(block: () -> T): T = database.transactionWithResult { block() }

    // Program, cursor and the workout in progress

    val program: Flow<ProgramResponse?> = watchValue(PROGRAM) { json.decodeFromString<ProgramResponse>(it) }

    val active: Flow<ActiveWorkout?> = watchValue(ACTIVE) { json.decodeFromString<ActiveWorkout>(it) }

    fun program(): ProgramResponse? = readValue(PROGRAM) { json.decodeFromString<ProgramResponse>(it) }

    fun saveProgram(program: ProgramResponse) = values.save(PROGRAM, json.encodeToString(program))

    fun active(): ActiveWorkout? = readValue(ACTIVE) { json.decodeFromString<ActiveWorkout>(it) }

    fun saveActive(active: ActiveWorkout?) {
        if (active == null) values.remove(ACTIVE) else values.save(ACTIVE, json.encodeToString(active))
    }

    fun cursor(): Long = readValue(CURSOR) { it.toLong() } ?: 0

    private fun saveCursor(cursor: Long) = values.save(CURSOR, cursor.toString())

    private fun <T> readValue(key: String, decode: (String) -> T): T? =
        values.select(key).executeAsOneOrNull()?.let { runCatching { decode(it) }.getOrNull() }

    private fun <T> watchValue(key: String, decode: (String) -> T): Flow<T?> =
        values.select(key).asFlow().mapToOneOrNull(Dispatchers.Default).map { value ->
            value?.let { runCatching { decode(it) }.getOrNull() }
        }

    // Logs

    val allSessions: Flow<List<WorkoutSession>> =
        sessions.selectAll().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { it.toModel() } }

    val allSteps: Flow<Map<String, Int>> =
        steps.selectAll().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.associate { it.exercise_id to it.step.toInt() } }

    fun sessions(): List<WorkoutSession> = sessions.selectAll().executeAsList().map { it.toModel() }

    fun session(id: String): WorkoutSession? = sessions.selectById(id).executeAsOneOrNull()?.toModel()

    fun steps(): Map<String, Int> = steps.selectAll().executeAsList().associate { it.exercise_id to it.step.toInt() }

    fun setsForExercises(exerciseIds: Collection<String>): List<SetLog> =
        if (exerciseIds.isEmpty()) emptyList() else sets.selectByExercises(exerciseIds).executeAsList().map { it.toModel() }

    fun setsForSession(sessionId: String): List<SetLog> = sets.selectBySession(sessionId).executeAsList().map { it.toModel() }

    fun warmUpsForSession(sessionId: String): List<WarmUpLog> =
        warmUps.selectBySession(sessionId).executeAsList().map { it.toModel() }

    fun saveSession(session: WorkoutSession) = save(session, dirty = true)

    fun saveSet(set: SetLog) = save(set, dirty = true)

    fun saveWarmUp(warmUp: WarmUpLog) = save(warmUp, dirty = true)

    fun saveStep(step: ExerciseStep) = save(step, dirty = true)

    // Sync

    fun dirty(): SyncChanges = transaction {
        SyncChanges(
            sessions = sessions.selectDirty().executeAsList().map { it.toModel() },
            sets = sets.selectDirty().executeAsList().map { it.toModel() },
            warmUps = warmUps.selectDirty().executeAsList().map { it.toModel() },
            steps = steps.selectDirty().executeAsList().map { ExerciseStep(it.exercise_id, it.step.toInt(), it.updated_at) },
        )
    }

    /** Clears the dirty mark of pushed records, unless they changed again since. */
    fun markClean(pushed: SyncChanges) = transaction {
        pushed.sessions.forEach { sessions.markClean(it.id, it.updatedAt) }
        pushed.sets.forEach { sets.markClean(it.id, it.updatedAt) }
        pushed.warmUps.forEach { warmUps.markClean(it.id, it.updatedAt) }
        pushed.steps.forEach { steps.markClean(it.exerciseId, it.updatedAt) }
    }

    /** Applies records pulled from the server and moves the cursor past them. */
    fun merge(pulled: SyncChanges, cursor: Long) = transaction {
        pulled.sessions.forEach { if (wins(it.updatedAt, sessions.updatedAt(it.id).executeAsOneOrNull())) save(it, dirty = false) }
        pulled.sets.forEach { if (wins(it.updatedAt, sets.updatedAt(it.id).executeAsOneOrNull())) save(it, dirty = false) }
        pulled.warmUps.forEach { if (wins(it.updatedAt, warmUps.updatedAt(it.id).executeAsOneOrNull())) save(it, dirty = false) }
        pulled.steps.forEach { if (wins(it.updatedAt, steps.updatedAt(it.exerciseId).executeAsOneOrNull())) save(it, dirty = false) }
        saveCursor(cursor)
    }

    /** A pulled record replaces the local one unless the local one is newer. */
    private fun wins(incoming: Long, local: Long?) = local == null || incoming >= local

    private fun save(session: WorkoutSession, dirty: Boolean) = sessions.save(
        session.id, session.workoutId, session.programVersion, session.startedAt, session.mainStartedAt,
        session.endedAt, session.updatedAt, session.deleted.toLong(), dirty.toLong(),
    )

    private fun save(set: SetLog, dirty: Boolean) = sets.save(
        set.id, set.sessionId, set.exerciseId, set.setNumber.toLong(), set.weightGrams.toLong(), set.target.toLong(),
        set.achieved?.toLong(), set.completedAt, set.updatedAt, set.deleted.toLong(), dirty.toLong(),
    )

    private fun save(warmUp: WarmUpLog, dirty: Boolean) = warmUps.save(
        warmUp.id, warmUp.sessionId, warmUp.position.toLong(), warmUp.status.name, warmUp.completedAt,
        warmUp.updatedAt, warmUp.deleted.toLong(), dirty.toLong(),
    )

    private fun save(step: ExerciseStep, dirty: Boolean) =
        steps.save(step.exerciseId, step.step.toLong(), step.updatedAt, dirty.toLong())

    private companion object {
        const val PROGRAM = "program"
        const val ACTIVE = "active_workout"
        const val CURSOR = "sync_cursor"
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