package dev.habita.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.habita.app.compose.ComposeApp
import dev.habita.app.feature.expense.android.OPEN_EXPENSE_EXTRA

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val app = (application as HabitaApplication).app
        applySystemBars(app.core.settings.darkTheme.value)
        openFromNotification(intent)
        setContent {
            ComposeApp(app, onDarkThemeChange = ::applySystemBars)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFromNotification(intent)
    }

    /** Opened from a payment notification: show that payment. */
    private fun openFromNotification(intent: Intent?) {
        val expenseId = intent?.getStringExtra(OPEN_EXPENSE_EXTRA) ?: return
        intent.removeExtra(OPEN_EXPENSE_EXTRA)
        (application as HabitaApplication).app.expense.requestOpen(expenseId)
    }

    /** Matches the status and navigation bar icons to the app's theme, not the system's. */
    private fun applySystemBars(darkTheme: Boolean) {
        val style = if (darkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}