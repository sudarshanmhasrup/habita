package dev.habita.app.feature.gym.data

import dev.habita.app.core.sync.LocalRecord
import dev.habita.app.core.sync.LocalSyncTable
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.SyncJson
import dev.habita.app.shared.api.SyncRecord
import dev.habita.app.shared.gym.Body
import dev.habita.app.shared.gym.BodyLog
import dev.habita.app.shared.gym.CheckIn
import dev.habita.app.shared.gym.ExerciseSwap
import dev.habita.app.shared.gym.GymSetting
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.jsonObject

/**
 * The gym's collections the server keeps without reading: unlike sessions and sets, nothing on
 * the server works them out, so they travel as plain records and the server export holds them
 * under `records`.
 */
internal fun gymRecordTables(store: GymStore): List<GymRecordTable<*>> = listOf(
    GymRecordTable(
        collection = SyncCollections.GYM_BODY,
        serializer = BodyLog.serializer(),
        envelope = { Triple(it.day, it.updatedAt, it.deleted) },
        fromEnvelope = { log, record -> log.copy(day = record.id, updatedAt = record.updatedAt, deleted = record.deleted) },
        all = { store.body.selectAll().executeAsList().map { it.toModel() } },
        dirtyRows = { store.body.selectDirty().executeAsList().map { it.toModel() } },
        byId = { id -> store.body.selectByDay(id).executeAsOneOrNull()?.let { it.toModel() to (it.dirty == 1L) } },
        save = { log, dirty -> store.saveBody(log, dirty) },
        merge = Body::merge,
        cleanRow = { id, updatedAt -> store.body.markClean(id, updatedAt) },
        dirtyAll = { store.body.markAllDirty() },
        transaction = { block -> store.transaction(block) },
    ),
    GymRecordTable(
        collection = SyncCollections.GYM_SWAPS,
        serializer = ExerciseSwap.serializer(),
        envelope = { Triple(it.exerciseId, it.updatedAt, it.deleted) },
        fromEnvelope = { swap, record -> swap.copy(exerciseId = record.id, updatedAt = record.updatedAt, deleted = record.deleted) },
        all = { store.swaps() },
        dirtyRows = { store.swaps.selectDirty().executeAsList().map { it.toModel() } },
        byId = { id -> store.swaps.selectById(id).executeAsOneOrNull()?.let { it.toModel() to (it.dirty == 1L) } },
        save = { swap, dirty -> store.saveSwap(swap, dirty) },
        cleanRow = { id, updatedAt -> store.swaps.markClean(id, updatedAt) },
        dirtyAll = { store.swaps.markAllDirty() },
        transaction = { block -> store.transaction(block) },
    ),
    GymRecordTable(
        collection = SyncCollections.GYM_CHECK_INS,
        serializer = CheckIn.serializer(),
        envelope = { Triple(it.id, it.updatedAt, it.deleted) },
        fromEnvelope = { checkIn, record -> checkIn.copy(id = record.id, updatedAt = record.updatedAt, deleted = record.deleted) },
        all = { store.checkIns() },
        dirtyRows = { store.checkIns.selectDirty().executeAsList().map { it.toModel() } },
        byId = { id -> store.checkIns.selectById(id).executeAsOneOrNull()?.let { it.toModel() to (it.dirty == 1L) } },
        save = { checkIn, dirty -> store.saveCheckIn(checkIn, dirty) },
        cleanRow = { id, updatedAt -> store.checkIns.markClean(id, updatedAt) },
        dirtyAll = { store.checkIns.markAllDirty() },
        transaction = { block -> store.transaction(block) },
    ),
    GymRecordTable(
        collection = SyncCollections.GYM_SETTINGS,
        serializer = GymSetting.serializer(),
        envelope = { Triple(it.key, it.updatedAt, false) },
        fromEnvelope = { setting, record -> setting.copy(key = record.id, updatedAt = record.updatedAt) },
        all = { store.settings() },
        dirtyRows = { store.settings.selectDirty().executeAsList().map { it.toModel() } },
        byId = { id -> store.settings.selectByKey(id).executeAsOneOrNull()?.let { it.toModel() to (it.dirty == 1L) } },
        save = { setting, dirty -> store.saveSetting(setting, dirty) },
        cleanRow = { id, updatedAt -> store.settings.markClean(id, updatedAt) },
        dirtyAll = { store.settings.markAllDirty() },
        transaction = { block -> store.transaction(block) },
    ),
)

/** One plain gym collection of [T], read and written whole like the gym's typed records. */
internal class GymRecordTable<T>(
    override val collection: String,
    private val serializer: KSerializer<T>,
    private val envelope: (T) -> Triple<String, Long, Boolean>,
    private val fromEnvelope: (T, SyncRecord) -> T,
    private val all: () -> List<T>,
    private val dirtyRows: () -> List<T>,
    private val byId: (String) -> Pair<T, Boolean>?,
    private val save: (T, Boolean) -> Unit,
    private val cleanRow: (String, Long) -> Unit,
    private val dirtyAll: () -> Unit,
    private val transaction: (() -> Unit) -> Unit,
    /** Merges a local and a pulled copy field by field; `null` keeps whole-record rules. */
    private val merge: ((T, T) -> T)? = null,
) : LocalSyncTable {
    override val mergesItself: Boolean get() = merge != null

    /** Every record, deleted ones included, for the backup file. */
    fun everything(): List<SyncRecord> = all().map(::record)

    override fun dirty(): List<SyncRecord> = dirtyRows().map(::record)

    override fun markClean(pushed: List<SyncRecord>) = transaction { pushed.forEach { cleanRow(it.id, it.updatedAt) } }

    override fun local(id: String): LocalRecord? = byId(id)?.let { (model, dirty) -> LocalRecord(record(model), dirty) }

    override fun applyRemote(records: List<SyncRecord>) = transaction {
        records.forEach { record ->
            val decoded = runCatching { SyncJson.decodeFromJsonElement(serializer, record.data) }.getOrNull() ?: return@forEach
            val remote = fromEnvelope(decoded, record)
            val local = byId(record.id)?.first
            if (merge == null || local == null) return@forEach save(remote, false)
            // Same as the server's copy: saved as sent. Same as the local one: nothing to do.
            // Anything else is new to both, so it is saved to send.
            when (val merged = merge.invoke(local, remote)) {
                remote -> save(remote, false)
                local -> Unit
                else -> save(merged, true)
            }
        }
    }

    override fun markAllDirty() = dirtyAll()

    override fun <R> inTransaction(block: () -> R): R {
        // A failure inside propagates out of the transaction, which rolls it back.
        var result: Any? = null
        transaction { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as R
    }

    override fun canRead(record: SyncRecord): Boolean =
        runCatching { SyncJson.decodeFromJsonElement(serializer, record.data) }.isSuccess

    /** Saves restored records as unsent, so the server gets them too. */
    fun restore(records: List<SyncRecord>) = transaction {
        records.forEach { record ->
            val model = runCatching { SyncJson.decodeFromJsonElement(serializer, record.data) }.getOrNull() ?: return@forEach
            save(fromEnvelope(model, record), true)
        }
    }

    private fun record(model: T): SyncRecord {
        val (id, updatedAt, deleted) = envelope(model)
        return SyncRecord(id, updatedAt, deleted, SyncJson.encodeToJsonElement(serializer, model).jsonObject)
    }
}