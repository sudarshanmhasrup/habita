package dev.habita.app.feature.gym.data

import dev.habita.app.core.sync.LocalSyncTable
import dev.habita.app.shared.api.Export
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.SyncRecord
import dev.habita.app.shared.api.toCollections
import dev.habita.app.shared.api.winsOver
import kotlinx.serialization.json.Json

/** What a restore brought back: how many records were newer than the phone's, per kind. */
data class RestoreResult(val workouts: Int, val sets: Int, val bodyDays: Int, val other: Int) {
    val total: Int get() = workouts + sets + bodyDays + other
}

/**
 * Brings back a backup file, the phone's own or the server's export. It merges with the same rule
 * as syncing: a record from the file is kept only when it is newer than the phone's copy, so a
 * restore never undoes newer work. Restored records are marked to send, so the server gets them.
 */
internal object GymRestore {
    private val json = Json { ignoreUnknownKeys = true }

    /** The file's gym records by collection, or `null` when it isn't a Habita backup. */
    fun read(text: String): Map<String, List<SyncRecord>>? {
        val export = runCatching { json.decodeFromString<Export>(text) }.getOrNull() ?: return null
        return export.changes.toCollections() + export.records.filterKeys { it.startsWith("gym.") }
    }

    fun merge(tables: List<LocalSyncTable>, collections: Map<String, List<SyncRecord>>, transaction: (() -> Unit) -> Unit): RestoreResult {
        val counts = mutableMapOf<String, Int>()
        transaction {
            tables.forEach { table ->
                val newer = collections[table.collection].orEmpty().filter { it.winsOver(table.local(it.id)?.record) }
                if (newer.isEmpty()) return@forEach
                // Saved as unsent, so the server gets them in case it lacks them.
                when (table) {
                    is GymSyncTable -> table.restore(newer)
                    is GymRecordTable<*> -> table.restore(newer)
                    else -> table.applyRemote(newer)
                }
                counts[table.collection] = newer.size
            }
        }
        return RestoreResult(
            workouts = counts[SyncCollections.GYM_SESSIONS] ?: 0,
            sets = counts[SyncCollections.GYM_SETS] ?: 0,
            bodyDays = counts[SyncCollections.GYM_BODY] ?: 0,
            other = counts.filterKeys { it !in setOf(SyncCollections.GYM_SESSIONS, SyncCollections.GYM_SETS, SyncCollections.GYM_BODY) }.values.sum(),
        )
    }
}