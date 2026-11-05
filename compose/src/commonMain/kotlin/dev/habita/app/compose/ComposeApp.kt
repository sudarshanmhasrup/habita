package dev.habita.app.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import dev.habita.app.core.ui.HabitaScreen
import dev.habita.app.core.ui.ScreenTransition
import dev.habita.app.feature.auth.LoginScreen
import dev.habita.app.feature.expense.ExpenseScreen
import dev.habita.app.feature.gym.GymScreen

/**
 * The app: the login screen without a token (when login is on), otherwise home and the features
 * opened from it.
 * [onDarkThemeChange] lets the platform match its system bars to the theme.
 */
@Composable
fun ComposeApp(app: HabitaApp, onDarkThemeChange: (Boolean) -> Unit = {}) {
    val settings = app.core.settings
    val token by settings.token.collectAsStateWithLifecycle()
    val darkTheme by settings.darkTheme.collectAsStateWithLifecycle()
    val accent by settings.accent.collectAsStateWithLifecycle()
    val update by (app.updates?.state ?: noUpdates).collectAsStateWithLifecycle()
    val gymToday by app.gym.today.collectAsStateWithLifecycle(null)
    val expenseToday by app.expense.today.collectAsStateWithLifecycle(null)
    val expenseRequest by app.expense.openRequest.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

    LaunchedEffect(darkTheme) { onDarkThemeChange(darkTheme) }
    val loggedOut = token == null && app.core.config.loginEnabled
    LaunchedEffect(loggedOut) { if (loggedOut) screen = Screen.Home }
    // A payment notification was tapped: the expense screen takes the request from here.
    LaunchedEffect(expenseRequest) { if (expenseRequest != null) screen = Screen.Expense }

    HabitaTheme(darkTheme, accent) {
        // Home is the root: opening a feature slides forward, leaving it slides back.
        ScreenTransition(
            target = if (loggedOut) null else screen,
            isForward = { from, to -> from == null || (from == Screen.Home && to != null) },
        ) { current ->
            when (current) {
                null -> LoginScreen(app.core)
                Screen.Home -> HabitaScreen {
                    HomeScreen(
                        userName = app.core.config.userName,
                        hour = currentHour(),
                        darkTheme = darkTheme,
                        accent = accent,
                        gym = gymToday,
                        expense = expenseToday,
                        update = update,
                        onInstallUpdate = { app.updates?.install() },
                        onDarkTheme = settings::setDarkTheme,
                        onAccent = settings::setAccent,
                        onStartWorkout = { workoutId ->
                            app.gym.requestOpen(workoutId)
                            screen = Screen.Gym
                        },
                        onOpenGym = { screen = Screen.Gym },
                        onOpenExpense = { screen = Screen.Expense },
                        onAddExpense = {
                            app.expense.requestAdd()
                            screen = Screen.Expense
                        },
                    )
                }
                Screen.Gym -> GymScreen(app.gym, onExit = { screen = Screen.Home })
                Screen.Expense -> ExpenseScreen(app.expense, onExit = { screen = Screen.Home })
            }
        }
    }
}

private enum class Screen { Home, Gym, Expense }

/** Platforms without an installer (iOS, desktop) never have an update to show. */
private val noUpdates: StateFlow<UpdateState> = MutableStateFlow(UpdateState.None)