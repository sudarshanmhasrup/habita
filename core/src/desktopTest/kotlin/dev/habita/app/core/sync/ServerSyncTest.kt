package dev.habita.app.core.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.habita.app.core.data.Settings
import dev.habita.app.core.db.CoreDatabase
import dev.habita.app.core.network.HabitaApi
import dev.habita.app.shared.api.LoginRequest
import dev.habita.app.shared.api.LoginResponse
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.SyncRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two app instances syncing through a real server. Skipped unless HABITA_E2E_URL, HABITA_E2E_EMAIL
 * and HABITA_E2E_PASSWORD point at a test server, for example one started with `./gradlew
 * :server:run`. Never point it at the live server: it writes test records.
 */
class ServerSyncTest {
    private val url = System.getenv("HABITA_E2E_URL")
    private val email = System.getenv("HABITA_E2E_EMAIL")
    private val password = System.getenv("HABITA_E2E_PASSWORD")

    private class Device(url: String) {
        val scope = CoroutineScope(Dispatchers.Default)
        val settings = Settings(CoreDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CoreDatabase.Schema.create(it) }), scope)
        val api = HabitaApi(url, settings)
        val table = MemoryTable(SyncCollections.EXPENSES)
        val engine = SyncEngine(scope, ApiSyncTransport(api), settings, MutableStateFlow(true)).apply { register(table) }
    }

    private class MemoryTable(override val collection: String) : LocalSyncTable {
        val rows = mutableMapOf<String, LocalRecord>()

        override fun dirty() = rows.values.filter { it.dirty }.map { it.record }

        override fun markClean(pushed: List<SyncRecord>) = pushed.forEach { record ->
            rows[record.id]?.takeIf { it.record.updatedAt == record.updatedAt }?.let { rows[record.id] = it.copy(dirty = false) }
        }

        override fun local(id: String) = rows[id]

        override fun applyRemote(records: List<SyncRecord>) = records.forEach { rows[it.id] = LocalRecord(it, dirty = false) }

        override fun markAllDirty() = rows.replaceAll { _, row -> row.copy(dirty = true) }
    }

    @Test
    fun `a record saved on one device reaches another through the server`() = runBlocking {
        if (url == null || email == null || password == null) return@runBlocking
        val phone = Device(url)
        val tablet = Device(url)
        listOf(phone, tablet).forEach { device ->
            device.settings.saveToken(device.api.post<LoginRequest, LoginResponse>("auth/login", LoginRequest(email, password)).token)
        }
        val id = "e2e-" + System.currentTimeMillis()
        val record = SyncRecord(id, System.currentTimeMillis(), data = buildJsonObject { put("amountPaise", 12_300) })
        phone.table.rows[id] = LocalRecord(record, dirty = true)

        assertTrue(phone.engine.sync(), phone.engine.state.value.toString())
        assertTrue(tablet.engine.sync(), tablet.engine.state.value.toString())

        assertEquals(record, tablet.table.rows[id]?.record)
        assertEquals(false, phone.table.rows.getValue(id).dirty)
    }
}