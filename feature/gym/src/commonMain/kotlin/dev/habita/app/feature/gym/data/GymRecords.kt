package dev.habita.app.feature.gym.data

import dev.habita.app.core.sync.LocalRecord
import dev.habita.app.core.sync.LocalSyncTable
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.SyncJson
import dev.habita.app.shared.api.SyncRecord
import dev.habita.app.shared.gym.BodyLog
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
        cleanRow = { id, updatedAt -> store.body.markClean(id, updatedAt) },
        dirtyAll = { store.body.markAllDirty() },
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
) : LocalSyncTable {
    /** Every record, deleted ones included, for the backup file. */
    fun everything(): List<SyncRecord> = all().map(::record)

    override fun dirty(): List<SyncRecord> = dirtyRows().map(::record)

    override fun markClean(pushed: List<SyncRecord>) = transaction { pushed.forEach { cleanRow(it.id, it.updatedAt) } }

    override fun local(id: String): LocalRecord? = byId(id)?.let { (model, dirty) -> LocalRecord(record(model), dirty) }

    override fun applyRemote(records: List<SyncRecord>) = transaction {
        records.forEach { record ->
            val model = runCatching { SyncJson.decodeFromJsonElement(serializer, record.data) }.getOrNull() ?: return@forEach
            save(fromEnvelope(model, record), false)
        }
    }

    override fun markAllDirty() = dirtyAll()

    private fun record(model: T): SyncRecord {
        val (id, updatedAt, deleted) = envelope(model)
        return SyncRecord(id, updatedAt, deleted, SyncJson.encodeToJsonElement(serializer, model).jsonObject)
    }
}