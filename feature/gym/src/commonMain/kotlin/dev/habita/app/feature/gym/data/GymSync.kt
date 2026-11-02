package dev.habita.app.feature.gym.data

import dev.habita.app.core.Core
import dev.habita.app.core.network.ApiException
import dev.habita.app.shared.api.ProgramResponse
import dev.habita.app.shared.api.PullResponse
import dev.habita.app.shared.api.PushResponse
import dev.habita.app.shared.api.SyncChanges
import kotlinx.coroutines.CancellationException
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

/**
 * Keeps the local database and the server in step: fetches the program, pushes local changes, then
 * pulls everything after the saved cursor. It runs after login, when the gym opens and after every
 * local change. Failures, such as being offline at the gym, retry every 30 seconds.
 */
class GymSync(private val store: GymStore, private val core: Core) {
    private val mutex = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var retry: Job? = null

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    init {
        core.scope.launch {
            core.settings.token.collect { if (it != null) request() }
        }
        core.scope.launch {
            for (request in requests) {
                // Waits briefly so a burst of changes goes out as one push.
                delay(DEBOUNCE_MILLIS)
                if (!sync()) scheduleRetry()
            }
        }
    }

    fun request() {
        requests.trySend(Unit)
    }

    /** Syncs now and returns whether it worked. */
    suspend fun sync(): Boolean = mutex.withLock {
        if (core.settings.token.value == null) return false
        retry?.cancel()
        _state.value = SyncState.Syncing
        try {
            val program: ProgramResponse = core.api.get("program")
            if (store.program() != program) store.saveProgram(program)

            val dirty = store.dirty()
            if (dirty.isNotEmpty()) {
                core.api.post<SyncChanges, PushResponse>("sync/push", dirty)
                store.markClean(dirty)
            }

            val pulled: PullResponse = core.api.get("sync/pull?since=${store.cursor()}")
            store.merge(pulled.changes, pulled.cursor)
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

    private fun scheduleRetry() {
        val failure = (_state.value as? SyncState.Failed)?.error
        if (failure is ApiException.Unauthorized || failure is ApiException.NotConfigured) return
        retry?.cancel()
        retry = core.scope.launch {
            delay(RETRY_MILLIS)
            request()
        }
    }

    private fun SyncChanges.isNotEmpty() =
        sessions.isNotEmpty() || sets.isNotEmpty() || warmUps.isNotEmpty() || steps.isNotEmpty()

    private companion object {
        const val DEBOUNCE_MILLIS = 500L
        const val RETRY_MILLIS = 30_000L
    }
}