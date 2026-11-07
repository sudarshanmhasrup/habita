package dev.habita.app.feature.gym.data

import dev.habita.app.core.Core
import dev.habita.app.core.data.newId
import dev.habita.app.core.data.nowMillis
import dev.habita.app.feature.gym.db.GymDatabase
import dev.habita.app.feature.gym.workout.ActiveWorkout
import dev.habita.app.feature.gym.workout.WorkoutEngine
import dev.habita.app.shared.api.Export
import dev.habita.app.shared.api.ExportedProgram
import dev.habita.app.shared.api.ProgramResponse
import dev.habita.app.shared.gym.Body
import dev.habita.app.shared.gym.BodyGoal
import dev.habita.app.shared.gym.BodyLog
import dev.habita.app.shared.gym.GymSetting
import dev.habita.app.shared.gym.CheckIn
import dev.habita.app.shared.gym.CheckInData
import dev.habita.app.shared.gym.CheckInReason
import dev.habita.app.shared.gym.CheckIns
import dev.habita.app.shared.gym.Effort
import dev.habita.app.shared.gym.Exercise
import dev.habita.app.shared.gym.ExerciseSwap
import dev.habita.app.shared.gym.Swaps
import dev.habita.app.shared.gym.ExerciseStep
import dev.habita.app.shared.gym.ExerciseTarget
import dev.habita.app.shared.gym.History
import dev.habita.app.shared.gym.Program
import dev.habita.app.shared.gym.SetLog
import dev.habita.app.shared.gym.ProgramParser
import dev.habita.app.shared.gym.Progression
import dev.habita.app.shared.gym.Summary
import dev.habita.app.shared.gym.WarmUpStatus
import dev.habita.app.shared.gym.Workout
import dev.habita.app.shared.gym.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

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

    val syncEnabled: Boolean = core.config.loginEnabled

    init {
        loadBundledProgram()
        recoverUnreadableWorkout()
    }

    /**
     * A workout in progress this version can't read back, for example after an update changed how
     * it is saved, can't be resumed. Its session is finished at its last done set, so it counts, and
     * the gym is free to start a new one. Only this phone's own workout is touched.
     */
    private fun recoverUnreadableWorkout() {
        store.recoverUnreadableActive(nowMillis())
    }

    /**
     * Uses the plan built into the app until the server sends one, so the gym works offline from
     * the first launch. A newer bundled plan replaces an older bundled one, never the server's.
     */
    private fun loadBundledProgram() {
        val cached = store.program()
        if (cached != null && cached.version != BUNDLED_VERSION) return
        val bundled = ProgramResponse(BUNDLED_VERSION, ProgramParser.parse(BUNDLED_PROGRAM))
        if (cached != bundled) store.saveProgram(bundled)
    }

    val program: Flow<ProgramResponse?> = store.program
    val sessions: Flow<List<WorkoutSession>> = store.allSessions
    val steps: Flow<Map<String, Int>> = store.allSteps
    val active: Flow<ActiveWorkout?> = store.active
    val body: Flow<List<BodyLog>> = store.allBody

    /** Every set, for the progress screen. */
    val sets: Flow<List<SetLog>> = store.allSets

    /** Exercises swapped at this gym, by the planned exercise they replace. */
    val swaps: Flow<Map<String, String>> = store.allSwaps.map(Swaps::active)

    /** What the diet is for; losing fat until it is changed. */
    val goal: Flow<BodyGoal> = store.allSettings.map { settings ->
        BodyGoal.entries.firstOrNull { it.name == settings[GymSetting.GOAL_KEY] } ?: BodyGoal.LOSE_FAT
    }

    suspend fun setGoal(goal: BodyGoal) = write {
        store.saveSetting(GymSetting(GymSetting.GOAL_KEY, goal.name, nowMillis()))
    }

    /**
     * Whether the plan needs a check-in, and which exercises are stuck. It reads every set, so a
     * burst of changes, such as a sync, is worked out once after it settles.
     */
    @OptIn(FlowPreview::class)
    val checkIn: Flow<CheckInStatus> =
        combine(store.program, store.allSessions, store.allSets, store.allCheckIns) { program, sessions, sets, checkIns ->
            Quad(program, sessions, sets, checkIns)
        }.debounce(CHECK_IN_SETTLE_MILLIS).map { (program, sessions, sets, checkIns) ->
            val plan = program?.program ?: return@map CheckInStatus(null, emptyList(), null)
            val now = nowMillis()
            val last = checkIns.filter { !it.deleted }.maxOfOrNull { it.at }
            val plateaus = CheckIns.plateaus(plan, sessions, sets, now)
            CheckInStatus(CheckIns.due(last, sessions, plateaus, now), plateaus.mapNotNull { plan.exercise(it.exerciseId)?.name }, last)
        }.flowOn(Dispatchers.Default)

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    /** The check-in text to paste into an AI chat. Copying it counts as a check-in. */
    suspend fun checkInText(today: String, formatDate: (Long) -> String): String? = write {
        val program = store.program()?.program ?: return@write null
        val now = nowMillis()
        val text = CheckIns.text(
            CheckInData(
                program = program,
                sessions = store.sessions(),
                sets = store.allSets(),
                body = store.everythingBody(),
                swaps = Swaps.active(store.swaps()),
                lastCheckIn = store.checkIns().filter { !it.deleted }.maxOfOrNull { it.at },
                now = now,
                today = today,
                formatDate = formatDate,
                goal = BodyGoal.entries.firstOrNull { goal -> store.settings().any { it.key == GymSetting.GOAL_KEY && it.value == goal.name } }
                    ?: BodyGoal.LOSE_FAT,
            ),
        )
        store.saveCheckIn(CheckIn(newId(), now, now))
        text
    }

    /** Saves today's weight, keeping a waist already logged today. */
    suspend fun logWeight(day: String, grams: Int) = write {
        store.transaction {
            val today = store.bodyLog(day)?.takeIf { !it.deleted }
            store.saveBody(Body.update(today, day, grams, today?.waistMm, nowMillis()))
        }
    }

    /**
     * Sets both values of any [day], for fixing or filling in a past day. With both `null` the day
     * is deleted.
     */
    suspend fun setBody(day: String, weightGrams: Int?, waistMm: Int?) = write {
        val now = nowMillis()
        store.transaction {
            val existing = store.bodyLog(day)
            when {
                weightGrams != null || waistMm != null -> store.saveBody(Body.update(existing, day, weightGrams, waistMm, now))
                existing != null && !existing.deleted -> store.saveBody(existing.copy(deleted = true, updatedAt = now))
            }
        }
    }

    /** Saves today's waist, keeping a weight already logged today. */
    suspend fun logWaist(day: String, mm: Int) = write {
        store.transaction {
            val today = store.bodyLog(day)?.takeIf { !it.deleted }
            store.saveBody(Body.update(today, day, today?.weightGrams, mm, nowMillis()))
        }
    }

    /** Today's targets for every exercise in [workoutId], or `null` without a program. */
    suspend fun targets(workoutId: String): List<ExerciseTarget>? = withContext(Dispatchers.Default) {
        val program = store.program()?.program ?: return@withContext null
        val workout = program.workout(workoutId) ?: return@withContext null
        targetsFor(program, Swaps.apply(workout, program, Swaps.active(store.swaps())), nowMillis())
    }

    suspend fun start(workoutId: String): ActiveWorkout = write {
        check(store.active() == null) { "A workout is already in progress" }
        val loaded = checkNotNull(store.program()) { "No program yet" }
        val program = loaded.program
        val planned = checkNotNull(program.workout(workoutId)) { "Unknown workout $workoutId" }
        val workout = Swaps.apply(planned, program, Swaps.active(store.swaps()))
        val warmUp = program.warmUps.first { it.id == workout.warmUpId }
        val now = nowMillis()
        val active = WorkoutEngine.start(
            sessionId = newId(),
            workout = workout,
            warmUp = warmUp,
            exercises = workout.exercises.map { program.exercise(it.exerciseId)!! },
            targets = targetsFor(program, workout, now),
            now = now,
            planned = planned,
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
    suspend fun logSet(weightGrams: Int, achieved: Int?, effort: Effort?): String? = write {
        val active = store.active() ?: return@write null
        val now = nowMillis()
        val exercise = active.currentSlot?.let { active.exercises[it] } ?: return@write null
        val step = store.steps()[exercise.id] ?: exercise.defaultStep
        val (next, log) = WorkoutEngine.logSet(active, weightGrams, achieved, effort, step, newId(), now)
        store.transaction {
            store.saveSet(log)
            if (next.isFinished) finishSession(next, now) else store.saveActive(next)
        }
        // The set can be taken back until anything else changes the workout.
        _undoable.value = Undo(active, next, log.id)
        next.sessionId.takeIf { next.isFinished }
    }

    /** The set [setId] took the workout from [before] to [after]. */
    private class Undo(val before: ActiveWorkout, val after: ActiveWorkout, val setId: String)

    private val _undoable = MutableStateFlow<Undo?>(null)

    /**
     * Whether [undo] still applies: the workout is as the set left it, apart from its rest timer,
     * or, when that set finished it, nothing has started since.
     */
    private fun stillUndoable(undo: Undo, active: ActiveWorkout?): Boolean =
        if (undo.after.isFinished) {
            active == null && store.session(undo.after.sessionId)?.let { !it.deleted && it.endedAt != null } == true
        } else {
            active != null && active.copy(rest = null) == undo.after.copy(rest = null)
        }

    /** The id of the set that "Undo last set" would take back, or `null`. */
    val undoableSet: Flow<String?> = combine(_undoable, store.active) { undo, active ->
        undo?.takeIf { withContext(Dispatchers.Default) { stillUndoable(it, active) } }?.setId
    }

    /**
     * Takes back the set just logged, by mistake or with the wrong numbers: it is deleted and the
     * workout goes back to just before it, so the set can be done again. A set that finished the
     * workout reopens it. Returns whether a set was taken back.
     */
    suspend fun undoLastSet(): Boolean = write {
        val undo = _undoable.value ?: return@write false
        if (!stillUndoable(undo, store.active())) return@write false
        _undoable.value = null
        val now = nowMillis()
        store.transaction {
            store.setsForSession(undo.before.sessionId).firstOrNull { it.id == undo.setId }
                ?.let { store.saveSet(it.copy(deleted = true, updatedAt = now)) }
            if (undo.after.isFinished) updateSession(undo.before.sessionId) { it.copy(endedAt = null, updatedAt = now) }
            store.saveActive(undo.before)
        }
        true
    }

    /** Logs the current exercise's warm-up set, or skips it when [reps] is `null`. */
    suspend fun logWarmUpSet(weightGrams: Int, reps: Int?) = write {
        val active = store.active() ?: return@write
        val (next, log) = WorkoutEngine.logWarmUpSet(active, weightGrams, reps, newId(), nowMillis())
        store.transaction {
            log?.let(store::saveSet)
            store.saveActive(next)
        }
    }

    suspend fun endRest() = update { WorkoutEngine.endRest(it) }

    suspend fun extendRest(seconds: Int) = update { WorkoutEngine.extendRest(it, seconds) }

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
            val targets = targetsFor(program, active.workout, now, slots) { active.exercises[it] }
            store.saveActive(WorkoutEngine.updateTargets(active, slots.zip(targets).toMap()))
        }
    }

    /**
     * At this gym, does [exerciseId] (as the plan has it) as [replacementId] from now on, or as
     * planned again when [replacementId] is `null`. The workout in progress follows, for slots with
     * no working sets yet.
     */
    suspend fun swap(exerciseId: String, replacementId: String?) = write {
        val now = nowMillis()
        store.transaction {
            val existing = store.swaps().firstOrNull { it.exerciseId == exerciseId }
            when {
                replacementId != null -> store.saveSwap(ExerciseSwap(exerciseId, replacementId, now))
                existing != null && !existing.deleted -> store.saveSwap(existing.copy(deleted = true, updatedAt = now))
            }
            followSwaps(now)
        }
    }

    /** A new gym: every swap is undone, so each exercise is back to the plan's. */
    suspend fun newGym() = write {
        val now = nowMillis()
        store.transaction {
            store.swaps().filter { !it.deleted }.forEach { store.saveSwap(it.copy(deleted = true, updatedAt = now)) }
            followSwaps(now)
        }
    }

    /** Brings the workout in progress in line with the swaps, for slots with no working sets yet. */
    private fun followSwaps(now: Long) {
        var active = store.active() ?: return
        val program = store.program()?.program ?: return
        val wanted = Swaps.apply(active.planned, program, Swaps.active(store.swaps()))
        val slots = active.workout.exercises.indices.filter {
            active.canSwap(it) && wanted.exercises[it].exerciseId != active.slot(it).exerciseId
        }
        if (slots.isEmpty()) return
        val targets = targetsFor(program, wanted, now, slots)
        slots.zip(targets).forEach { (slot, target) ->
            val exercise = program.exercise(wanted.exercises[slot].exerciseId) ?: return@forEach
            active = WorkoutEngine.swapExercise(active, slot, exercise, target)
        }
        store.saveActive(active)
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

    /**
     * Every workout on this phone as JSON, in the same format as the server's export, so it can be
     * kept as a backup or read by anything that reads the server's.
     */
    suspend fun backup(): String = withContext(Dispatchers.Default) {
        val now = nowMillis()
        val program = store.program()
        val export = Export(
            exportedAt = now,
            programs = listOf(
                ExportedProgram(
                    version = program?.version ?: BUNDLED_VERSION,
                    content = if (program == null || program.version == BUNDLED_VERSION) BUNDLED_PROGRAM else "",
                    createdAt = now,
                ),
            ),
            changes = store.everything(),
            records = sync.records.associate { it.collection to it.everything() }.filterValues { it.isNotEmpty() },
        )
        backupJson.encodeToString(export)
    }

    /**
     * Merges a backup file into the phone: records newer than the phone's copies are kept. Returns
     * `null` when the file isn't a Habita backup.
     */
    suspend fun restore(text: String): RestoreResult? = write {
        val collections = GymRestore.read(text) ?: return@write null
        GymRestore.merge(sync.tables, collections) { block -> store.transaction(block) }
    }

    /** The summary of [sessionId] with the data for its charts. */
    suspend fun report(sessionId: String): SessionReport? = withContext(Dispatchers.Default) {
        val program = store.program()?.program ?: return@withContext null
        val session = store.session(sessionId) ?: return@withContext null
        val sessions = store.sessions()
        val exerciseIds = store.setsForSession(sessionId).map { it.exerciseId } +
            program.workout(session.workoutId)?.exercises?.map { it.exerciseId }.orEmpty()
        val sameWorkout = sessions.filter { it.workoutId == session.workoutId && !it.deleted }.map { it.id }.toSet()
        val sets = (store.setsForExercises(exerciseIds.distinct()) + sameWorkout.flatMap { store.setsForSession(it) })
            .distinctBy { it.id }
        val summary = Summary.of(
            session = session,
            program = program,
            sessions = sessions,
            sets = sets,
            steps = store.steps(),
            now = nowMillis(),
            swaps = Swaps.active(store.swaps()),
        )
        Reports.of(summary, session, program, sessions, sets)
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
        // For a workout in progress: its own copy, when a new plan no longer has the exercise.
        known: (slot: Int) -> Exercise? = { null },
    ): List<ExerciseTarget> {
        val exerciseIds = slots.map { workout.exercises[it].exerciseId }.distinct()
        val sessions = store.sessions()
        val sets = store.setsForExercises(exerciseIds)
        val steps = store.steps()
        return slots.map { index ->
            val slot = workout.exercises[index]
            val exercise = program.exercise(slot.exerciseId) ?: known(index)
                ?: error("Exercise ${slot.exerciseId} is not in the plan")
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

/** How long the data must stay still before the check-in is worked out again. */
private const val CHECK_IN_SETTLE_MILLIS = 1_000L

/** The version of the plan built into the app. Server versions start at 1. */
internal const val BUNDLED_VERSION = 0L

private val backupJson = Json { prettyPrint = true }

/**
 * Whether a check-in is due and why, the names of stuck exercises, and when the last one was, or
 * `null` before the first.
 */
data class CheckInStatus(val due: CheckInReason?, val stuck: List<String>, val lastAt: Long?)