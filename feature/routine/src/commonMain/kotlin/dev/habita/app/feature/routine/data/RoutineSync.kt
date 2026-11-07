package dev.habita.app.feature.routine.data

import dev.habita.app.core.sync.LocalRecord
import dev.habita.app.core.sync.LocalSyncTable
import dev.habita.app.feature.routine.db.RoutineDatabase
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.SyncJson
import dev.habita.app.shared.api.SyncRecord
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import dev.habita.app.feature.routine.db.Routine_check as CheckRow
import dev.habita.app.feature.routine.db.Routine_value as ValueRow
import dev.habita.app.feature.routine.db.Routine_group as GroupRow
import dev.habita.app.feature.routine.db.Routine_task as TaskRow

/** The routine's collections. The server stores them as they come, without reading them. */
internal fun routineSyncTables(database: RoutineDatabase): List<LocalSyncTable> =
    listOf(GroupTable(database), TaskTable(database), CheckTable(database), ValueTable(database))

@Serializable
internal data class GroupData(val name: String, val position: Long, val days: Int)

@Serializable
internal data class TaskData(val groupId: String, val title: String, val adjustable: Boolean, val position: Long)

@Serializable
internal data class CheckData(val day: String, val taskId: String, val done: Boolean)

@Serializable
internal data class ValueData(val value: String)

private fun <T> encode(serializer: KSerializer<T>, data: T, id: String, updatedAt: Long, deleted: Boolean = false) =
    SyncRecord(id, updatedAt, deleted, SyncJson.encodeToJsonElement(serializer, data).jsonObject)

private fun <T> decode(serializer: KSerializer<T>, record: SyncRecord): T? =
    runCatching { SyncJson.decodeFromJsonElement(serializer, record.data) }.getOrNull()

private fun Boolean.toLong() = if (this) 1L else 0L

private class GroupTable(private val database: RoutineDatabase) : LocalSyncTable {
    private val queries = database.routineGroupQueries

    override fun <T> inTransaction(block: () -> T): T = database.transactionWithResult { block() }
    override val collection = SyncCollections.ROUTINE_GROUPS

    override fun dirty() = queries.selectDirty().executeAsList().map { it.toRecord() }

    override fun markClean(pushed: List<SyncRecord>) = database.transaction {
        pushed.forEach { queries.markClean(it.id, it.updatedAt) }
    }

    override fun local(id: String) = queries.byId(id).executeAsOneOrNull()?.let { LocalRecord(it.toRecord(), it.dirty == 1L) }

    override fun applyRemote(records: List<SyncRecord>) = database.transaction {
        records.forEach { record ->
            val data = decode(GroupData.serializer(), record) ?: return@forEach
            queries.save(record.id, data.name, data.position, data.days.toLong(), record.deleted.toLong(), record.updatedAt, 0)
        }
    }

    override fun markAllDirty() {
        queries.markAllDirty()
    }

    private fun GroupRow.toRecord() =
        encode(GroupData.serializer(), GroupData(name, position, days.toInt()), id, updated_at, deleted == 1L)
}

private class TaskTable(private val database: RoutineDatabase) : LocalSyncTable {
    private val queries = database.routineTaskQueries

    override fun <T> inTransaction(block: () -> T): T = database.transactionWithResult { block() }
    override val collection = SyncCollections.ROUTINE_TASKS

    override fun dirty() = queries.selectDirty().executeAsList().map { it.toRecord() }

    override fun markClean(pushed: List<SyncRecord>) = database.transaction {
        pushed.forEach { queries.markClean(it.id, it.updatedAt) }
    }

    override fun local(id: String) = queries.byId(id).executeAsOneOrNull()?.let { LocalRecord(it.toRecord(), it.dirty == 1L) }

    override fun applyRemote(records: List<SyncRecord>) = database.transaction {
        records.forEach { record ->
            val data = decode(TaskData.serializer(), record) ?: return@forEach
            queries.save(
                record.id, data.groupId, data.title, data.adjustable.toLong(), data.position, record.deleted.toLong(), record.updatedAt, 0,
            )
        }
    }

    override fun markAllDirty() {
        queries.markAllDirty()
    }

    private fun TaskRow.toRecord() =
        encode(TaskData.serializer(), TaskData(group_id, title, adjustable == 1L, position), id, updated_at, deleted == 1L)
}

/** Ticks are never deleted: unticking saves done = false. */
private class CheckTable(private val database: RoutineDatabase) : LocalSyncTable {
    private val queries = database.routineCheckQueries

    override fun <T> inTransaction(block: () -> T): T = database.transactionWithResult { block() }
    override val collection = SyncCollections.ROUTINE_CHECKS

    override fun dirty() = queries.selectDirty().executeAsList().map { it.toRecord() }

    override fun markClean(pushed: List<SyncRecord>) = database.transaction {
        pushed.forEach { queries.markClean(it.id, it.updatedAt) }
    }

    override fun local(id: String) = queries.byId(id).executeAsOneOrNull()?.let { LocalRecord(it.toRecord(), it.dirty == 1L) }

    override fun applyRemote(records: List<SyncRecord>) = database.transaction {
        records.forEach { record ->
            val data = decode(CheckData.serializer(), record) ?: return@forEach
            queries.save(record.id, data.day, data.taskId, (data.done && !record.deleted).toLong(), record.updatedAt, 0)
        }
    }

    override fun markAllDirty() {
        queries.markAllDirty()
    }

    private fun CheckRow.toRecord() = encode(CheckData.serializer(), CheckData(day, task_id, done == 1L), id, updated_at)
}

/** Single values by key, such as the current round of ticks. Never deleted. */
private class ValueTable(private val database: RoutineDatabase) : LocalSyncTable {
    private val queries = database.routineValueQueries

    override fun <T> inTransaction(block: () -> T): T = database.transactionWithResult { block() }
    override val collection = SyncCollections.ROUTINE_SETTINGS

    override fun dirty() = queries.selectDirty().executeAsList().map { it.toRecord() }

    override fun markClean(pushed: List<SyncRecord>) = database.transaction {
        pushed.forEach { queries.markClean(it.id, it.updatedAt) }
    }

    override fun local(id: String) = queries.byKey(id).executeAsOneOrNull()?.let { LocalRecord(it.toRecord(), it.dirty == 1L) }

    override fun applyRemote(records: List<SyncRecord>) = database.transaction {
        records.forEach { record ->
            val data = decode(ValueData.serializer(), record) ?: return@forEach
            queries.save(record.id, data.value, record.updatedAt, 0)
        }
    }

    override fun markAllDirty() {
        queries.markAllDirty()
    }

    private fun ValueRow.toRecord() = encode(ValueData.serializer(), ValueData(value_), key, updated_at)
}