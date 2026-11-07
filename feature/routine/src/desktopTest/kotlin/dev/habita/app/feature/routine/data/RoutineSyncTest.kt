package dev.habita.app.feature.routine.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.habita.app.feature.routine.db.RoutineDatabase
import dev.habita.app.shared.api.SyncCollections
import kotlin.test.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutineSyncTest {
    private fun database() = RoutineDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { RoutineDatabase.Schema.create(it) })

    private fun RoutineDatabase.table(collection: String) = routineSyncTables(this).single { it.collection == collection }

    @Test
    fun `a task from another device is saved as synced and reads back the same`() {
        val sender = database()
        sender.routineTaskQueries.save("t1", "office", "Clean shoes", 0, 2, 0, 1_000, 1)
        val record = sender.table(SyncCollections.ROUTINE_TASKS).dirty().single()
        val receiver = database()
        val table = receiver.table(SyncCollections.ROUTINE_TASKS)

        table.applyRemote(listOf(record))

        assertEquals(record, table.local("t1")!!.record)
        assertEquals(false, table.local("t1")!!.dirty)
        assertEquals(emptyList(), table.dirty())
    }

    @Test
    fun `a deleted group arrives deleted and drops out of the list`() {
        val sender = database()
        sender.routineGroupQueries.save("office", "Office", 1, Days.WEEKDAYS.toLong(), 1, 2_000, 1)
        val record = sender.table(SyncCollections.ROUTINE_GROUPS).dirty().single()
        val receiver = database()
        receiver.routineGroupQueries.save("office", "Office", 1, Days.WEEKDAYS.toLong(), 0, 0, 0)

        receiver.table(SyncCollections.ROUTINE_GROUPS).applyRemote(listOf(record))

        assertEquals(true, record.deleted)
        assertEquals(emptyList(), receiver.routineGroupQueries.active().executeAsList())
    }

    @Test
    fun `a tick from another device counts as done today and an untick clears it`() {
        val sender = database()
        sender.routineCheckQueries.save("2026-11-06/t1", "2026-11-06", "t1", 1, 1_000, 1)
        val tick = sender.table(SyncCollections.ROUTINE_CHECKS).dirty().single()
        val receiver = database()
        val table = receiver.table(SyncCollections.ROUTINE_CHECKS)

        table.applyRemote(listOf(tick))
        assertEquals(listOf("t1"), receiver.routineCheckQueries.doneOn("2026-11-06").executeAsList())

        sender.routineCheckQueries.save("2026-11-06/t1", "2026-11-06", "t1", 0, 2_000, 1)
        table.applyRemote(sender.table(SyncCollections.ROUTINE_CHECKS).dirty())
        assertEquals(emptyList(), receiver.routineCheckQueries.doneOn("2026-11-06").executeAsList())
    }

    @Test
    fun `a reset on one device starts the same round on another`() {
        val sender = database()
        val round = Period("r-abc", 5_000)
        sender.routineValueQueries.save("period", round.encode(), 5_000, 1)
        val record = sender.table(SyncCollections.ROUTINE_SETTINGS).dirty().single()
        val receiver = database()
        receiver.routineValueQueries.save("period", Period("2026-11-07", 0).encode(), 0, 0)

        receiver.table(SyncCollections.ROUTINE_SETTINGS).applyRemote(listOf(record))

        assertEquals(round, Period.decode(receiver.routineValueQueries.byKey("period").executeAsOne().value_))
    }

    @Test
    fun `a task with fields from a newer app waits for an update instead of losing them`() {
        val sender = database()
        sender.routineTaskQueries.save("t1", "fixed-1", "Clean shoes", 0, 2, 0, 1_000, 1)
        val record = sender.table(SyncCollections.ROUTINE_TASKS).dirty().single()
        val newer = record.copy(data = JsonObject(record.data + ("note" to JsonPrimitive("x"))))
        val table = database().table(SyncCollections.ROUTINE_TASKS)

        assertTrue(table.canRead(record))
        assertFalse(table.canRead(newer))
    }
}