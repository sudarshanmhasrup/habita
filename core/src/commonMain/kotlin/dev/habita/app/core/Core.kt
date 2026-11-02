package dev.habita.app.core

import dev.habita.app.core.data.DatabaseFactory
import dev.habita.app.core.data.Settings
import dev.habita.app.core.db.CoreDatabase
import dev.habita.app.core.network.HabitaApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Build-time settings. [serverUrl] is the Habita server, [userName] the name used in the greeting.
 */
data class AppConfig(
    val serverUrl: String,
    val userName: String,
)

/**
 * What every feature shares: the settings, the server client and a scope that lives as long as the
 * app, for work such as syncing that must not stop when a screen closes.
 */
class Core(val config: AppConfig, val databases: DatabaseFactory) {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: Settings = Settings(CoreDatabase(databases.open("core.db", CoreDatabase.Schema)), scope)

    val api: HabitaApi = HabitaApi(config.serverUrl, settings)
}