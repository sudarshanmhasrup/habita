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
}