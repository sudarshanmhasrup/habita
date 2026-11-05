package dev.habita.app.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.habita.app.core.db.CoreDatabase
import dev.habita.app.core.sync.SyncCheckpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The login token, the theme and where syncing got to. The token and theme are read once at
 * startup, so the first screen is already the right one.
 */
class Settings(private val database: CoreDatabase, scope: CoroutineScope) : SyncCheckpoint {
    private val queries = database.settingQueries

    val token: StateFlow<String?> = watch(TOKEN, scope) { it }

    val darkTheme: StateFlow<Boolean> = watch(DARK_THEME, scope) { it == "true" }

    fun saveToken(token: String) {
        queries.save(TOKEN, token)
    }

    fun clearToken() {
        queries.remove(TOKEN)
    }

    fun setDarkTheme(dark: Boolean) {
        queries.save(DARK_THEME, dark.toString())
    }

    override fun cursor(): Long = queries.select(SYNC_CURSOR).executeAsOneOrNull()?.toLongOrNull() ?: 0

    override fun serverId(): String? = queries.select(SYNC_SERVER).executeAsOneOrNull()

    override fun saveCheckpoint(cursor: Long, serverId: String) = database.transaction {
        queries.save(SYNC_CURSOR, cursor.toString())
        queries.save(SYNC_SERVER, serverId)
    }

    private fun <T> watch(key: String, scope: CoroutineScope, read: (String?) -> T): StateFlow<T> =
        queries.select(key)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map(read)
            .stateIn(scope, SharingStarted.Eagerly, read(queries.select(key).executeAsOneOrNull()))

    private companion object {
        const val TOKEN = "token"
        const val DARK_THEME = "dark_theme"
        const val SYNC_CURSOR = "sync_cursor"
        const val SYNC_SERVER = "sync_server_id"
    }
}