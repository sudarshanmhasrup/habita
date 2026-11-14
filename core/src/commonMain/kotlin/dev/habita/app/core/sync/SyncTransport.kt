package dev.habita.app.core.sync

import dev.habita.app.core.network.ApiException
import dev.habita.app.core.network.HabitaApi
import dev.habita.app.shared.api.SyncLive
import dev.habita.app.shared.api.SyncPull
import dev.habita.app.shared.api.SyncPush
import dev.habita.app.shared.api.SyncPushResult
import kotlinx.serialization.json.Json

/** How the engine talks to the server; tests swap in a fake. */
interface SyncTransport {
    /** Throws [ApiException] when the server can't be reached or refuses. */
    suspend fun push(push: SyncPush): SyncPushResult

    suspend fun pull(since: Long, limit: Int): SyncPull
}

class ApiSyncTransport(private val api: HabitaApi) : SyncTransport {
    override suspend fun push(push: SyncPush): SyncPushResult = api.post("sync/push", push)

    override suspend fun pull(since: Long, limit: Int): SyncPull = api.get("sync/pull?since=$since&limit=$limit")
}

/**
 * The server's `/sync/live` socket. The server repeats its position every half minute, so a socket
 * quiet for longer than [SILENCE_MILLIS] is taken as dropped, even if the network never said so.
 */
class ApiLiveTransport(private val api: HabitaApi) : LiveTransport {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun listen(onCursor: suspend (Long) -> Unit) = api.listen("sync/live", SILENCE_MILLIS) { text ->
        // A message this version can't read is skipped, not fatal.
        val message = runCatching { json.decodeFromString(SyncLive.serializer(), text) }.getOrNull()
        if (message != null) onCursor(message.cursor)
    }

    private companion object {
        const val SILENCE_MILLIS = 75_000L
    }
}