package dev.habita.app.feature.gym

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.habita.app.core.Core
import dev.habita.app.core.ui.HabitaScreen
import dev.habita.app.feature.gym.data.GymRepository
import dev.habita.app.feature.gym.ui.GymRoute
import dev.habita.app.feature.gym.ui.GymViewModel
import dev.habita.app.feature.gym.ui.PreStartScreen
import dev.habita.app.feature.gym.ui.SelectScreen
import dev.habita.app.feature.gym.ui.SummaryScreen
import dev.habita.app.feature.gym.ui.WorkoutScreen

/** The gym feature. Create it once at startup, so syncing starts as soon as the user is logged in. */
class GymFeature(core: Core) {
    internal val repository = GymRepository(core)
}

/** The gym, from picking a workout to its summary. [onExit] goes back to the home screen. */
@Composable
fun GymScreen(feature: GymFeature, onExit: () -> Unit) {
    val viewModel = viewModel { GymViewModel(feature.repository) }
    LaunchedEffect(viewModel) { viewModel.open() }
    val route by viewModel.route.collectAsStateWithLifecycle()
    val data by viewModel.data.collectAsStateWithLifecycle()
    val preStartTargets by viewModel.preStartTargets.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()

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

    HabitaScreen(glow = route == GymRoute.Select) {
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

                is GymRoute.Summary -> SummaryScreen(
                    summary = summary,
                    program = current.program,
                    workoutName = current.sessions.firstOrNull { it.id == screen.sessionId }
                        ?.let { session -> current.program?.workout(session.workoutId)?.name },
                    onDone = onExit,
                )
            }
        }
    }
}