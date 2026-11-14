package dev.habita.app.core

import dev.habita.app.core.data.DatabaseFactory
import dev.habita.app.core.data.PreferenceSyncTable
import dev.habita.app.core.data.Settings
import dev.habita.app.core.db.CoreDatabase
import dev.habita.app.core.network.HabitaApi
import dev.habita.app.core.sync.ApiLiveTransport
import dev.habita.app.core.sync.ApiSyncTransport
import dev.habita.app.core.sync.LiveSync
import dev.habita.app.core.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Build-time settings. [serverUrl] is the Habita server, [userName] the name used in the greeting.
 * Without [loginEnabled] the app never asks for a login and works from the phone alone.
 */
data class AppConfig(
    val serverUrl: String,
    val userName: String,
    val loginEnabled: Boolean = true,
)

/**
 * What every feature shares: the settings, the server client, the sync engine features register
 * their collections with, and a scope that lives as long as the app, for work such as syncing
 * that must not stop when a screen closes.
 */
class Core(val config: AppConfig, val databases: DatabaseFactory) {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = CoreDatabase(databases.open("core.db", CoreDatabase.Schema))

    val settings: Settings = Settings(database, scope)

    val api: HabitaApi = HabitaApi(config.serverUrl, settings)

    private val loggedIn = settings.token.map { it != null }.stateIn(scope, SharingStarted.Eagerly, settings.token.value != null)

    val sync: SyncEngine = SyncEngine(
        scope = scope,
        transport = ApiSyncTransport(api),
        checkpoint = settings,
        loggedIn = loggedIn,
    )

    /** Syncs as soon as another device changes something, while the app is in use. */
    val live: LiveSync = LiveSync(
        scope = scope,
        transport = ApiLiveTransport(api),
        checkpoint = settings,
        loggedIn = loggedIn,
        requestSync = sync::request,
    )

    init {
        // The theme and other preferences sync like any feature's data.
        sync.register(PreferenceSyncTable(database))
        settings.onPreferenceChanged = sync::request
    }
}