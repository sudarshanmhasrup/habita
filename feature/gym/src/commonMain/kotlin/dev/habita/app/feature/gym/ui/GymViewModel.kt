package dev.habita.app.feature.gym.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.habita.app.feature.gym.data.CheckInStatus
import dev.habita.app.feature.gym.data.GymRepository
import dev.habita.app.feature.gym.data.RestoreResult
import dev.habita.app.feature.gym.data.SessionReport
import dev.habita.app.core.sync.SyncState
import dev.habita.app.feature.gym.workout.ActiveWorkout
import dev.habita.app.feature.gym.workout.Phase
import dev.habita.app.shared.gym.Body
import dev.habita.app.shared.gym.BodyGoal
import dev.habita.app.shared.gym.BodyLog
import dev.habita.app.shared.gym.BodyTrend
import dev.habita.app.shared.gym.Effort
import dev.habita.app.shared.gym.ExerciseTarget
import dev.habita.app.shared.gym.Program
import dev.habita.app.shared.gym.Rotation
import dev.habita.app.shared.gym.Progress as ProgressReport
import dev.habita.app.shared.gym.Swaps
import dev.habita.app.shared.gym.WarmUpStatus
import dev.habita.app.shared.gym.Workout
import dev.habita.app.shared.gym.WorkoutSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import dev.habita.app.core.data.nowMillis

internal sealed interface GymRoute {
    /** How far in the flow the screen is, so transitions know which way to slide. */
    val depth: Int
        get() = when (this) {
            Select -> 0
            Progress -> 1
            is PreStart -> 1
            Workout -> 2
            is Summary -> 3
        }

    data object Select : GymRoute

    data object Progress : GymRoute

    data class PreStart(val workoutId: String) : GymRoute

    data object Workout : GymRoute

    data class Summary(val sessionId: String) : GymRoute
}

internal data class GymData(
    val program: Program?,
    val sessions: List<WorkoutSession>,
    val steps: Map<String, Int>,
    val active: ActiveWorkout?,
    val sync: SyncState,
    /** Whether the app logs in and syncs; without it everything stays on the phone. */
    val syncEnabled: Boolean,
    val body: BodyTrend = Body.trend(emptyList(), today()),
    /** Exercises swapped at this gym, by the planned exercise they replace. */
    val swaps: Map<String, String> = emptyMap(),
    /** Today's date, so screens redraw when the day changes. */
    val day: String = today(),
    /** Every body log, for editing past days. */
    val bodyLogs: List<BodyLog> = emptyList(),
    /** Changes waiting for a server that accepts them. */
    val refused: Int = 0,
) {
    /** [id] as it will be done here: the plan's workout with this gym's swaps. */
    fun workout(id: String): Workout? =
        program?.let { plan -> plan.workout(id)?.let { Swaps.apply(it, plan, swaps) } }

    /** The next workout in the rotation. */
    val suggested: Workout? = program?.let { Rotation.next(it, sessions) }

    fun step(exerciseId: String): Int? =
        steps[exerciseId] ?: program?.exercise(exerciseId)?.defaultStep
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class GymViewModel(private val repository: GymRepository) : ViewModel() {
    private val _route = MutableStateFlow<GymRoute>(GymRoute.Select)
    val route: StateFlow<GymRoute> = _route.asStateFlow()

    val data: StateFlow<GymData?> = combine(
        combine(repository.program, repository.sessions, repository.steps, repository.active, repository.sync.state) {
            program, sessions, steps, active, sync ->
            GymData(program?.program, sessions, steps, active, sync, repository.syncEnabled)
        },
        repository.body,
        repository.swaps,
        currentDay,
        repository.goal,
    ) { data, body, swaps, day, goal ->
        data.copy(body = Body.trend(body, day, goal), swaps = swaps, day = day, bodyLogs = body)
    }.combine(repository.sync.refused) { data, refused -> data.copy(refused = refused) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Targets for the pre-start screen, refreshed when synced history or a swap arrives. */
    val preStartTargets: StateFlow<List<ExerciseTarget>?> = _route
        .flatMapLatest { route ->
            if (route !is GymRoute.PreStart) {
                flowOf(null)
            } else {
                combine(repository.program, repository.sessions, repository.steps, repository.swaps) { _, _, _, _ -> }
                    .mapLatest { repository.targets(route.workoutId) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val report: StateFlow<SessionReport?> = _route
        .flatMapLatest { route ->
            if (route is GymRoute.Summary) flow<SessionReport?> { emit(repository.report(route.sessionId)) } else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val checkIn: StateFlow<CheckInStatus?> = repository.checkIn.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The check-in text for an AI chat; getting it counts as a check-in. */
    suspend fun checkInText(): String? = repository.checkInText(today(), ::backupDate)

    /** The progress screen's data, worked out only while it is open. */
    val progress: StateFlow<ProgressData?> = _route
        .flatMapLatest { route ->
            if (route != GymRoute.Progress) {
                flowOf(null)
            } else {
                combine(repository.program, repository.sessions, repository.sets, repository.body, currentDay) { program, sessions, sets, body, day ->
                    val plan = program?.program ?: return@combine null
                    ProgressData(ProgressReport.exercises(plan, sessions, sets), ProgressReport.bodyWeeks(body, day))
                }.flowOn(Dispatchers.Default)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun openProgress() {
        _route.value = GymRoute.Progress
    }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var busy = false

    /**
     * Called each time the gym screen appears, including after the activity is recreated: resumes
     * a workout in progress and syncs, but otherwise stays on the current screen.
     */
    fun open() {
        _error.value = null
        viewModelScope.launch {
            val active = repository.active.first()
            val hasActive = active != null
            val route = _route.value
            // Coming back to the gym goes to the workout in progress, wherever it was left; a
            // summary from earlier gives way to the list.
            _route.value = when {
                // Looking at another workout's plan is left alone, for example across a rotation.
                hasActive && (route is GymRoute.Select || (route is GymRoute.PreStart && route.workoutId == active?.workout?.id)) ->
                    GymRoute.Workout
                !hasActive && route == GymRoute.Workout -> GymRoute.Select
                route is GymRoute.Summary && isOld(route.sessionId) -> GymRoute.Select
                else -> route
            }
        }
        repository.sync.request()
    }

    /** Whether [sessionId] ended over half an hour ago. */
    private suspend fun isOld(sessionId: String): Boolean {
        val ended = repository.sessions.first().firstOrNull { it.id == sessionId }?.endedAt ?: return true
        return nowMillis() - ended > 30 * 60 * 1000
    }

    /** Goes back one screen. Returns `false` on the first gym screen, where back leaves the gym. */
    fun back(): Boolean {
        if (_route.value == GymRoute.Select) return false
        _route.value = GymRoute.Select
        return true
    }

    fun retrySync() = repository.sync.request()

    /** The backup file's contents. */
    suspend fun backup(): String = repository.backup()

    /** Merges a backup file in; `null` when it isn't one. */
    suspend fun restore(text: String): RestoreResult? = repository.restore(text)

    fun openWorkout(workoutId: String) {
        _route.value = GymRoute.PreStart(workoutId)
    }

    fun start(workoutId: String) = act {
        repository.start(workoutId)
        // Waits for the new workout to reach [data] first; otherwise the gym screen sees the workout
        // route with no workout and sends us back to the list.
        withTimeoutOrNull(2_000) { data.first { it?.active != null } }
        _route.value = GymRoute.Workout
    }

    fun resume() {
        _route.value = GymRoute.Workout
    }

    fun discard() = settled { repository.discard() }

    fun warmUpDone() = settled { repository.completeWarmUp(WarmUpStatus.DONE) }

    fun warmUpSkip() = settled { repository.completeWarmUp(WarmUpStatus.SKIPPED) }

    fun warmUpMachineTaken() = settled { repository.warmUpMachineTaken() }

    fun endBreak() = settled {
        if (data.value?.active?.phase == Phase.BREAK) repository.endBreak()
    }

    fun endRest() = settled {
        if (data.value?.active?.rest != null) repository.endRest()
    }

    fun logWarmUpSet(weightGrams: Int, reps: Int?) = settled { repository.logWarmUpSet(weightGrams, reps) }

    // The +30 s button stays put, so quick repeated taps are all meant: no settling.
    fun extendRest(seconds: Int) = act { repository.extendRest(seconds) }

    /** Logs the current set; [achieved] is `null` for a skipped set. */
    fun logSet(weightGrams: Int, achieved: Int?, effort: Effort?) = settled {
        repository.logSet(weightGrams, achieved, effort)?.let { _route.value = GymRoute.Summary(it) }
    }

    /** Saves this morning's weight. */
    fun logWeight(grams: Int) = act { repository.logWeight(today(), grams) }

    fun setGoal(goal: BodyGoal) = act { repository.setGoal(goal) }

    /** Fixes or fills in [day]; both `null` deletes it. */
    fun setBody(day: String, weightGrams: Int?, waistMm: Int?) = act { repository.setBody(day, weightGrams, waistMm) }

    /** Saves today's waist measurement. */
    fun logWaist(mm: Int) = act { repository.logWaist(today(), mm) }

    /** Does [exerciseId] as [replacementId] at this gym, or as planned again for `null`. */
    fun swap(exerciseId: String, replacementId: String?) = act { repository.swap(exerciseId, replacementId) }

    fun newGym() = act { repository.newGym() }

    fun exerciseMachineTaken() = settled { repository.exerciseMachineTaken() }

    fun setStep(exerciseId: String, step: Int) = act { repository.setStep(exerciseId, step) }

    fun finish() = settled {
        repository.finish()?.let { _route.value = GymRoute.Summary(it) }
    }

    fun dismissError() {
        _error.value = null
    }

    /** Runs one action at a time, so a double tap never logs a set twice. */
    private fun act(block: suspend () -> Unit) = run(settle = false, block)

    /**
     * A workout step. The next step's buttons appear where the finger just was, so further taps are
     * ignored until the screen shows the new step and a moment has passed: a double tap never
     * logs two sets or skips a warm-up item unseen.
     */
    private fun settled(block: suspend () -> Unit) = run(settle = true, block)

    /** Runs one action at a time, so a double tap never logs a set twice. */
    private fun run(settle: Boolean, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        val before = data.value?.active
        viewModelScope.launch {
            try {
                block()
                if (settle) {
                    withTimeoutOrNull(SETTLE_TIMEOUT_MILLIS) { data.first { it?.active != before } }
                    delay(TAP_COOLDOWN_MILLIS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IllegalStateException) {
                // A tap on a screen that was already out of date, such as Done during a rest.
            } catch (e: Exception) {
                _error.value = e.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }

    /** Takes back the last set; from the summary, that reopens the workout on it. */
    fun undoLastSet() = settled {
        if (repository.undoLastSet() && _route.value is GymRoute.Summary) _route.value = GymRoute.Workout
    }

    /** The set "Undo last set" would take back, shown during the rest after it. */
    val undoableSet: StateFlow<String?> = repository.undoableSet.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private companion object {
        const val SETTLE_TIMEOUT_MILLIS = 1_500L
        const val TAP_COOLDOWN_MILLIS = 350L
    }
}