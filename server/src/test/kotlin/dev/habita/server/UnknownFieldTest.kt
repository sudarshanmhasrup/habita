package dev.habita.server

import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.SyncJson
import dev.habita.app.shared.api.SyncPush
import dev.habita.app.shared.api.SyncRecord
import dev.habita.app.shared.gym.SetLog
import dev.habita.server.data.openDatabase
import dev.habita.server.sync.SyncEngine
import dev.habita.server.sync.gymTables
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnknownFieldTest {
    private val set = SetLog("set1", "s1", "bench", 1, 20_000, 12, 12, completedAt = 1, updatedAt = 1)

    private fun record(extra: Map<String, JsonPrimitive> = emptyMap()): SyncRecord {
        val data = SyncJson.encodeToJsonElement(SetLog.serializer(), set).jsonObject
        return SyncRecord(set.id, set.updatedAt, data = JsonObject(data + extra))
    }

    @Test
    fun `a gym record with a field this server does not know is refused, not stripped`() {
        val (driver, database) = openDatabase(createTempFile("habita", ".db").toString())
        val engine = SyncEngine(database, gymTables(database))

        val refused = engine.push(SyncPush(mapOf(SyncCollections.GYM_SETS to listOf(record(mapOf("rpe" to JsonPrimitive(8)))))), NOW)
        val accepted = engine.push(SyncPush(mapOf(SyncCollections.GYM_SETS to listOf(record()))), NOW)

        assertEquals(listOf("set1"), refused.rejected.map { it.id })
        assertTrue(accepted.rejected.isEmpty())
        driver.close()
    }
}