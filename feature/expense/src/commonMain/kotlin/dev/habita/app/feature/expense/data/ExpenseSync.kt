package dev.habita.app.feature.expense.data

import dev.habita.app.core.sync.LocalRecord
import dev.habita.app.core.sync.LocalSyncTable
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.SyncJson
import dev.habita.app.shared.api.SyncRecord
import dev.habita.app.feature.expense.db.ExpenseDatabase
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import dev.habita.app.feature.expense.db.Category as CategoryRow
import dev.habita.app.feature.expense.db.Expense as ExpenseRow
import dev.habita.app.feature.expense.db.Expense_value as ValueRow

/** The expense tracker's collections. The server stores them as they come, without reading them. */
internal fun expenseSyncTables(database: ExpenseDatabase): List<LocalSyncTable> =
    listOf(ExpenseTable(database), CategoryTable(database), SettingTable(database))

/** A payment as it travels: everything but the envelope's id, time and deleted flag. */
@Serializable
internal data class ExpenseData(
    val amountPaise: Long,
    val description: String,
    val payee: String? = null,
    val categoryId: String? = null,
    val mode: String,
    val source: String,
    val smsRef: String? = null,
    val paidAt: Long,
)

@Serializable
internal data class CategoryData(
    val name: String,
    val limitPaise: Long,
    val position: Long,
    val archived: Boolean = false,
)

/** One value, such as the income. The record id is its key. */
@Serializable
internal data class SettingData(val value: String)

private fun <T> encode(serializer: KSerializer<T>, data: T, id: String, updatedAt: Long, deleted: Boolean = false) =
    SyncRecord(id, updatedAt, deleted, SyncJson.encodeToJsonElement(serializer, data).jsonObject)

private fun <T> decode(serializer: KSerializer<T>, record: SyncRecord): T? =
    runCatching { SyncJson.decodeFromJsonElement(serializer, record.data) }.getOrNull()

private class ExpenseTable(private val database: ExpenseDatabase) : LocalSyncTable {
    private val queries = database.expenseQueries
    override val collection = SyncCollections.EXPENSES

    override fun dirty() = queries.selectDirty().executeAsList().map { it.toRecord() }

    override fun markClean(pushed: List<SyncRecord>) = database.transaction {
        pushed.forEach { queries.markClean(it.id, it.updatedAt) }
    }

    override fun local(id: String) = queries.byId(id).executeAsOneOrNull()?.let { LocalRecord(it.toRecord(), it.dirty == 1L) }

    override fun applyRemote(records: List<SyncRecord>) = database.transaction {
        records.forEach { record ->
            val data = decode(ExpenseData.serializer(), record) ?: return@forEach
            queries.saveSynced(
                record.id, data.amountPaise, data.description, data.payee, data.categoryId, data.mode, data.source,
                data.smsRef, data.paidAt, record.updatedAt, if (record.deleted) 1L else 0L,
            )
        }
    }

    override fun markAllDirty() {
        queries.markAllDirty()
    }

    private fun ExpenseRow.toRecord() = encode(
        ExpenseData.serializer(),
        ExpenseData(amount_paise, description, payee, category_id, mode, source, sms_ref, paid_at),
        id,
        updated_at,
        deleted == 1L,
    )
}

/** Categories are archived, never deleted, so the deleted flag is never set. */
private class CategoryTable(private val database: ExpenseDatabase) : LocalSyncTable {
    private val queries = database.categoryQueries
    override val collection = SyncCollections.EXPENSE_CATEGORIES

    override fun dirty() = queries.selectDirty().executeAsList().map { it.toRecord() }

    override fun markClean(pushed: List<SyncRecord>) = database.transaction {
        pushed.forEach { queries.markClean(it.id, it.updatedAt) }
    }

    override fun local(id: String) = queries.byId(id).executeAsOneOrNull()?.let { LocalRecord(it.toRecord(), it.dirty == 1L) }

    override fun applyRemote(records: List<SyncRecord>) = database.transaction {
        records.forEach { record ->
            val data = decode(CategoryData.serializer(), record) ?: return@forEach
            queries.save(record.id, data.name, data.limitPaise, data.position, if (data.archived) 1L else 0L, record.updatedAt, 0)
        }
    }

    override fun markAllDirty() {
        queries.markAllDirty()
    }

    private fun CategoryRow.toRecord() =
        encode(CategoryData.serializer(), CategoryData(name, limit_paise, position, archived == 1L), id, updated_at)
}

private class SettingTable(private val database: ExpenseDatabase) : LocalSyncTable {
    private val queries = database.expenseValueQueries
    override val collection = SyncCollections.EXPENSE_SETTINGS

    override fun dirty() = queries.selectDirty().executeAsList().map { it.toRecord() }

    override fun markClean(pushed: List<SyncRecord>) = database.transaction {
        pushed.forEach { queries.markClean(it.id, it.updatedAt) }
    }

    override fun local(id: String) = queries.byKey(id).executeAsOneOrNull()?.let { LocalRecord(it.toRecord(), it.dirty == 1L) }

    override fun applyRemote(records: List<SyncRecord>) = database.transaction {
        records.forEach { record ->
            val data = decode(SettingData.serializer(), record) ?: return@forEach
            queries.save(record.id, data.value, record.updatedAt, 0)
        }
    }

    override fun markAllDirty() {
        queries.markAllDirty()
    }

    private fun ValueRow.toRecord() = encode(SettingData.serializer(), SettingData(value_), key, updated_at)
}