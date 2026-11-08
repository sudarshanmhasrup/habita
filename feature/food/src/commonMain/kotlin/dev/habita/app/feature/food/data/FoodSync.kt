package dev.habita.app.feature.food.data

import dev.habita.app.core.sync.LocalRecord
import dev.habita.app.core.sync.LocalSyncTable
import dev.habita.app.feature.food.db.FoodDatabase
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.SyncJson
import dev.habita.app.shared.api.SyncRecord
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import dev.habita.app.feature.food.db.Food_event as EventRow
import dev.habita.app.feature.food.db.Food_value as ValueRow

/** The food collections. The server stores them as they come, without reading them. */
internal fun foodSyncTables(database: FoodDatabase): List<LocalSyncTable> = listOf(EventTable(database), ValueTable(database))

@Serializable
internal data class EventData(
    val itemId: String,
    val kind: String,
    val quantity: Double,
    val day: Int,
    val at: Long,
    val pricePaise: Long? = null,
    val shop: String? = null,
    val note: String = "",
)

@Serializable
internal data class ValueData(val value: String)

private fun <T> encode(serializer: KSerializer<T>, data: T, id: String, updatedAt: Long, deleted: Boolean = false) =
    SyncRecord(id, updatedAt, deleted, SyncJson.encodeToJsonElement(serializer, data).jsonObject)

private fun <T> decode(serializer: KSerializer<T>, record: SyncRecord): T? =
    runCatching { SyncJson.decodeFromJsonElement(serializer, record.data) }.getOrNull()

/** Like the sync format, but refuses fields it doesn't know instead of dropping them. */
private val StrictJson = Json { ignoreUnknownKeys = false }

/**
 * Strictly: a record with fields this version doesn't know, such as one from a newer app, waits
 * for an update whole. Saving it without them would lose them on the next edit.
 */
private fun <T> readable(serializer: KSerializer<T>, record: SyncRecord): Boolean =
    runCatching { StrictJson.decodeFromJsonElement(serializer, record.data) }.isSuccess

private class EventTable(private val database: FoodDatabase) : LocalSyncTable {
    private val queries = database.foodEventQueries

    override fun <T> inTransaction(block: () -> T): T = database.transactionWithResult { block() }
    override val collection = SyncCollections.FOOD_EVENTS
    override fun canRead(record: SyncRecord) = readable(EventData.serializer(), record)

    override fun dirty() = queries.selectDirty().executeAsList().map { it.toRecord() }

    override fun markClean(pushed: List<SyncRecord>) = database.transaction {
        pushed.forEach { queries.markClean(it.id, it.updatedAt) }
    }

    override fun local(id: String) = queries.byId(id).executeAsOneOrNull()?.let { LocalRecord(it.toRecord(), it.dirty == 1L) }

    override fun applyRemote(records: List<SyncRecord>) = database.transaction {
        records.forEach { record ->
            val data = decode(EventData.serializer(), record) ?: return@forEach
            queries.save(
                record.id, data.itemId, data.kind, data.quantity, data.day.toLong(), data.at, data.pricePaise, data.shop, data.note,
                if (record.deleted) 1L else 0L, record.updatedAt, 0,
            )
        }
    }

    override fun markAllDirty() {
        queries.markAllDirty()
    }

    private fun EventRow.toRecord() = encode(
        EventData.serializer(),
        EventData(item_id, kind, quantity, day.toInt(), at, price_paise, shop, note),
        id,
        updated_at,
        deleted == 1L,
    )
}

/** Single values by key, such as the plan. Never deleted. */
private class ValueTable(private val database: FoodDatabase) : LocalSyncTable {
    private val queries = database.foodValueQueries

    override fun <T> inTransaction(block: () -> T): T = database.transactionWithResult { block() }
    override val collection = SyncCollections.FOOD_SETTINGS
    override fun canRead(record: SyncRecord) = readable(ValueData.serializer(), record)

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