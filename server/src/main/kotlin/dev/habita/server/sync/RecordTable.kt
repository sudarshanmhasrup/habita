package dev.habita.server.sync

import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.SyncRecord
import dev.habita.app.shared.api.SyncJson
import dev.habita.server.db.HabitaDatabase
import dev.habita.server.db.Sync_record
import kotlinx.serialization.json.jsonObject

/**
 * Collections the server keeps without reading them: it stores each record as it came and hands
 * it to other devices. A new feature with no server logic only needs its name added here.
 */
fun recordTables(database: HabitaDatabase): List<RecordTable> = listOf(
    SyncCollections.GYM_BODY,
    SyncCollections.EXPENSES,
    SyncCollections.EXPENSE_CATEGORIES,
    SyncCollections.EXPENSE_SETTINGS,
    SyncCollections.ROUTINE_GROUPS,
    SyncCollections.ROUTINE_TASKS,
    SyncCollections.ROUTINE_CHECKS,
    SyncCollections.APP_PREFERENCES,
).map { RecordTable(it, database) }

class RecordTable(override val collection: String, database: HabitaDatabase) : SyncTable {
    private val queries = database.syncRecordQueries

    override fun check(record: SyncRecord): String? = null

    override fun get(id: String): SyncRecord? = queries.selectById(collection, id).executeAsOneOrNull()?.toRecord()

    override fun save(record: SyncRecord, seq: Long) {
        queries.save(collection, record.id, record.updatedAt, if (record.deleted) 1L else 0L, record.data.toString(), seq)
    }

    override fun changedSince(since: Long, limit: Long): List<Sequenced> =
        queries.changedSince(collection, since, limit).executeAsList().map { Sequenced(it.server_seq, it.toRecord()) }

    /** Every record, including deleted ones, for the export. */
    fun all(): List<SyncRecord> = queries.selectAll(collection).executeAsList().map { it.toRecord() }

    private fun Sync_record.toRecord() = SyncRecord(
        id = id,
        updatedAt = updated_at,
        deleted = deleted == 1L,
        data = SyncJson.parseToJsonElement(data_).jsonObject,
    )
}