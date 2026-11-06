package dev.habita.app.feature.gym.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.habita.app.feature.gym.db.Body_log
import dev.habita.app.feature.gym.db.Exercise_step
import dev.habita.app.feature.gym.db.GymDatabase
import dev.habita.app.feature.gym.db.Set_log
import dev.habita.app.feature.gym.db.Warm_up_log
import dev.habita.app.feature.gym.db.Workout_session
import dev.habita.app.feature.gym.workout.ActiveWorkout
import dev.habita.app.shared.api.ProgramResponse
import dev.habita.app.shared.api.SyncChanges
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.gym.BodyLog
import dev.habita.app.shared.gym.Effort
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
 * The gym's local database. Local writes are marked dirty until the server has them. Which server
 * copies to keep is decided by the app's sync engine ([dev.habita.app.core.sync.SyncEngine]).
 */
class GymStore(private val database: GymDatabase) {
    private val sessions = database.workoutSessionQueries
    private val sets = database.setLogQueries
    private val warmUps = database.warmUpLogQueries
    private val steps = database.exerciseStepQueries
    private val values = database.gymValueQueries
    internal val body = database.bodyLogQueries
    private val json = Json { ignoreUnknownKeys = true }

    fun <T> transaction(block: () -> T): T = database.transactionWithResult { block() }

    // Program and the workout in progress

    val program: Flow<ProgramResponse?> = watchValue(PROGRAM) { json.decodeFromString<ProgramResponse>(it) }

    val active: Flow<ActiveWorkout?> = watchValue(ACTIVE) { json.decodeFromString<ActiveWorkout>(it) }

    fun program(): ProgramResponse? = readValue(PROGRAM) { json.decodeFromString<ProgramResponse>(it) }

    fun saveProgram(program: ProgramResponse) = values.save(PROGRAM, json.encodeToString(program))

    fun active(): ActiveWorkout? = readValue(ACTIVE) { json.decodeFromString<ActiveWorkout>(it) }

    fun saveActive(active: ActiveWorkout?) {
        if (active == null) values.remove(ACTIVE) else values.save(ACTIVE, json.encodeToString(active))
    }

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

    // Body

    val allBody: Flow<List<BodyLog>> =
        body.selectAll().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { it.toModel() } }

    fun bodyLog(day: String): BodyLog? = body.selectByDay(day).executeAsOneOrNull()?.toModel()

    fun saveBody(log: BodyLog) = saveBody(log, dirty = true)

    internal fun saveBody(log: BodyLog, dirty: Boolean) = body.save(
        log.day, log.weightGrams?.toLong(), log.waistMm?.toLong(), log.updatedAt, log.deleted.toLong(), dirty.toLong(),
    )

    /** Every record, including deleted ones and ones already synced, for a backup. */
    fun everything(): SyncChanges = transaction {
        SyncChanges(
            sessions = sessions.selectAll().executeAsList().map { it.toModel() },
            sets = sets.selectAll().executeAsList().map { it.toModel() },
            warmUps = warmUps.selectAll().executeAsList().map { it.toModel() },
            steps = steps.selectAll().executeAsList().map { it.toModel() },
        )
    }

    // Sync

    /** Unsent records of one collection. */
    fun dirty(collection: String): SyncChanges = when (collection) {
        SyncCollections.GYM_SESSIONS -> SyncChanges(sessions = sessions.selectDirty().executeAsList().map { it.toModel() })
        SyncCollections.GYM_SETS -> SyncChanges(sets = sets.selectDirty().executeAsList().map { it.toModel() })
        SyncCollections.GYM_WARM_UPS -> SyncChanges(warmUps = warmUps.selectDirty().executeAsList().map { it.toModel() })
        SyncCollections.GYM_STEPS -> SyncChanges(steps = steps.selectDirty().executeAsList().map { it.toModel() })
        else -> SyncChanges()
    }

    /** One record of [collection] and whether it is unsent, or `null` when there is none. */
    fun local(collection: String, id: String): Pair<SyncChanges, Boolean>? = when (collection) {
        SyncCollections.GYM_SESSIONS -> sessions.selectById(id).executeAsOneOrNull()?.let { SyncChanges(sessions = listOf(it.toModel())) to (it.dirty == 1L) }
        SyncCollections.GYM_SETS -> sets.selectById(id).executeAsOneOrNull()?.let { SyncChanges(sets = listOf(it.toModel())) to (it.dirty == 1L) }
        SyncCollections.GYM_WARM_UPS -> warmUps.selectById(id).executeAsOneOrNull()?.let { SyncChanges(warmUps = listOf(it.toModel())) to (it.dirty == 1L) }
        SyncCollections.GYM_STEPS -> steps.selectById(id).executeAsOneOrNull()?.let { SyncChanges(steps = listOf(it.toModel())) to (it.dirty == 1L) }
        else -> null
    }

    /** Clears the dirty mark of pushed records, unless they changed again since. */
    fun markClean(pushed: SyncChanges) = transaction {
        pushed.sessions.forEach { sessions.markClean(it.id, it.updatedAt) }
        pushed.sets.forEach { sets.markClean(it.id, it.updatedAt) }
        pushed.warmUps.forEach { warmUps.markClean(it.id, it.updatedAt) }
        pushed.steps.forEach { steps.markClean(it.exerciseId, it.updatedAt) }
    }

    /** Saves records from the server as they are, already synced. */
    fun applyRemote(pulled: SyncChanges) = transaction {
        pulled.sessions.forEach { save(it, dirty = false) }
        pulled.sets.forEach { save(it, dirty = false) }
        pulled.warmUps.forEach { save(it, dirty = false) }
        pulled.steps.forEach { save(it, dirty = false) }
    }

    fun markAllDirty(collection: String) {
        when (collection) {
            SyncCollections.GYM_SESSIONS -> sessions.markAllDirty()
            SyncCollections.GYM_SETS -> sets.markAllDirty()
            SyncCollections.GYM_WARM_UPS -> warmUps.markAllDirty()
            SyncCollections.GYM_STEPS -> steps.markAllDirty()
        }
    }

    private fun save(session: WorkoutSession, dirty: Boolean) = sessions.save(
        session.id, session.workoutId, session.programVersion, session.startedAt, session.mainStartedAt,
        session.endedAt, session.updatedAt, session.deleted.toLong(), dirty.toLong(),
    )

    private fun save(set: SetLog, dirty: Boolean) = sets.save(
        set.id, set.sessionId, set.exerciseId, set.setNumber.toLong(), set.weightGrams.toLong(), set.target.toLong(),
        set.achieved?.toLong(), set.completedAt, set.updatedAt, set.deleted.toLong(), dirty.toLong(), set.effort?.name, set.warmUp.toLong(),
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
    }
}

internal fun Boolean.toLong() = if (this) 1L else 0L

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
    effort = effort?.let { name -> Effort.entries.firstOrNull { it.name == name } },
    warmUp = warm_up == 1L,
)

private fun Warm_up_log.toModel() = WarmUpLog(
    id = id,
    sessionId = session_id,
    position = position.toInt(),
    status = WarmUpStatus.entries.firstOrNull { it.name == status } ?: WarmUpStatus.entries.first(),
    completedAt = completed_at,
    updatedAt = updated_at,
    deleted = deleted == 1L,
)

internal fun Body_log.toModel() = BodyLog(
    day = day,
    weightGrams = weight_grams?.toInt(),
    waistMm = waist_mm?.toInt(),
    updatedAt = updated_at,
    deleted = deleted == 1L,
)

private fun Exercise_step.toModel() = ExerciseStep(exercise_id, step.toInt(), updated_at)