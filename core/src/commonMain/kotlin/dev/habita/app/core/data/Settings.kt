package dev.habita.app.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.habita.app.core.db.CoreDatabase
import dev.habita.app.core.theme.Accent
import dev.habita.app.core.sync.PendingRecord
import dev.habita.app.core.sync.SyncCheckpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

/**
 * The login token and where syncing got to, which stay on this device, and the theme, which is
 * a synced preference and follows the user to every device. All are read once at startup, so
 * the first screen is already the right one.
 */
class Settings(private val database: CoreDatabase, scope: CoroutineScope) : SyncCheckpoint {
    private val queries = database.settingQueries
    private val preferences = database.preferenceQueries

    /** Called after a synced preference changes here, so it can go to the server. */
    var onPreferenceChanged: () -> Unit = {}

    val token: StateFlow<String?> = watch(TOKEN, scope) { it }

    val darkTheme: StateFlow<Boolean> = watchPreference(DARK_THEME, scope) { it == "true" }

    val accent: StateFlow<Accent> = watchPreference(ACCENT, scope) { Accent.fromId(it) }

    fun saveToken(token: String) {
        queries.save(TOKEN, token)
    }

    fun clearToken() {
        queries.remove(TOKEN)
    }

    fun setDarkTheme(dark: Boolean) = savePreference(DARK_THEME, dark.toString())

    fun setAccent(accent: Accent) = savePreference(ACCENT, accent.id)

    private fun savePreference(key: String, value: String) {
        preferences.save(key, value, nowMillis(), 1)
        onPreferenceChanged()
    }

    override fun cursor(): Long = queries.select(SYNC_CURSOR).executeAsOneOrNull()?.toLongOrNull() ?: 0

    override fun serverId(): String? = queries.select(SYNC_SERVER).executeAsOneOrNull()

    override fun saveCheckpoint(cursor: Long, serverId: String) = database.transaction {
        queries.save(SYNC_CURSOR, cursor.toString())
        queries.save(SYNC_SERVER, serverId)
    }

    override fun pending(): List<PendingRecord> =
        queries.select(SYNC_PENDING).executeAsOneOrNull()
            ?.let { runCatching { pendingJson.decodeFromString<List<PendingRecord>>(it) }.getOrNull() }
            .orEmpty()

    override fun highestSeen(): Long = queries.select(SYNC_HIGHEST).executeAsOneOrNull()?.toLongOrNull() ?: 0

    override fun saveHighestSeen(position: Long) {
        queries.save(SYNC_HIGHEST, position.toString())
    }

    override fun savePending(records: List<PendingRecord>) {
        queries.save(SYNC_PENDING, pendingJson.encodeToString(records))
    }

    private fun <T> watchPreference(key: String, scope: CoroutineScope, read: (String?) -> T): StateFlow<T> =
        preferences.select(key)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map(read)
            .stateIn(scope, SharingStarted.Eagerly, read(preferences.select(key).executeAsOneOrNull()))

    private fun <T> watch(key: String, scope: CoroutineScope, read: (String?) -> T): StateFlow<T> =
        queries.select(key)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map(read)
            .stateIn(scope, SharingStarted.Eagerly, read(queries.select(key).executeAsOneOrNull()))

    private companion object {
        const val TOKEN = "token"
        const val DARK_THEME = "dark_theme"
        const val ACCENT = "accent"
        const val SYNC_CURSOR = "sync_cursor"
        const val SYNC_SERVER = "sync_server_id"
        const val SYNC_PENDING = "sync_pending"
        const val SYNC_HIGHEST = "sync_highest_seen"
        val pendingJson = Json { ignoreUnknownKeys = true }
    }
}