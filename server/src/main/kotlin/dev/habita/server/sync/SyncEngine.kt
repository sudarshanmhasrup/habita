package dev.habita.server.sync

import dev.habita.app.shared.api.RejectedRecord
import dev.habita.app.shared.api.SyncPull
import dev.habita.app.shared.api.SyncPush
import dev.habita.app.shared.api.SyncPushResult
import dev.habita.app.shared.api.SyncRecord
import dev.habita.app.shared.api.winsOver
import dev.habita.server.db.HabitaDatabase
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/**
 * Saves pushed records and hands out changes after a cursor, for every collection the same way.
 * A record replaces the stored one only when it wins ([winsOver]), and each saved record gets the
 * next server sequence number, which is what the app's cursor counts.
 */
class SyncEngine(private val database: HabitaDatabase, tables: List<SyncTable>) {
    private val tables = tables.associateBy { it.collection }
    private val counter = database.syncQueries

    /** Made once, when the database is created. */
    val serverId: String = database.transactionWithResult {
        counter.saveServerId(UUID.randomUUID().toString())
        counter.serverId().executeAsOne()
    }

    /**
     * Saves every record that wins over the stored copy, and returns the records it refused. One
     * bad record never blocks the others. A timestamp far in the future, from a phone whose clock
     * is wrong, is pulled back to [now] plus a few minutes, so that record can still be edited
     * from other devices.
     */
    fun push(push: SyncPush, now: Long): SyncPushResult = database.transactionWithResult {
        val rejected = mutableListOf<RejectedRecord>()
        push.collections.forEach { (collection, records) ->
            val table = tables[collection]
            records.forEach { incoming ->
                val reason = if (table == null) "Unknown collection" else basicCheck(incoming) ?: table.check(incoming)
                if (reason != null) {
                    rejected += RejectedRecord(collection, incoming.id, reason)
                } else {
                    val record = incoming.copy(updatedAt = minOf(incoming.updatedAt, now + MAX_CLOCK_AHEAD))
                    if (record.winsOver(table!!.get(record.id))) table.save(record, nextSeq())
                }
            }
        }
        SyncPushResult(counter.current().executeAsOne(), rejected)
    }

    /** Up to [limit] changes after [since], across every collection, oldest first. */
    fun pull(since: Long, limit: Int): SyncPull = database.transactionWithResult {
        val changes = tables.values
            .flatMap { table -> table.changedSince(since, limit + 1L).map { table.collection to it } }
            .sortedBy { it.second.seq }
        val page = changes.take(limit)
        val hasMore = changes.size > limit
        SyncPull(
            serverId = serverId,
            cursor = if (hasMore) page.last().second.seq else counter.current().executeAsOne(),
            hasMore = hasMore,
            collections = page.groupBy({ it.first }, { it.second.record }),
        )
    }

    private fun basicCheck(record: SyncRecord): String? = when {
        record.id.isBlank() || record.id.length > MAX_ID_LENGTH -> "Invalid id"
        record.updatedAt < 0 -> "Invalid updatedAt"
        record.data.toString().length > MAX_DATA_LENGTH -> "Record too large"
        else -> null
    }

    private fun nextSeq(): Long {
        counter.increment()
        return counter.current().executeAsOne()
    }

    companion object {
        /** The most records one push may carry; the app sends more in several pushes. */
        const val MAX_PUSH = 1_000

        /** The most changes one pull returns, and the default. */
        const val MAX_PULL = 1_000

        private val MAX_CLOCK_AHEAD = 5.minutes.inWholeMilliseconds
        private const val MAX_ID_LENGTH = 200
        private const val MAX_DATA_LENGTH = 16_000
    }
}