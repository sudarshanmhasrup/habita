package dev.habita.app.compose

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.ui.HabitaScreen
import dev.habita.app.feature.auth.LoginScreen
import dev.habita.app.feature.gym.GymScreen

/**
 * The app: the login screen without a token, otherwise home and the features opened from it.
 * [onDarkThemeChange] lets the platform match its system bars to the theme.
 */
@Composable
fun ComposeApp(app: HabitaApp, onDarkThemeChange: (Boolean) -> Unit = {}) {
    val settings = app.core.settings
    val token by settings.token.collectAsStateWithLifecycle()
    val darkTheme by settings.darkTheme.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

    LaunchedEffect(darkTheme) { onDarkThemeChange(darkTheme) }
    LaunchedEffect(token) { if (token == null) screen = Screen.Home }

    HabitaTheme(darkTheme) {
        Crossfade(if (token == null) null else screen) { current ->
            when (current) {
                null -> LoginScreen(app.core)
                Screen.Home -> HabitaScreen {
                    HomeScreen(
                        userName = app.core.config.userName,
                        hour = currentHour(),
                        darkTheme = darkTheme,
                        onToggleTheme = { settings.setDarkTheme(!darkTheme) },
                        onOpenGym = { screen = Screen.Gym },
                    )
                }
                Screen.Gym -> GymScreen(app.gym, onExit = { screen = Screen.Home })
            }
        }
    }
}

private enum class Screen { Home, Gym }