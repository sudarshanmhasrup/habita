package dev.habita.app.compose

import dev.habita.app.core.AppConfig
import dev.habita.app.core.Core
import dev.habita.app.core.data.DatabaseFactory
import dev.habita.app.core.update.AppInstaller
import dev.habita.app.core.update.AppUpdates
import dev.habita.app.feature.expense.ExpenseFeature
import dev.habita.app.feature.gym.GymFeature

/**
 * The app's object graph. Each platform entry point creates one and keeps it for the life of the
 * process.
 */
class HabitaApp(config: AppConfig, databases: DatabaseFactory, installer: AppInstaller? = null) {
    val core = Core(config, databases)
    val gym = GymFeature(core)
    val expense = ExpenseFeature(core)

    /** Updates from the Habita server, where the platform can install them (Android). */
    val updates: AppUpdates? = installer?.let { AppUpdates(core, it) }

    init {
        core.sync.start()
        updates?.start()
    }
}