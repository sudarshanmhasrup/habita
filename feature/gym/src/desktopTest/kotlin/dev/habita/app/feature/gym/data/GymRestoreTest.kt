package dev.habita.app.feature.gym.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.habita.app.feature.gym.db.GymDatabase
import dev.habita.app.shared.api.Export
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.gym.BodyLog
import dev.habita.app.shared.gym.SetLog
import dev.habita.app.shared.gym.WorkoutSession
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GymRestoreTest {
    private fun newStore() = GymStore(GymDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { GymDatabase.Schema.create(it) }))

    private fun tables(store: GymStore) = gymTypedTables(store) + gymRecordTables(store)

    private fun backupOf(store: GymStore): String = Json.encodeToString(
        Export(
            exportedAt = 0,
            programs = emptyList(),
            changes = store.everything(),
            records = gymRecordTables(store).associate { it.collection to it.everything() },
        ),
    )

    private fun restore(store: GymStore, text: String) =
        GymRestore.merge(tables(store), GymRestore.read(text)!!) { block -> store.transaction(block) }

    private val session = WorkoutSession("s1", "upper-a", programVersion = 0, startedAt = 1, endedAt = 2, updatedAt = 2)
    private val set = SetLog("set1", "s1", "bench", 1, 20_000, 12, 12, completedAt = 1, updatedAt = 1)

    @Test
    fun `a backup comes back on an empty phone and is marked to send`() {
        val old = newStore()
        old.saveSession(session)
        old.saveSet(set)
        old.saveBody(BodyLog("2026-11-07", weightGrams = 78_000, updatedAt = 3))
        val phone = newStore()

        val result = restore(phone, backupOf(old))

        assertEquals(RestoreResult(workouts = 1, sets = 1, bodyDays = 1, other = 0), result)
        assertEquals(listOf(session), phone.sessions())
        assertEquals(78_000, phone.bodyLog("2026-11-07")?.weightGrams)
        assertEquals(1, tables(phone).single { it.collection == SyncCollections.GYM_SETS }.dirty().size)
    }

    @Test
    fun `newer records on the phone are kept`() {
        val old = newStore()
        old.saveBody(BodyLog("2026-11-07", weightGrams = 78_000, updatedAt = 3))
        val phone = newStore()
        phone.saveBody(BodyLog("2026-11-07", weightGrams = 77_500, updatedAt = 9))

        val result = restore(phone, backupOf(old))

        assertEquals(0, result.total)
        assertEquals(77_500, phone.bodyLog("2026-11-07")?.weightGrams)
    }

    @Test
    fun `restoring the same file twice changes nothing the second time`() {
        val old = newStore()
        old.saveSession(session)
        val phone = newStore()
        val file = backupOf(old)

        restore(phone, file)

        assertEquals(0, restore(phone, file).total)
    }

    @Test
    fun `a file that is not a backup is refused`() {
        assertNull(GymRestore.read("not json"))
        assertNull(GymRestore.read("{\"hello\": 1}"))
        assertTrue(GymRestore.read(backupOf(newStore()))!!.values.all { it.isEmpty() })
    }
}