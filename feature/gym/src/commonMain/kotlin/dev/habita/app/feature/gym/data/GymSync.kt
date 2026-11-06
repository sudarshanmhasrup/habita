package dev.habita.app.feature.gym.data

import dev.habita.app.core.Core
import dev.habita.app.core.sync.LocalRecord
import dev.habita.app.core.sync.LocalSyncTable
import dev.habita.app.core.sync.SyncState
import dev.habita.app.shared.api.ProgramResponse
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.SyncRecord
import dev.habita.app.shared.api.gymChanges
import dev.habita.app.shared.api.toCollections
import kotlinx.coroutines.flow.StateFlow

/**
 * The gym's part in syncing: its collections, and fetching the workout plan at the start of every
 * sync. The app's sync engine does the rest.
 */
class GymSync(private val store: GymStore, private val core: Core) {
    /** The plain-record collections, such as the body log. */
    internal val records = gymRecordTables(store)

    init {
        listOf(
            SyncCollections.GYM_SESSIONS,
            SyncCollections.GYM_SETS,
            SyncCollections.GYM_WARM_UPS,
            SyncCollections.GYM_STEPS,
        ).forEach { core.sync.register(GymSyncTable(it, store)) }
        records.forEach(core.sync::register)
        core.sync.beforeEachSync {
            val program: ProgramResponse = core.api.get("program")
            if (store.program() != program) store.saveProgram(program)
        }
    }

    val state: StateFlow<SyncState> = core.sync.state

    fun request() = core.sync.request()
}

private class GymSyncTable(override val collection: String, private val store: GymStore) : LocalSyncTable {
    override fun dirty(): List<SyncRecord> = store.dirty(collection).toCollections()[collection].orEmpty()

    override fun markClean(pushed: List<SyncRecord>) = store.markClean(gymChanges(mapOf(collection to pushed)))

    override fun local(id: String): LocalRecord? {
        val (changes, dirty) = store.local(collection, id) ?: return null
        val record = changes.toCollections()[collection]?.singleOrNull() ?: return null
        return LocalRecord(record, dirty)
    }

    override fun applyRemote(records: List<SyncRecord>) = store.applyRemote(gymChanges(mapOf(collection to records)))

    override fun markAllDirty() = store.markAllDirty(collection)
}