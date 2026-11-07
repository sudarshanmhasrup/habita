package dev.habita.app.core.sync

import dev.habita.app.core.network.ApiException
import dev.habita.app.shared.api.RejectedRecord
import dev.habita.app.shared.api.SyncPull
import dev.habita.app.shared.api.SyncPush
import dev.habita.app.shared.api.SyncPushResult
import dev.habita.app.shared.api.SyncRecord
import dev.habita.app.shared.api.winsOver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val NOTES = "test.notes"

private fun note(id: String, text: String, at: Long, deleted: Boolean = false) =
    SyncRecord(id, at, deleted, buildJsonObject { put("text", text) })

/** The server's rules, in memory: last write wins by [winsOver], every save gets the next number. */
private class FakeServer(var serverId: String = "server-1") : SyncTransport {
    val records = mutableMapOf<String, Pair<Long, SyncRecord>>()
    var seq = 0L
    var online = true

    /** Record ids this server refuses, like a server too old to understand them. */
    var refuse = emptySet<String>()

    override suspend fun push(push: SyncPush): SyncPushResult {
        if (!online) throw ApiException.Offline(Exception("offline"))
        val rejected = mutableListOf<RejectedRecord>()
        push.collections[NOTES].orEmpty().forEach { record ->
            if (record.id in refuse) {
                rejected += RejectedRecord(NOTES, record.id, "Unreadable record")
            } else if (record.winsOver(records[record.id]?.second)) {
                records[record.id] = ++seq to record
            }
        }
        return SyncPushResult(seq, rejected)
    }

    /** Goes back to how it was at [atSeq], as restoring an older backup does. The id stays. */
    fun restore(atSeq: Long) {
        records.values.removeAll { it.first > atSeq }
        seq = atSeq
    }

    override suspend fun pull(since: Long, limit: Int): SyncPull {
        if (!online) throw ApiException.Offline(Exception("offline"))
        val changed = records.values.filter { it.first > since }.sortedBy { it.first }
        val page = changed.take(limit)
        val hasMore = changed.size > limit
        return SyncPull(
            serverId = serverId,
            cursor = if (hasMore) page.last().first else seq,
            hasMore = hasMore,
            collections = if (page.isEmpty()) emptyMap() else mapOf(NOTES to page.map { it.second }),
        )
    }

    fun wipe(newId: String) {
        records.clear()
        seq = 0
        serverId = newId
    }
}

/** One phone: a notes table in memory and its own sync engine. */
private class Phone(server: SyncTransport) : LocalSyncTable, SyncCheckpoint {
    val rows = mutableMapOf<String, LocalRecord>()
    private var cursor = 0L
    private var serverId: String? = null
    private var kept = emptyList<PendingRecord>()
    private var highest = 0L

    /** Which records this app version can read; all of them unless a test says otherwise. */
    var readable: (SyncRecord) -> Boolean = { true }
    val engine = SyncEngine(CoroutineScope(Dispatchers.Unconfined), server, this, MutableStateFlow(true))

    init {
        engine.register(this)
    }

    fun edit(record: SyncRecord) {
        rows[record.id] = LocalRecord(record, dirty = true)
    }

    fun text(id: String) = rows[id]?.record?.data?.get("text").toString()

    override val collection get() = NOTES

    override fun dirty() = rows.values.filter { it.dirty }.map { it.record }

    override fun markClean(pushed: List<SyncRecord>) = pushed.forEach { record ->
        val row = rows[record.id] ?: return@forEach
        if (row.record.updatedAt == record.updatedAt) rows[record.id] = row.copy(dirty = false)
    }

    override fun local(id: String) = rows[id]

    override fun applyRemote(records: List<SyncRecord>) = records.forEach { rows[it.id] = LocalRecord(it, dirty = false) }

    override fun markAllDirty() = rows.replaceAll { _, row -> row.copy(dirty = true) }

    override fun cursor() = cursor

    override fun serverId() = serverId

    override fun saveCheckpoint(cursor: Long, serverId: String) {
        this.cursor = cursor
        this.serverId = serverId
    }

    override fun canRead(record: SyncRecord) = readable(record)

    override fun pending() = kept

    override fun highestSeen() = highest

    override fun saveHighestSeen(position: Long) {
        highest = position
    }

    override fun savePending(records: List<PendingRecord>) {
        kept = records
    }
}

class SyncEngineTest {
    @Test
    fun `edits made offline go up once the server is back`() = runBlocking {
        val server = FakeServer().apply { online = false }
        val phone = Phone(server)
        phone.edit(note("n1", "Buy chalk", at = 1))

        assertFalse(phone.engine.sync())
        assertIs<SyncState.Failed>(phone.engine.state.value)
        assertTrue(phone.rows.getValue("n1").dirty)

        server.online = true
        assertTrue(phone.engine.sync())
        assertEquals(note("n1", "Buy chalk", at = 1), server.records.getValue("n1").second)
        assertFalse(phone.rows.getValue("n1").dirty)
    }

    @Test
    fun `two phones editing the same record offline end up the same`() = runBlocking {
        val server = FakeServer()
        val a = Phone(server)
        val b = Phone(server)
        a.edit(note("n1", "first", at = 1))
        a.engine.sync()
        b.engine.sync()

        // Both edit offline; b's edit is later, so it should win everywhere.
        a.edit(note("n1", "from a", at = 5))
        b.edit(note("n1", "from b", at = 9))
        a.engine.sync()
        b.engine.sync()
        a.engine.sync()

        assertEquals("\"from b\"", a.text("n1"))
        assertEquals("\"from b\"", b.text("n1"))
        assertFalse(a.rows.getValue("n1").dirty)
        assertFalse(b.rows.getValue("n1").dirty)
    }

    @Test
    fun `an unsent local edit that loses is replaced by the server copy`() = runBlocking {
        val server = FakeServer()
        val a = Phone(server)
        val b = Phone(server)
        b.edit(note("n1", "newer on b", at = 9))
        b.engine.sync()

        a.edit(note("n1", "older on a", at = 3))
        a.engine.sync()

        assertEquals("\"newer on b\"", a.text("n1"))
        assertEquals(note("n1", "newer on b", at = 9), server.records.getValue("n1").second)
    }

    @Test
    fun `deletions reach other phones`() = runBlocking {
        val server = FakeServer()
        val a = Phone(server)
        val b = Phone(server)
        a.edit(note("n1", "x", at = 1))
        a.engine.sync()
        b.engine.sync()

        a.edit(note("n1", "x", at = 2, deleted = true))
        a.engine.sync()
        b.engine.sync()

        assertTrue(b.rows.getValue("n1").record.deleted)
    }

    @Test
    fun `a long history is pulled page by page`() = runBlocking {
        val server = FakeServer()
        val a = Phone(server)
        repeat(1_234) { a.edit(note("n$it", "note $it", at = 1)) }
        a.engine.sync()

        val b = Phone(server)
        b.engine.sync()

        assertEquals(1_234, b.rows.size)
    }

    @Test
    fun `a replaced server gets everything again`() = runBlocking {
        val server = FakeServer()
        val phone = Phone(server)
        phone.edit(note("n1", "keep me", at = 1))
        phone.engine.sync()

        // Restored from an empty backup: new id, no records.
        server.wipe("server-2")
        phone.engine.sync()

        assertEquals(note("n1", "keep me", at = 1), server.records.getValue("n1").second)
        assertFalse(phone.rows.getValue("n1").dirty)
    }

    @Test
    fun `a server restored from an older backup gets this phone's later changes back`() = runBlocking {
        val server = FakeServer()
        val phone = Phone(server)
        phone.edit(note("n1", "before the backup", at = 1))
        phone.engine.sync()
        val backupSeq = server.seq
        phone.edit(note("n2", "after the backup", at = 2))
        phone.engine.sync()

        // The disk dies; last night's backup comes back with the same server id.
        server.restore(backupSeq)
        phone.engine.sync()

        assertEquals(note("n2", "after the backup", at = 2), server.records.getValue("n2").second)
    }

    @Test
    fun `refused changes stay unsent and go up once the server accepts them`() = runBlocking {
        val server = FakeServer().apply { refuse = setOf("n1") }
        val phone = Phone(server)
        phone.edit(note("n1", "too new for this server", at = 1))

        phone.engine.sync()

        assertTrue(phone.rows.getValue("n1").dirty)
        assertEquals(1, phone.engine.refused.value)

        server.refuse = emptySet()
        phone.engine.sync()

        assertFalse(phone.rows.getValue("n1").dirty)
        assertEquals(0, phone.engine.refused.value)
        assertTrue("n1" in server.records)
    }

    @Test
    fun `a record this version can't read is kept and arrives after an update`() = runBlocking {
        val server = FakeServer()
        val newer = Phone(server)
        newer.edit(note("n1", "from a newer app", at = 1))
        newer.engine.sync()
        val older = Phone(server).apply { readable = { false } }

        older.engine.sync()
        assertFalse("n1" in older.rows)

        // The app is updated and can read it now; the server has nothing new to send.
        older.readable = { true }
        older.engine.sync()

        assertEquals("\"from a newer app\"", older.text("n1"))
    }

    @Test
    fun `pulling a long history back page by page is not mistaken for a restore`() = runBlocking {
        val server = FakeServer()
        val phone = Phone(server)
        repeat(1_234) { phone.edit(note("n$it", "note $it", at = 1)) }
        phone.engine.sync()
        val pushed = server.seq

        phone.engine.sync()

        assertEquals(pushed, server.seq)
        assertTrue(phone.dirty().isEmpty())
    }

    @Test
    fun `a kept-aside copy never replaces a newer one`() = runBlocking {
        val server = FakeServer()
        val newer = Phone(server)
        newer.edit(note("n1", "v1 unreadable", at = 1))
        newer.engine.sync()
        val older = Phone(server).apply { readable = { "unreadable" !in it.data.toString() } }
        older.engine.sync()

        newer.edit(note("n1", "v2 readable", at = 2))
        newer.engine.sync()
        older.engine.sync()
        older.readable = { true }
        older.engine.sync()

        assertEquals("\"v2 readable\"", older.text("n1"))
    }

    @Test
    fun `a refusal no server would accept is not sent again`() = runBlocking {
        val server = object : SyncTransport by FakeServer() {
            override suspend fun push(push: SyncPush) =
                SyncPushResult(0, listOf(RejectedRecord(NOTES, "n1", "Invalid id", retryable = false)))
        }
        val phone = Phone(server)
        phone.edit(note("n1", "bad", at = 1))

        phone.engine.sync()

        assertFalse(phone.rows.getValue("n1").dirty)
        assertEquals(0, phone.engine.refused.value)
    }
}