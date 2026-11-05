package dev.habita.app.core.data

import dev.habita.app.core.db.CoreDatabase
import dev.habita.app.core.db.Preference
import dev.habita.app.core.sync.LocalRecord
import dev.habita.app.core.sync.LocalSyncTable
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.SyncJson
import dev.habita.app.shared.api.SyncRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject

/** One preference as it travels. The record id is its key, such as "accent". */
@Serializable
internal data class PreferenceData(val value: String)

/** Synced preferences, such as the theme, so every device looks the same. */
internal class PreferenceSyncTable(private val database: CoreDatabase) : LocalSyncTable {
    private val queries = database.preferenceQueries
    override val collection = SyncCollections.APP_PREFERENCES

    override fun dirty() = queries.selectDirty().executeAsList().map { it.toRecord() }

    override fun markClean(pushed: List<SyncRecord>) = database.transaction {
        pushed.forEach { queries.markClean(it.id, it.updatedAt) }
    }

    override fun local(id: String) = queries.byKey(id).executeAsOneOrNull()?.let { LocalRecord(it.toRecord(), it.dirty == 1L) }

    override fun applyRemote(records: List<SyncRecord>) = database.transaction {
        records.forEach { record ->
            val data = runCatching { SyncJson.decodeFromJsonElement(PreferenceData.serializer(), record.data) }.getOrNull()
                ?: return@forEach
            queries.save(record.id, data.value, record.updatedAt, 0)
        }
    }

    override fun markAllDirty() {
        queries.markAllDirty()
    }

    private fun Preference.toRecord() =
        SyncRecord(key, updated_at, data = SyncJson.encodeToJsonElement(PreferenceData.serializer(), PreferenceData(value_)).jsonObject)
}