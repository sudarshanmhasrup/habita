package dev.habita.app.core.sync

import dev.habita.app.core.network.ApiException
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

    override suspend fun push(push: SyncPush): SyncPushResult {
        if (!online) throw ApiException.Offline(Exception("offline"))
        push.collections[NOTES].orEmpty().forEach { record ->
            if (record.winsOver(records[record.id]?.second)) records[record.id] = ++seq to record
        }
        return SyncPushResult(seq)
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
}