package dev.habita.app.core.sync

import dev.habita.app.core.network.ApiException
import dev.habita.app.core.network.HabitaApi
import dev.habita.app.shared.api.SyncPull
import dev.habita.app.shared.api.SyncPush
import dev.habita.app.shared.api.SyncPushResult

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