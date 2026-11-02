package dev.habita.app.feature.gym.data

import dev.habita.app.core.Core
import dev.habita.app.core.data.newId
import dev.habita.app.core.data.nowMillis
import dev.habita.app.feature.gym.db.GymDatabase
import dev.habita.app.feature.gym.workout.ActiveWorkout
import dev.habita.app.feature.gym.workout.WorkoutEngine
import dev.habita.app.shared.api.ProgramResponse
import dev.habita.app.shared.gym.ExerciseStep
import dev.habita.app.shared.gym.ExerciseTarget
import dev.habita.app.shared.gym.History
import dev.habita.app.shared.gym.Program
import dev.habita.app.shared.gym.Progression
import dev.habita.app.shared.gym.Summary
import dev.habita.app.shared.gym.WarmUpStatus
import dev.habita.app.shared.gym.Workout
import dev.habita.app.shared.gym.WorkoutSession
import dev.habita.app.shared.gym.WorkoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Everything the gym screens do. Each action saves its records and the workout in progress in one
 * transaction, then asks for a sync, so nothing is lost when the app closes or the phone is
 * offline. Targets and summaries are computed here from local data, with the same shared code the
 * server uses.
 */
class GymRepository(core: Core) {
    private val store = GymStore(GymDatabase(core.databases.open("gym.db", GymDatabase.Schema)))
    private val mutex = Mutex()

    val sync = GymSync(store, core)

    val program: Flow<ProgramResponse?> = store.program
    val sessions: Flow<List<WorkoutSession>> = store.allSessions
    val steps: Flow<Map<String, Int>> = store.allSteps
    val active: Flow<ActiveWorkout?> = store.active

    /** Today's targets for every exercise in [workoutId], or `null` without a program. */
    suspend fun targets(workoutId: String): List<ExerciseTarget>? = withContext(Dispatchers.Default) {
        val program = store.program()?.program ?: return@withContext null
        val workout = program.workout(workoutId) ?: return@withContext null
        targetsFor(program, workout, nowMillis())
    }

    suspend fun start(workoutId: String): ActiveWorkout = write {
        check(store.active() == null) { "A workout is already in progress" }
        val loaded = checkNotNull(store.program()) { "No program yet" }
        val program = loaded.program
        val workout = checkNotNull(program.workout(workoutId)) { "Unknown workout $workoutId" }
        val warmUp = program.warmUps.first { it.id == workout.warmUpId }
        val now = nowMillis()
        val active = WorkoutEngine.start(
            sessionId = newId(),
            workout = workout,
            warmUp = warmUp,
            exercises = workout.exercises.map { program.exercise(it.exerciseId)!! },
            targets = targetsFor(program, workout, now),
            now = now,
        )
        store.transaction {
            store.saveSession(
                WorkoutSession(
                    id = active.sessionId,
                    workoutId = workout.id,
                    programVersion = loaded.version,
                    startedAt = now,
                    updatedAt = now,
                ),
            )
            store.saveActive(active)
        }
        active
    }

    suspend fun completeWarmUp(status: WarmUpStatus) = update { active ->
        val (next, log) = WorkoutEngine.completeWarmUp(active, status, newId(), nowMillis())
        store.saveWarmUp(log)
        next
    }

    suspend fun warmUpMachineTaken() = update { WorkoutEngine.warmUpMachineTaken(it) }

    /** Ends the break after the warm-up; the main workout's time starts here. */
    suspend fun endBreak() = update { active ->
        val now = nowMillis()
        updateSession(active.sessionId) { it.copy(mainStartedAt = now, updatedAt = now) }
        WorkoutEngine.endBreak(active)
    }

    /**
     * Logs the current set. Returns the session id when that was the workout's last set, which
     * finishes the workout.
     */
    suspend fun logSet(weightGrams: Int, achieved: Int?): String? = write {
        val active = store.active() ?: return@write null
        val now = nowMillis()
        val (next, log) = WorkoutEngine.logSet(active, weightGrams, achieved, newId(), now)
        store.transaction {
            store.saveSet(log)
            if (next.isFinished) finishSession(next, now) else store.saveActive(next)
        }
        next.sessionId.takeIf { next.isFinished }
    }

    suspend fun endRest() = update { WorkoutEngine.endRest(it) }

    suspend fun exerciseMachineTaken() = update { WorkoutEngine.exerciseMachineTaken(it) }

    /**
     * Saves the progression step for [exerciseId] and recomputes the targets of its exercises that
     * have not started yet in the workout in progress.
     */
    suspend fun setStep(exerciseId: String, step: Int) = write {
        val now = nowMillis()
        store.transaction {
            store.saveStep(ExerciseStep(exerciseId, step, now))
            val active = store.active() ?: return@transaction
            val program = store.program()?.program ?: return@transaction
            val slots = active.workout.exercises.indices.filter { active.workout.exercises[it].exerciseId == exerciseId }
            val targets = targetsFor(program, active.workout, now, slots)
            store.saveActive(WorkoutEngine.updateTargets(active, slots.zip(targets).toMap()))
        }
    }

    /** Finishes the workout early. Sets not done are simply not logged. Returns the session id. */
    suspend fun finish(): String? = write {
        val active = store.active() ?: return@write null
        store.transaction { finishSession(active, nowMillis()) }
        active.sessionId
    }

    /** Throws the workout in progress away, with everything logged in it. */
    suspend fun discard() = write {
        val active = store.active() ?: return@write
        val now = nowMillis()
        store.transaction {
            updateSession(active.sessionId) { it.copy(deleted = true, updatedAt = now) }
            store.setsForSession(active.sessionId).forEach { store.saveSet(it.copy(deleted = true, updatedAt = now)) }
            store.warmUpsForSession(active.sessionId).forEach { store.saveWarmUp(it.copy(deleted = true, updatedAt = now)) }
            store.saveActive(null)
        }
    }

    suspend fun summary(sessionId: String): WorkoutSummary? = withContext(Dispatchers.Default) {
        val program = store.program()?.program ?: return@withContext null
        val session = store.session(sessionId) ?: return@withContext null
        val exerciseIds = store.setsForSession(sessionId).map { it.exerciseId } +
            program.workout(session.workoutId)?.exercises?.map { it.exerciseId }.orEmpty()
        Summary.of(
            session = session,
            program = program,
            sessions = store.sessions(),
            sets = store.setsForExercises(exerciseIds.distinct()),
            steps = store.steps(),
            now = nowMillis(),
        )
    }

    private fun finishSession(active: ActiveWorkout, now: Long) {
        updateSession(active.sessionId) { it.copy(mainStartedAt = it.mainStartedAt ?: now, endedAt = now, updatedAt = now) }
        store.saveActive(null)
    }

    private fun updateSession(id: String, change: (WorkoutSession) -> WorkoutSession) {
        store.session(id)?.let { store.saveSession(change(it)) }
    }

    /** Targets for [slots] of [workout], from the local history. */
    private fun targetsFor(
        program: Program,
        workout: Workout,
        now: Long,
        slots: List<Int> = workout.exercises.indices.toList(),
    ): List<ExerciseTarget> {
        val exerciseIds = slots.map { workout.exercises[it].exerciseId }.distinct()
        val sessions = store.sessions()
        val sets = store.setsForExercises(exerciseIds)
        val steps = store.steps()
        return slots.map { index ->
            val slot = workout.exercises[index]
            val exercise = program.exercise(slot.exerciseId)!!
            Progression.target(
                exercise = exercise,
                slot = slot,
                step = steps[exercise.id] ?: exercise.defaultStep,
                history = History.performances(exercise.id, sessions, sets),
                now = now,
            )
        }
    }

    /** Runs [block] alone, off the main thread, then asks for a sync. */
    private suspend fun <T> write(block: () -> T): T = withContext(Dispatchers.Default) {
        mutex.withLock { block() }
    }.also { sync.request() }

    /** Changes the workout in progress, if there is one, and saves it with the records [change] writes. */
    private suspend fun update(change: (ActiveWorkout) -> ActiveWorkout) = write {
        store.transaction {
            val active = store.active() ?: return@transaction
            store.saveActive(change(active))
        }
    }
}