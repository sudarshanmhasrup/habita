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
import kotlinx.serialization.Serializable

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

    /** Pulled records this version couldn't read yet, kept to try again after an update. */
    fun pending(): List<PendingRecord> = emptyList()

    fun savePending(records: List<PendingRecord>) {}

    /** The highest server position this device has seen, its own pushes included. */
    fun highestSeen(): Long = 0

    fun saveHighestSeen(position: Long) {}
}

/** A pulled record kept aside: its collection is unknown here, or its data can't be read yet. */
@Serializable
data class PendingRecord(val collection: String, val record: SyncRecord)

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

    private val _refused = MutableStateFlow(0)

    /**
     * How many local changes the server refused on the last push. They stay unsent and are offered
     * again on every sync, for example until a newer server that understands them is deployed.
     */
    val refused: StateFlow<Int> = _refused.asStateFlow()

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
        retryPending()
        var since = checkpoint.cursor()
        while (true) {
            val page = transport.pull(since, PULL_PAGE)
            val known = checkpoint.serverId()
            // A position below one this device has seen means the server's database was restored
            // from a backup, which keeps its id: it lost what came after, this device's pushes too.
            // Mid-history pages stop short of the end, so only the last page is compared with pushes.
            val restored = page.cursor < since || (!page.hasMore && page.cursor < checkpoint.highestSeen())
            if (known != null && (known != page.serverId || restored)) {
                // The server's database was replaced, so the cursor means nothing there: start
                // over, and send everything this device has, in case the server lost it.
                tables.values.forEach { it.markAllDirty() }
                checkpoint.saveCheckpoint(0, page.serverId)
                checkpoint.saveHighestSeen(0)
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
        val unreadable = mutableListOf<PendingRecord>()
        page.collections.forEach { (collection, records) ->
            unreadable += applyCollection(collection, records)
        }
        // A kept-aside copy that a newer pulled one replaces is stale: drop it.
        val pulled = page.collections.flatMap { (collection, records) -> records.map { (collection to it.id) to it } }.toMap()
        val kept = checkpoint.pending().filter { old ->
            val newer = pulled[old.collection to old.record.id]
            newer == null || old.record.winsOver(newer)
        }
        if (unreadable.isNotEmpty() || kept.size != checkpoint.pending().size) savePending(kept + unreadable)
    }

    /**
     * Saves the records this version reads and returns the ones it can't, to keep aside. A
     * [retried] record was pulled earlier, so it only replaces a local copy it is newer than.
     */
    private fun applyCollection(collection: String, records: List<SyncRecord>, retried: Boolean = false): List<PendingRecord> {
        // A collection from a newer app version is kept for when this app is updated.
        val table = tables[collection] ?: return records.map { PendingRecord(collection, it) }
        val (readable, unreadable) = records.partition(table::canRead)
        if (readable.isNotEmpty()) {
            table.inTransaction {
                val winners = if (table.mergesItself) readable else readable.filter { record ->
                    val local = table.local(record.id)
                    local == null || (!local.dirty && !retried) || record.winsOver(local.record)
                }
                if (winners.isNotEmpty()) table.applyRemote(winners)
            }
        }
        return unreadable.map { PendingRecord(collection, it) }
    }

    /** Offers the records kept aside again: after an update, this version may read them. */
    private fun retryPending() {
        val pending = checkpoint.pending()
        if (pending.isEmpty()) return
        val still = pending.groupBy({ it.collection }, { it.record }).flatMap { (collection, records) ->
            applyCollection(collection, records, retried = true)
        }
        checkpoint.savePending(still)
    }

    /** Keeps one copy per record, the one that wins. */
    private fun savePending(records: List<PendingRecord>) {
        checkpoint.savePending(
            records.groupBy { it.collection to it.record.id }.values.map { copies ->
                copies.reduce { kept, next -> if (next.record.winsOver(kept.record)) next else kept }
            },
        )
    }

    private suspend fun push() {
        val dirty = tables.values.flatMap { table -> table.dirty().map { table.collection to it } }
        var refused = 0
        dirty.chunked(PUSH_BATCH).forEach { batch ->
            val collections: Map<String, List<SyncRecord>> = batch.groupBy({ it.first }, { it.second })
            val result = transport.push(SyncPush(collections))
            if (result.cursor > checkpoint.highestSeen()) checkpoint.saveHighestSeen(result.cursor)
            // Refusals a newer server could accept stay unsent, to offer again; the rest no server
            // will take, so they are cleared like sent ones.
            val kept = result.rejected.filter { it.retryable }.map { it.collection to it.id }.toSet()
            refused += kept.size
            collections.forEach { (collection, records) ->
                tables.getValue(collection).markClean(records.filter { (collection to it.id) !in kept })
            }
        }
        _refused.value = refused
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