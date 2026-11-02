package dev.habita.app.compose

import dev.habita.app.core.AppConfig
import dev.habita.app.core.Core
import dev.habita.app.core.data.DatabaseFactory
import dev.habita.app.feature.gym.GymFeature

/**
 * The app's object graph. Each platform entry point creates one and keeps it for the life of the
 * process.
 */
class HabitaApp(config: AppConfig, databases: DatabaseFactory) {
    val core = Core(config, databases)
    val gym = GymFeature(core)
}