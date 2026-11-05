package dev.habita.app.core.sync

import dev.habita.app.core.network.ApiException
import dev.habita.app.shared.api.SyncPull
import dev.habita.app.shared.api.SyncPush
import dev.habita.app.shared.api.SyncRecord
import dev.habita.app.shared.api.winsOver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncState {
    data object Idle : SyncState

    data object Syncing : SyncState

    data class Failed(val error: ApiException) : SyncState
}

/** Where the engine keeps its place: the server's cursor and which server it belongs to. */
interface SyncCheckpoint {
    fun cursor(): Long

    fun serverId(): String?

    fun saveCheckpoint(cursor: Long, serverId: String)
}

/**
 * Keeps every feature's data in step with the server. The app works fully offline: edits are saved
 * on the phone and marked dirty, and the engine sends them once the server is reachable.
 *
 * A sync first pulls what changed on the server, then pushes local changes. A pulled record
 * replaces the local one unless the local one has unsent changes that win by the same rule the
 * server uses ([winsOver]), so both sides always settle on the same copy.
 *
 * It runs after login, on [request] (features call it after every local change) and, while the
 * server can't be reached, again after 10 seconds, doubling up to a minute.
 */
class SyncEngine(
    private val scope: CoroutineScope,
    private val transport: SyncTransport,
    private val checkpoint: SyncCheckpoint,
    private val loggedIn: StateFlow<Boolean>,
) {
    private val tables = mutableMapOf<String, LocalSyncTable>()
    private val beforeSync = mutableListOf<suspend () -> Unit>()
    private val mutex = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var retry: Job? = null
    private var failures = 0

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Starts syncing. Call once, after every feature has registered its collections. */
    fun start() {
        scope.launch {
            loggedIn.collect { if (it) request() }
        }
        scope.launch {
            for (request in requests) {
                // Waits briefly so a burst of changes goes out as one push.
                delay(DEBOUNCE_MILLIS)
                if (!sync() && loggedIn.value) scheduleRetry()
            }
        }
    }

    /** Adds a feature's collection. Call at startup, before the first sync. */
    fun register(table: LocalSyncTable) {
        tables[table.collection] = table
    }

    /** Adds work that runs at the start of every sync, such as fetching the workout plan. */
    fun beforeEachSync(step: suspend () -> Unit) {
        beforeSync += step
    }

    /** Asks for a sync soon. Cheap to call after every change. */
    fun request() {
        requests.trySend(Unit)
    }

    /** Syncs now and returns whether it worked. */
    suspend fun sync(): Boolean = mutex.withLock {
        if (!loggedIn.value) return false
        retry?.cancel()
        _state.value = SyncState.Syncing
        try {
            beforeSync.forEach { it() }
            pull()
            push()
            failures = 0
            _state.value = SyncState.Idle
            true
        } catch (e: CancellationException) {
            _state.value = SyncState.Idle
            throw e
        } catch (e: ApiException) {
            _state.value = SyncState.Failed(e)
            false
        } catch (e: Exception) {
            // An unreadable response: report it like a server error and try again later.
            _state.value = SyncState.Failed(ApiException.Server(0, e.message ?: "Sync failed"))
            false
        }
    }

    private suspend fun pull() {
        var since = checkpoint.cursor()
        while (true) {
            val page = transport.pull(since, PULL_PAGE)
            val known = checkpoint.serverId()
            if (known != null && known != page.serverId) {
                // The server's database was replaced, so the cursor means nothing there: start
                // over, and send everything this device has, in case the server lost it.
                tables.values.forEach { it.markAllDirty() }
                checkpoint.saveCheckpoint(0, page.serverId)
                since = 0
                continue
            }
            apply(page)
            checkpoint.saveCheckpoint(page.cursor, page.serverId)
            since = page.cursor
            if (!page.hasMore) return
        }
    }

    private fun apply(page: SyncPull) {
        page.collections.forEach { (collection, records) ->
            // A collection from a newer app version is left for that version.
            val table = tables[collection] ?: return@forEach
            val winners = records.filter { record ->
                val local = table.local(record.id)
                local == null || !local.dirty || record.winsOver(local.record)
            }
            if (winners.isNotEmpty()) table.applyRemote(winners)
        }
    }

    private suspend fun push() {
        val dirty = tables.values.flatMap { table -> table.dirty().map { table.collection to it } }
        dirty.chunked(PUSH_BATCH).forEach { batch ->
            val collections: Map<String, List<SyncRecord>> = batch.groupBy({ it.first }, { it.second })
            // Refused records are cleared too: sending them again unchanged can't succeed.
            transport.push(SyncPush(collections))
            collections.forEach { (collection, records) -> tables.getValue(collection).markClean(records) }
        }
    }

    private fun scheduleRetry() {
        val failure = (_state.value as? SyncState.Failed)?.error
        if (failure is ApiException.Unauthorized || failure is ApiException.NotConfigured) return
        failures++
        val wait = minOf(FIRST_RETRY_MILLIS shl (failures - 1).coerceAtMost(6), MAX_RETRY_MILLIS)
        retry?.cancel()
        retry = scope.launch {
            delay(wait)
            request()
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 500L
        const val FIRST_RETRY_MILLIS = 10_000L
        const val MAX_RETRY_MILLIS = 60_000L
        const val PULL_PAGE = 500
        const val PUSH_BATCH = 500
    }
}