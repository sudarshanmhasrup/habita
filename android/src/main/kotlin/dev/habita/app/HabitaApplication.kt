package dev.habita.app

import android.app.Application
import android.content.Context
import android.content.Intent
import dev.habita.app.compose.HabitaApp
import dev.habita.app.core.AppConfig
import dev.habita.app.core.data.AndroidDatabaseFactory
import dev.habita.app.feature.expense.ExpenseFeature
import dev.habita.app.feature.expense.android.ExpenseHost
import dev.habita.app.feature.gym.GymFeature
import dev.habita.app.feature.gym.android.GymHost
import dev.habita.app.feature.gym.android.WaistReminder

class HabitaApplication : Application(), ExpenseHost, GymHost {
    /** Created on first use and kept for the life of the process. */
    val app: HabitaApp by lazy {
        HabitaApp(
            config = AppConfig(
                serverUrl = BuildConfig.SERVER_URL,
                userName = BuildConfig.USER_NAME,
                loginEnabled = BuildConfig.LOGIN_ENABLED,
            ),
            databases = AndroidDatabaseFactory(this),
            installer = AndroidAppInstaller(this),
        )
    }

    override fun onCreate() {
        super.onCreate()
        WaistReminder.schedule(this)
    }

    override val expense: ExpenseFeature get() = app.expense

    override val gym: GymFeature get() = app.gym

    override val notificationIcon: Int = R.drawable.ic_launcher_monochrome

    override fun launchIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}