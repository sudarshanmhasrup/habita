package dev.habita.server.sync

import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.SyncRecord
import dev.habita.app.shared.gym.Effort
import dev.habita.app.shared.gym.ExerciseStep
import dev.habita.app.shared.gym.SetLog
import dev.habita.app.shared.gym.WarmUpLog
import dev.habita.app.shared.gym.WarmUpStatus
import dev.habita.app.shared.gym.WorkoutSession
import dev.habita.app.shared.api.SyncJson
import dev.habita.server.db.Exercise_step
import dev.habita.server.db.HabitaDatabase
import dev.habita.server.db.Set_log
import dev.habita.server.db.Warm_up_log
import dev.habita.server.db.Workout_session
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.jsonObject

/**
 * The gym's collections. Unlike other features, the server reads these, to work out targets, the
 * rotation and summaries, so they keep typed tables.
 */
fun gymTables(database: HabitaDatabase): List<SyncTable> = listOf(
    SessionTable(database),
    SetTable(database),
    WarmUpTable(database),
    StepTable(database),
)

/** A table whose records decode to [T]. The envelope's id, time and deleted flag always win. */
private abstract class ModelTable<T>(
    override val collection: String,
    private val serializer: KSerializer<T>,
) : SyncTable {
    /** Why [model] is invalid, or `null`. */
    abstract fun problem(model: T): String?

    abstract fun store(model: T, record: SyncRecord, seq: Long)

    override fun check(record: SyncRecord): String? {
        val model = decode(record) ?: return "Unreadable record"
        return problem(model)
    }

    override fun save(record: SyncRecord, seq: Long) = store(decode(record)!!, record, seq)

    fun encode(model: T, id: String, updatedAt: Long, deleted: Boolean) =
        SyncRecord(id, updatedAt, deleted, SyncJson.encodeToJsonElement(serializer, model).jsonObject)

    private fun decode(record: SyncRecord): T? =
        runCatching { SyncJson.decodeFromJsonElement(serializer, record.data) }.getOrNull()
}

private class SessionTable(database: HabitaDatabase) :
    ModelTable<WorkoutSession>(SyncCollections.GYM_SESSIONS, WorkoutSession.serializer()) {
    private val queries = database.workoutSessionQueries

    override fun problem(model: WorkoutSession) = if (model.workoutId.isBlank()) "Session has no workout" else null

    override fun store(model: WorkoutSession, record: SyncRecord, seq: Long) {
        queries.save(
            record.id, model.workoutId, model.programVersion, model.startedAt, model.mainStartedAt, model.endedAt,
            record.updatedAt, record.deleted.toLong(), seq,
        )
    }

    override fun get(id: String) = queries.selectById(id).executeAsOneOrNull()?.toModel()?.let(::record)

    override fun changedSince(since: Long, limit: Long) =
        queries.changedSince(since, limit).executeAsList().map { Sequenced(it.server_seq, record(it.toModel())) }

    private fun record(model: WorkoutSession) = encode(model, model.id, model.updatedAt, model.deleted)
}

private class SetTable(database: HabitaDatabase) : ModelTable<SetLog>(SyncCollections.GYM_SETS, SetLog.serializer()) {
    private val queries = database.setLogQueries

    override fun problem(model: SetLog) = when {
        model.sessionId.isBlank() || model.exerciseId.isBlank() -> "Set has no session or exercise"
        (model.setNumber <= 0 && !model.warmUp) || model.weightGrams < 0 || model.target <= 0 || (model.achieved ?: 0) < 0 ->
            "Set has an invalid number"
        else -> null
    }

    override fun store(model: SetLog, record: SyncRecord, seq: Long) {
        queries.save(
            record.id, model.sessionId, model.exerciseId, model.setNumber.toLong(), model.weightGrams.toLong(),
            model.target.toLong(), model.achieved?.toLong(), model.completedAt, record.updatedAt,
            record.deleted.toLong(), seq, model.effort?.name, model.warmUp.toLong(),
        )
    }

    override fun get(id: String) = queries.selectById(id).executeAsOneOrNull()?.toModel()?.let(::record)

    override fun changedSince(since: Long, limit: Long) =
        queries.changedSince(since, limit).executeAsList().map { Sequenced(it.server_seq, record(it.toModel())) }

    private fun record(model: SetLog) = encode(model, model.id, model.updatedAt, model.deleted)
}

private class WarmUpTable(database: HabitaDatabase) :
    ModelTable<WarmUpLog>(SyncCollections.GYM_WARM_UPS, WarmUpLog.serializer()) {
    private val queries = database.warmUpLogQueries

    override fun problem(model: WarmUpLog) =
        if (model.sessionId.isBlank() || model.position < 0) "Warm-up has no session or position" else null

    override fun store(model: WarmUpLog, record: SyncRecord, seq: Long) {
        queries.save(
            record.id, model.sessionId, model.position.toLong(), model.status.name, model.completedAt,
            record.updatedAt, record.deleted.toLong(), seq,
        )
    }

    override fun get(id: String) = queries.selectById(id).executeAsOneOrNull()?.toModel()?.let(::record)

    override fun changedSince(since: Long, limit: Long) =
        queries.changedSince(since, limit).executeAsList().map { Sequenced(it.server_seq, record(it.toModel())) }

    private fun record(model: WarmUpLog) = encode(model, model.id, model.updatedAt, model.deleted)
}

/** One record per exercise, keyed by the exercise id. Steps are never deleted. */
private class StepTable(database: HabitaDatabase) :
    ModelTable<ExerciseStep>(SyncCollections.GYM_STEPS, ExerciseStep.serializer()) {
    private val queries = database.exerciseStepQueries

    override fun problem(model: ExerciseStep) = if (model.step <= 0) "Step must be positive" else null

    override fun store(model: ExerciseStep, record: SyncRecord, seq: Long) {
        queries.save(record.id, model.step.toLong(), record.updatedAt, seq)
    }

    override fun get(id: String) = queries.selectById(id).executeAsOneOrNull()?.toModel()?.let(::record)

    override fun changedSince(since: Long, limit: Long) =
        queries.changedSince(since, limit).executeAsList().map { Sequenced(it.server_seq, record(it.toModel())) }

    private fun record(model: ExerciseStep) = encode(model, model.exerciseId, model.updatedAt, false)
}

private fun Boolean.toLong() = if (this) 1L else 0L

internal fun Workout_session.toModel() = WorkoutSession(
    id = id,
    workoutId = workout_id,
    programVersion = program_version,
    startedAt = started_at,
    mainStartedAt = main_started_at,
    endedAt = ended_at,
    updatedAt = updated_at,
    deleted = deleted == 1L,
)

internal fun Set_log.toModel() = SetLog(
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

internal fun Warm_up_log.toModel() = WarmUpLog(
    id = id,
    sessionId = session_id,
    position = position.toInt(),
    status = WarmUpStatus.entries.firstOrNull { it.name == status } ?: WarmUpStatus.entries.first(),
    completedAt = completed_at,
    updatedAt = updated_at,
    deleted = deleted == 1L,
)

internal fun Exercise_step.toModel() = ExerciseStep(
    exerciseId = exercise_id,
    step = step.toInt(),
    updatedAt = updated_at,
)