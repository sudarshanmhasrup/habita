package dev.habita.app.feature.gym

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.habita.app.core.Core
import dev.habita.app.core.data.nowMillis
import dev.habita.app.core.ui.HabitaScreen
import dev.habita.app.feature.gym.data.GymRepository
import dev.habita.app.feature.gym.ui.GymRoute
import dev.habita.app.feature.gym.ui.GymViewModel
import dev.habita.app.feature.gym.ui.PreStartScreen
import dev.habita.app.feature.gym.ui.SelectScreen
import dev.habita.app.feature.gym.ui.SummaryScreen
import dev.habita.app.feature.gym.ui.WorkoutScreen
import dev.habita.app.feature.gym.ui.backupDate
import dev.habita.app.feature.gym.ui.daysBetween
import dev.habita.app.feature.gym.ui.estimateMinutes
import dev.habita.app.feature.gym.ui.startOfWeek
import dev.habita.app.feature.gym.ui.rememberFileSaver
import dev.habita.app.shared.gym.Rotation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * What the home screen shows about the gym: today's workout ([inProgress] when one is running),
 * how long it takes, and which days of this week (0 is Monday) had a finished workout.
 */
data class GymToday(
    val workoutId: String,
    val name: String,
    val detail: String,
    val inProgress: Boolean,
    val trainedDays: Set<Int>,
    val todayIndex: Int,
    val workoutsThisWeek: Int,
)

/** The gym feature. Create it once at startup, so syncing starts as soon as the user is logged in. */
class GymFeature(core: Core) {
    internal val repository = GymRepository(core)

    private val _openRequest = MutableStateFlow<Pair<String, Long>?>(null)

    /** A workout to open from outside the gym, such as home's Start button. */
    internal val openRequest: StateFlow<Pair<String, Long>?> = _openRequest.asStateFlow()

    /** Shows [workoutId] ready to start, or the workout in progress, next time the gym opens. */
    fun requestOpen(workoutId: String) {
        _openRequest.value = workoutId to nowMillis()
    }

    internal fun consumeOpenRequest() {
        _openRequest.value = null
    }

    val today: Flow<GymToday?> = combine(repository.program, repository.sessions, repository.active) { program, sessions, active ->
        val plan = program?.program ?: return@combine null
        val workout = active?.workout ?: Rotation.next(plan, sessions)
        val now = nowMillis()
        val weekStart = startOfWeek(now)
        val trained = sessions
            .filter { !it.deleted && it.endedAt != null && it.startedAt >= weekStart }
            .map { daysBetween(weekStart, it.startedAt) }
            .toSet()
        val warmUp = plan.warmUps.firstOrNull { it.id == workout.warmUpId }
        GymToday(
            workoutId = workout.id,
            name = workout.name,
            detail = "${workout.exercises.size} exercises · ~${estimateMinutes(workout, warmUp)} min",
            inProgress = active != null,
            trainedDays = trained,
            todayIndex = daysBetween(weekStart, now),
            workoutsThisWeek = trained.size,
        )
    }
}

/** The gym, from picking a workout to its summary. [onExit] goes back to the home screen. */
@Composable
fun GymScreen(feature: GymFeature, onExit: () -> Unit) {
    val viewModel = viewModel { GymViewModel(feature.repository) }
    LaunchedEffect(viewModel) { viewModel.open() }
    val route by viewModel.route.collectAsStateWithLifecycle()
    val data by viewModel.data.collectAsStateWithLifecycle()
    val openRequest by feature.openRequest.collectAsStateWithLifecycle()
    // Opened from home's Start button: go straight to that workout, unless one is running.
    LaunchedEffect(openRequest, data != null) {
        val (workoutId, _) = openRequest ?: return@LaunchedEffect
        val current = data ?: return@LaunchedEffect
        feature.consumeOpenRequest()
        if (current.active == null) viewModel.openWorkout(workoutId)
    }
    val preStartTargets by viewModel.preStartTargets.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val saveFile = rememberFileSaver { saved ->
        backupMessage = when (saved) {
            true -> "Backup saved. Keep it somewhere safe, like Google Drive."
            false -> "Couldn't save the backup. Try another place."
            null -> null
        }
    }

    // The workout screen with no workout, for example after a discard: go back to the list. The
    // check runs on the latest route, so finishing, which moves on to the summary, is left alone.
    val hasActive = data?.active != null
    LaunchedEffect(route, hasActive, data != null) {
        if (data != null && !hasActive && viewModel.route.value == GymRoute.Workout) viewModel.back()
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = { if (!viewModel.back()) onExit() },
    )

    HabitaScreen {
        val current = data ?: return@HabitaScreen
        Crossfade(route) { screen ->
            when (screen) {
                GymRoute.Select -> SelectScreen(
                    data = current,
                    onBack = onExit,
                    onOpenWorkout = viewModel::openWorkout,
                    onResume = viewModel::resume,
                    onDiscard = viewModel::discard,
                    onRetry = viewModel::retrySync,
                    onBackup = {
                        scope.launch { saveFile("habita-backup-${backupDate(nowMillis())}.json", viewModel.backup()) }
                    },
                    backupMessage = backupMessage,
                )

                is GymRoute.PreStart -> PreStartScreen(
                    workoutId = screen.workoutId,
                    data = current,
                    targets = preStartTargets,
                    onBack = { viewModel.back() },
                    onStart = { viewModel.start(screen.workoutId) },
                )

                GymRoute.Workout -> current.active?.let { active ->
                    WorkoutScreen(active, current, viewModel, onBack = { viewModel.back() })
                }

                is GymRoute.Summary -> SummaryScreen(report = report, onDone = { viewModel.back() })
            }
        }
    }
}