package dev.habita.server

import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.SyncPush
import dev.habita.app.shared.api.SyncRecord
import dev.habita.server.data.openDatabase
import dev.habita.server.sync.SyncEngine
import dev.habita.server.sync.recordTables
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RestoreTest {
    private fun note(id: String) = SyncRecord(id, NOW, data = buildJsonObject { put("amountPaise", 100) })

    private fun push(engine: SyncEngine, vararg ids: String) =
        engine.push(SyncPush(mapOf(SyncCollections.EXPENSES to ids.map(::note))), NOW)

    /** Opens [db] with its position file, runs [block], and closes it again. */
    private fun <T> withEngine(db: File, block: (SyncEngine) -> T): T {
        val (driver, database) = openDatabase(db.path)
        return try {
            block(SyncEngine(database, recordTables(database), File(db.path + ".position")))
        } finally {
            driver.close()
        }
    }

    @Test
    fun `a restart keeps the id, a restored older backup gets a new one`() {
        val directory = createTempDirectory("habita").toFile()
        val db = File(directory, "habita.db")
        val backup = File(directory, "backup.db")

        val firstId = withEngine(db) { engine -> push(engine, "e1"); engine.serverId }
        db.copyTo(backup)
        withEngine(db) { engine -> push(engine, "e2", "e3") }
        val restartId = withEngine(db) { it.serverId }

        // The disk is lost and last night's backup is put back; the position file stays.
        backup.copyTo(db, overwrite = true)
        val restoredId = withEngine(db) { it.serverId }

        assertEquals(firstId, restartId)
        assertNotEquals(firstId, restoredId)
        // Once it has a new id it stays, so the apps resend only once.
        assertEquals(restoredId, withEngine(db) { it.serverId })
    }

    @Test
    fun `only refusals a newer server could accept are worth retrying`() {
        val directory = createTempDirectory("habita").toFile()
        withEngine(File(directory, "habita.db")) { engine ->
            val result = engine.push(
                SyncPush(
                    mapOf(
                        "future.collection" to listOf(note("x1")),
                        SyncCollections.EXPENSES to listOf(note(" ").copy(id = "")),
                    ),
                ),
                NOW,
            )
            val byId = result.rejected.associateBy { it.id }

            assertTrue(byId.getValue("x1").retryable)
            assertFalse(byId.getValue("").retryable)
        }
    }
}