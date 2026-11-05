package dev.habita.server.sync

import dev.habita.app.shared.api.SyncRecord

/** A record with the server sequence number it was saved under. */
data class Sequenced(val seq: Long, val record: SyncRecord)

/**
 * One synced collection on the server. [SyncEngine] handles last-write-wins, sequence numbers and
 * transactions; a table only stores and reads its records. Adding a feature means adding a table
 * here, or listing its collection as a [RecordTable] when the server never reads it.
 */
interface SyncTable {
    val collection: String

    /** Why [record] can't be stored, or `null` when it can. Must catch anything [save] would fail on. */
    fun check(record: SyncRecord): String?

    fun get(id: String): SyncRecord?

    fun save(record: SyncRecord, seq: Long)

    /** Up to [limit] records saved after sequence number [since], oldest first. */
    fun changedSince(since: Long, limit: Long): List<Sequenced>
}