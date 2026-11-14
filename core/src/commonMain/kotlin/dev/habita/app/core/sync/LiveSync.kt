package dev.habita.app.core.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** The server's live socket; tests swap in a fake. */
interface LiveTransport {
    /**
     * Opens the socket and calls [onCursor] with every server position it sends. Returns or throws
     * once the socket closes, fails to open, or goes quiet for too long.
     */
    suspend fun listen(onCursor: suspend (Long) -> Unit)
}

/**
 * Makes changes from other devices show up without waiting for a local edit. While the app is
 * logged in and in use ([setInUse]), it keeps a socket to the server open; the server sends its
 * position whenever something is saved, and when that differs from what this device has pulled,
 * it asks the [SyncEngine] for a sync. The socket only says when to sync, never what changed, so
 * without it (an older server, a network that blocks sockets) the app syncs exactly as before.
 *
 * A dropped socket is opened again after 2 seconds, doubling up to a minute while it keeps failing.
 */
class LiveSync(
    private val scope: CoroutineScope,
    private val transport: LiveTransport,
    private val checkpoint: SyncCheckpoint,
    private val loggedIn: StateFlow<Boolean>,
    private val requestSync: () -> Unit,
) {
    private val inUse = MutableStateFlow(false)

    /** Whether the app is in front of the owner. The socket stays closed in the background. */
    fun setInUse(value: Boolean) {
        inUse.value = value
    }

    /** Call once, at startup. */
    fun start() {
        scope.launch {
            combine(loggedIn, inUse) { loggedIn, inUse -> loggedIn && inUse }
                .distinctUntilChanged()
                .collectLatest { if (it) stayConnected() }
        }
    }

    private suspend fun stayConnected() {
        var failures = 0
        while (true) {
            var heard = false
            try {
                transport.listen { cursor ->
                    heard = true
                    if (needsSync(cursor, checkpoint.cursor())) requestSync()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Dropped or refused; try again below.
            }
            // A socket that worked starts the wait over; one that never did backs off further.
            failures = if (heard) 1 else failures + 1
            delay(reconnectDelay(failures))
        }
    }

    companion object {
        private const val FIRST_RECONNECT_MILLIS = 2_000L
        private const val MAX_RECONNECT_MILLIS = 60_000L

        /**
         * Whether to sync: the server holds changes this device hasn't pulled, or it is behind this
         * device, which means its database was restored and the sync engine should find out.
         */
        fun needsSync(serverCursor: Long, pulledCursor: Long): Boolean = serverCursor != pulledCursor

        /** How long to wait before opening the socket again after [failures] drops in a row. */
        fun reconnectDelay(failures: Int): Long {
            val doublings = (failures - 1).coerceIn(0, 6)
            return minOf(FIRST_RECONNECT_MILLIS shl doublings, MAX_RECONNECT_MILLIS)
        }
    }
}