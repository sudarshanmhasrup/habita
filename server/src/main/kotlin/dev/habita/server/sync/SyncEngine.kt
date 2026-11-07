package dev.habita.server.sync

import dev.habita.app.shared.api.RejectedRecord
import dev.habita.app.shared.api.SyncPull
import dev.habita.app.shared.api.SyncPush
import dev.habita.app.shared.api.SyncPushResult
import dev.habita.app.shared.api.SyncRecord
import dev.habita.app.shared.api.winsOver
import dev.habita.server.db.HabitaDatabase
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/**
 * Saves pushed records and hands out changes after a cursor, for every collection the same way.
 * A record replaces the stored one only when it wins ([winsOver]), and each saved record gets the
 * next server sequence number, which is what the app's cursor counts.
 */
class SyncEngine(
    private val database: HabitaDatabase,
    tables: List<SyncTable>,
    private val positionFile: File? = null,
) {
    private val tables = tables.associateBy { it.collection }
    private val counter = database.syncQueries

    /**
     * Made once, when the database is created, and made again when the database turns out to have
     * been restored from a backup: its position is behind the one last written to [positionFile],
     * which lives outside the database. Apps that see a new id send everything they have, so
     * changes made after the backup come back.
     */
    val serverId: String = database.transactionWithResult {
        counter.saveServerId(UUID.randomUUID().toString())
        val seen = positionFile?.let(::readPosition)
        if (seen != null && counter.current().executeAsOne() < seen) counter.replaceServerId(UUID.randomUUID().toString())
        counter.serverId().executeAsOne()
    }.also { writePosition() }

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
                val reason = if (table == null) UNKNOWN_COLLECTION else basicCheck(incoming) ?: table.check(incoming)
                if (reason != null) {
                    rejected += RejectedRecord(collection, incoming.id, reason, retryable = reason in RETRYABLE)
                } else {
                    val record = incoming.copy(updatedAt = minOf(incoming.updatedAt, now + MAX_CLOCK_AHEAD))
                    if (record.winsOver(table!!.get(record.id))) table.save(record, nextSeq())
                }
            }
        }
        SyncPushResult(counter.current().executeAsOne(), rejected)
    }.also { writePosition() }

    /** Writes the current position next to the database, replacing the file in one step. */
    private fun writePosition() {
        val file = positionFile ?: return
        val temporary = File(file.path + ".tmp")
        temporary.writeText(counter.current().executeAsOne().toString())
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun readPosition(file: File): Long? = if (file.exists()) file.readText().trim().toLongOrNull() else null

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
        const val UNKNOWN_COLLECTION = "Unknown collection"
        const val UNREADABLE = "Unreadable record"
        const val UNKNOWN_FIELDS = "Has fields this server doesn't know"

        /** Refusals a newer server could accept. Bad ids, sizes or numbers never will be. */
        val RETRYABLE = setOf(UNKNOWN_COLLECTION, UNREADABLE, UNKNOWN_FIELDS)

        /** The most records one push may carry; the app sends more in several pushes. */
        const val MAX_PUSH = 1_000

        /** The most changes one pull returns, and the default. */
        const val MAX_PULL = 1_000

        private val MAX_CLOCK_AHEAD = 5.minutes.inWholeMilliseconds
        private const val MAX_ID_LENGTH = 200
        private const val MAX_DATA_LENGTH = 16_000
    }
}