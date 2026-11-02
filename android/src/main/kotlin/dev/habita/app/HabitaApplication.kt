package dev.habita.app

import android.app.Application
import dev.habita.app.compose.HabitaApp
import dev.habita.app.core.AppConfig
import dev.habita.app.core.data.AndroidDatabaseFactory

class HabitaApplication : Application() {
    /** Created on first use and kept for the life of the process. */
    val app: HabitaApp by lazy {
        HabitaApp(
            config = AppConfig(serverUrl = BuildConfig.SERVER_URL, userName = BuildConfig.USER_NAME),
            databases = AndroidDatabaseFactory(this),
        )
    }
}