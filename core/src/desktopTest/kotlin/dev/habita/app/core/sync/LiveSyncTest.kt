package dev.habita.app.core.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveSyncTest {
    private class Checkpoint(var pulled: Long) : SyncCheckpoint {
        override fun cursor() = pulled

        override fun serverId() = "server-1"

        override fun saveCheckpoint(cursor: Long, serverId: String) {
            pulled = cursor
        }
    }

    /** A server socket the test drives: each [listen] takes the next script from [connections]. */
    private class FakeSocket : LiveTransport {
        val connections = Channel<suspend (suspend (Long) -> Unit) -> Unit>(Channel.UNLIMITED)
        var opened = 0
        var open = false

        override suspend fun listen(onCursor: suspend (Long) -> Unit) {
            val script = connections.receive()
            opened++
            open = true
            try {
                script(onCursor)
            } finally {
                open = false
            }
        }
    }

    private class Setup(pulled: Long = 5) {
        val scope = CoroutineScope(Dispatchers.Default)
        val checkpoint = Checkpoint(pulled)
        val socket = FakeSocket()
        val loggedIn = MutableStateFlow(true)
        val requests = Channel<Unit>(Channel.UNLIMITED)
        val live = LiveSync(scope, socket, checkpoint, loggedIn) { requests.trySend(Unit) }.apply { start() }
    }

    private suspend fun waitUntil(condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(10)
    }

    @Test
    fun `syncs only when the server position differs from the pulled one`() {
        assertFalse(LiveSync.needsSync(serverCursor = 5, pulledCursor = 5))
        assertTrue(LiveSync.needsSync(serverCursor = 6, pulledCursor = 5))
        // A server behind this device was restored; a sync lets the engine notice.
        assertTrue(LiveSync.needsSync(serverCursor = 3, pulledCursor = 5))
    }

    @Test
    fun `reconnects after 2 seconds, doubling up to a minute`() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L, 60_000L), (1..8).map(LiveSync::reconnectDelay))
    }

    @Test
    fun `a newer server position asks for a sync, the same one doesn't`() = runBlocking {
        val setup = Setup(pulled = 5)
        setup.socket.connections.send { send ->
            send(5)
            send(6)
            awaitCancellation()
        }
        setup.live.setInUse(true)

        withTimeout(5_000) { setup.requests.receive() }
        delay(200)
        assertTrue(setup.requests.tryReceive().isFailure, "Only the newer position should ask for a sync")
        setup.scope.cancel()
    }

    @Test
    fun `the socket is open only while logged in and in use`() = runBlocking {
        val setup = Setup()
        repeat(3) { setup.socket.connections.send { awaitCancellation() } }

        delay(200)
        assertEquals(0, setup.socket.opened, "Not in use yet")
        setup.live.setInUse(true)
        waitUntil { setup.socket.open }
        setup.live.setInUse(false)
        waitUntil { !setup.socket.open }
        setup.live.setInUse(true)
        waitUntil { setup.socket.open }
        setup.loggedIn.value = false
        waitUntil { !setup.socket.open }
        assertEquals(2, setup.socket.opened)
        setup.scope.cancel()
    }

    @Test
    fun `a dropped socket is opened again`() = runBlocking {
        val setup = Setup()
        setup.socket.connections.send { send -> send(5) }
        setup.socket.connections.send { awaitCancellation() }
        setup.live.setInUse(true)

        withTimeout(5_000) { waitUntil { setup.socket.opened == 2 && setup.socket.open } }
        setup.scope.cancel()
    }
}