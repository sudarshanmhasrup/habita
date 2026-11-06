package dev.habita.app.feature.gym

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
import dev.habita.app.core.ui.ScreenTransition
import dev.habita.app.feature.gym.data.GymRepository
import dev.habita.app.feature.gym.ui.GymRoute
import dev.habita.app.feature.gym.ui.GymViewModel
import dev.habita.app.feature.gym.ui.PreStartScreen
import dev.habita.app.feature.gym.ui.SelectScreen
import dev.habita.app.feature.gym.ui.SummaryScreen
import dev.habita.app.feature.gym.ui.WorkoutScreen
import dev.habita.app.feature.gym.ui.backupDate
import dev.habita.app.feature.gym.ui.currentDay
import dev.habita.app.feature.gym.ui.daysBetween
import dev.habita.app.feature.gym.ui.estimateMinutes
import dev.habita.app.feature.gym.ui.startOfWeek
import dev.habita.app.feature.gym.data.RestoreResult
import dev.habita.app.feature.gym.ui.rememberFileOpener
import dev.habita.app.feature.gym.ui.rememberFileSaver
import dev.habita.app.feature.gym.ui.rememberTextCopier
import dev.habita.app.feature.gym.ui.today as todayDate
import dev.habita.app.shared.gym.Body
import dev.habita.app.shared.gym.Rotation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    /** Sunday, and the waist hasn't been measured this week. */
    val waistDue: Boolean = false,
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

    /** The coming Sundays that need a waist reminder, for platforms that queue them ahead. */
    val waistReminderDays: Flow<List<String>> =
        combine(repository.body, currentDay) { body, day -> Body.waistReminderDays(body, day, 4) }.distinctUntilChanged()

    /** Whether to remind about the waist today; for the Sunday notification. */
    suspend fun waistDue(): Boolean = Body.waistDue(repository.body.first(), todayDate())

    val today: Flow<GymToday?> = combine(
        repository.program,
        repository.sessions,
        repository.active,
        repository.body,
        currentDay,
    ) { program, sessions, active, body, day ->
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
            waistDue = Body.waistDue(body, day),
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
    val openFile = rememberFileOpener { text ->
        if (text == null) return@rememberFileOpener
        scope.launch {
            val result = viewModel.restore(text)
            backupMessage = when {
                result == null -> "That file isn't a Habita backup."
                result.total == 0 -> "Nothing to restore: the phone already has everything in that file."
                else -> "Restored ${restoredLine(result)}. Anything newer on the phone was kept."
            }
        }
    }
    val checkIn by viewModel.checkIn.collectAsStateWithLifecycle()
    var checkInMessage by remember { mutableStateOf<String?>(null) }
    val copyText = rememberTextCopier()
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
        // Deeper screens slide in from the right; going back slides the other way.
        ScreenTransition(route, isForward = { from, to -> to.depth > from.depth }) { screen ->
            when (screen) {
                GymRoute.Select -> SelectScreen(
                    data = current,
                    onBack = onExit,
                    onOpenWorkout = viewModel::openWorkout,
                    onResume = viewModel::resume,
                    onDiscard = viewModel::discard,
                    onRetry = viewModel::retrySync,
                    onRestore = openFile,
                    onBackup = {
                        scope.launch { saveFile("habita-backup-${backupDate(nowMillis())}.json", viewModel.backup()) }
                    },
                    backupMessage = backupMessage,
                    onWeight = viewModel::logWeight,
                    onWaist = viewModel::logWaist,
                    onNewGym = viewModel::newGym,
                    onSetBody = viewModel::setBody,
                    checkIn = checkIn,
                    onCopyCheckIn = {
                        scope.launch {
                            val text = viewModel.checkInText() ?: return@launch
                            copyText(text)
                            checkInMessage = "Copied. Paste it into Claude or ChatGPT, in the chat where your goal is saved."
                        }
                    },
                    checkInMessage = checkInMessage,
                )

                is GymRoute.PreStart -> PreStartScreen(
                    workoutId = screen.workoutId,
                    data = current,
                    targets = preStartTargets,
                    onBack = { viewModel.back() },
                    onStart = { viewModel.start(screen.workoutId) },
                    onSwap = viewModel::swap,
                )

                GymRoute.Workout -> current.active?.let { active ->
                    WorkoutScreen(active, current, viewModel, onBack = { viewModel.back() })
                }

                is GymRoute.Summary -> SummaryScreen(report = report, onDone = { viewModel.back() })
            }
        }
    }
}

/** "12 workouts, 300 sets and 20 body days". */
private fun restoredLine(result: RestoreResult): String {
    fun count(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"
    val parts = listOfNotNull(
        count(result.workouts, "workout", "workouts").takeIf { result.workouts > 0 },
        count(result.sets, "set", "sets").takeIf { result.sets > 0 },
        count(result.bodyDays, "body day", "body days").takeIf { result.bodyDays > 0 },
        count(result.other, "other record", "other records").takeIf { result.other > 0 },
    )
    return if (parts.size <= 1) parts.joinToString() else parts.dropLast(1).joinToString(", ") + " and " + parts.last()
}