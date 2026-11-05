package dev.habita.app.feature.gym.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.habita.app.feature.gym.data.GymRepository
import dev.habita.app.feature.gym.data.SessionReport
import dev.habita.app.core.sync.SyncState
import dev.habita.app.feature.gym.workout.ActiveWorkout
import dev.habita.app.feature.gym.workout.Phase
import dev.habita.app.shared.gym.Effort
import dev.habita.app.shared.gym.ExerciseTarget
import dev.habita.app.shared.gym.Program
import dev.habita.app.shared.gym.Rotation
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface GymRoute {
    /** How far in the flow the screen is, so transitions know which way to slide. */
    val depth: Int
        get() = when (this) {
            Select -> 0
            is PreStart -> 1
            Workout -> 2
            is Summary -> 3
        }

    data object Select : GymRoute

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
) {
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
        repository.program,
        repository.sessions,
        repository.steps,
        repository.active,
        repository.sync.state,
    ) { program, sessions, steps, active, sync ->
        GymData(program?.program, sessions, steps, active, sync, repository.syncEnabled)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Targets for the pre-start screen, refreshed when synced history arrives. */
    val preStartTargets: StateFlow<List<ExerciseTarget>?> = _route
        .flatMapLatest { route ->
            if (route !is GymRoute.PreStart) {
                flowOf(null)
            } else {
                combine(repository.program, repository.sessions, repository.steps) { _, _, _ -> }
                    .mapLatest { repository.targets(route.workoutId) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val report: StateFlow<SessionReport?> = _route
        .flatMapLatest { route ->
            if (route is GymRoute.Summary) flow<SessionReport?> { emit(repository.report(route.sessionId)) } else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
            val hasActive = repository.active.first() != null
            val route = _route.value
            if (hasActive && route is GymRoute.Select) _route.value = GymRoute.Workout
            if (!hasActive && route == GymRoute.Workout) _route.value = GymRoute.Select
        }
        repository.sync.request()
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

    fun discard() = act { repository.discard() }

    fun warmUpDone() = act { repository.completeWarmUp(WarmUpStatus.DONE) }

    fun warmUpSkip() = act { repository.completeWarmUp(WarmUpStatus.SKIPPED) }

    fun warmUpMachineTaken() = act { repository.warmUpMachineTaken() }

    fun endBreak() = act {
        if (data.value?.active?.phase == Phase.BREAK) repository.endBreak()
    }

    fun endRest() = act {
        if (data.value?.active?.rest != null) repository.endRest()
    }

    fun logWarmUpSet(weightGrams: Int, reps: Int?) = act { repository.logWarmUpSet(weightGrams, reps) }

    fun extendRest(seconds: Int) = act { repository.extendRest(seconds) }

    /** Logs the current set; [achieved] is `null` for a skipped set. */
    fun logSet(weightGrams: Int, achieved: Int?, effort: Effort?) = act {
        repository.logSet(weightGrams, achieved, effort)?.let { _route.value = GymRoute.Summary(it) }
    }

    fun exerciseMachineTaken() = act { repository.exerciseMachineTaken() }

    fun setStep(exerciseId: String, step: Int) = act { repository.setStep(exerciseId, step) }

    fun finish() = act {
        repository.finish()?.let { _route.value = GymRoute.Summary(it) }
    }

    fun dismissError() {
        _error.value = null
    }

    /** Runs one action at a time, so a double tap never logs a set twice. */
    private fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
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
}