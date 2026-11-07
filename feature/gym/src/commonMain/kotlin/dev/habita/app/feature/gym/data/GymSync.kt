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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

/**
 * The gym's part in syncing: its collections, and fetching the workout plan at the start of every
 * sync. The app's sync engine does the rest.
 */
class GymSync(private val store: GymStore, private val core: Core) {
    /** The plain-record collections, such as the body log. */
    internal val records = gymRecordTables(store)

    /** Every gym collection, typed and plain. */
    internal val tables: List<LocalSyncTable> = gymTypedTables(store) + records

    init {
        tables.forEach(core.sync::register)
        core.sync.beforeEachSync {
            // A plan the server can't send never holds back the records: the cached one stays.
            try {
                val program: ProgramResponse = core.api.get("program")
                if (store.program() != program) store.saveProgram(program)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    val state: StateFlow<SyncState> = core.sync.state

    fun request() = core.sync.request()
}

/** The collections the server reads too: sessions, sets, warm-ups and steps. */
internal fun gymTypedTables(store: GymStore): List<LocalSyncTable> = listOf(
    SyncCollections.GYM_SESSIONS,
    SyncCollections.GYM_SETS,
    SyncCollections.GYM_WARM_UPS,
    SyncCollections.GYM_STEPS,
).map { GymSyncTable(it, store) }

internal class GymSyncTable(override val collection: String, private val store: GymStore) : LocalSyncTable {
    override fun dirty(): List<SyncRecord> = store.dirty(collection).toCollections()[collection].orEmpty()

    override fun markClean(pushed: List<SyncRecord>) = store.markClean(gymChanges(mapOf(collection to pushed)))

    override fun local(id: String): LocalRecord? {
        val (changes, dirty) = store.local(collection, id) ?: return null
        val record = changes.toCollections()[collection]?.singleOrNull() ?: return null
        return LocalRecord(record, dirty)
    }

    override fun applyRemote(records: List<SyncRecord>) = store.applyRemote(gymChanges(mapOf(collection to records)))

    override fun markAllDirty() = store.markAllDirty(collection)

    override fun <T> inTransaction(block: () -> T): T = store.transaction(block)

    /** Saves restored records as unsent, so the server gets them too. */
    fun restore(records: List<SyncRecord>) = store.applyRemote(gymChanges(mapOf(collection to records)), dirty = true)

    override fun canRead(record: SyncRecord): Boolean =
        gymChanges(mapOf(collection to listOf(record))).toCollections()[collection].orEmpty().isNotEmpty()
}