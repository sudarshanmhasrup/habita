package dev.habita.app.core.sync

import dev.habita.app.shared.api.SyncRecord

/** A local record and whether it has changes the server doesn't have yet. */
data class LocalRecord(val record: SyncRecord, val dirty: Boolean)

/**
 * One synced collection in the app. [SyncEngine] decides what to send and which server copies to
 * keep; a table only reads and writes its own database. Every local edit must set the record's
 * `updatedAt` to now and mark it dirty, then call [SyncEngine.request].
 */
interface LocalSyncTable {
    val collection: String

    /** Records changed on this device that the server doesn't have yet. */
    fun dirty(): List<SyncRecord>

    /** Clears the dirty mark of [pushed] records, unless they changed again since. */
    fun markClean(pushed: List<SyncRecord>)

    fun local(id: String): LocalRecord?

    /** Saves records from the server, as clean. A record this version can't read is skipped. */
    fun applyRemote(records: List<SyncRecord>)

    /** Marks every record dirty, so all of it goes to the server again. */
    fun markAllDirty()

    /**
     * Runs [block] in one database transaction. The engine checks whether a local copy has unsent
     * changes and then saves the server's copy inside it, so an edit can't slip in between.
     */
    fun <T> inTransaction(block: () -> T): T = block()

    /**
     * Whether this version can read [record]. One it can't, such as a record from a newer app, is
     * kept aside and offered again on later syncs, so an app update picks it up.
     */
    fun canRead(record: SyncRecord): Boolean = true

    /**
     * Whether this table merges a pulled record with its local copy itself, field by field. The
     * engine then hands it every pulled record, unsent local edits or not, inside [inTransaction].
     */
    val mergesItself: Boolean get() = false
}