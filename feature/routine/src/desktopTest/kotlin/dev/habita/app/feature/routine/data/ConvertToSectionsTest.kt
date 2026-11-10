package dev.habita.app.feature.routine.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.habita.app.feature.routine.db.RoutineDatabase
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.api.winsOver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConvertToSectionsTest {
    private fun database() = RoutineDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { RoutineDatabase.Schema.create(it) })

    private fun RoutineDatabase.table(collection: String) = routineSyncTables(this).single { it.collection == collection }

    /** A copy of the routine as blocks, last synced at [time], like a device that hasn't opened in days. */
    private fun oldCopy(time: Long) = database().apply {
        routineGroupQueries.save("morning", "Morning", 0, Days.EVERY_DAY.toLong(), 0, time, 0)
        routineTaskQueries.save("morning-01", "morning", "Drink water.", 0, 0, 0, time, 0)
        routineTaskQueries.save("morning-02", "morning", "Plan the day.", 1, 1, 0, time, 0)
    }

    @Test
    fun `converted records are a millisecond newer than what they came from, not stamped now`() {
        val db = oldCopy(time = 1_000)

        assertTrue(db.convertToSections())

        val tasks = db.table(SyncCollections.ROUTINE_TASKS).dirty()
        val groups = db.table(SyncCollections.ROUTINE_GROUPS).dirty()
        assertEquals(setOf(1_001L), (tasks + groups).map { it.updatedAt }.toSet())
        assertEquals(setOf("morning", "fixed-1", "adjustable-1"), groups.map { it.id }.toSet())
        assertTrue(groups.single { it.id == "morning" }.deleted)
    }

    @Test
    fun `an old copy converted before syncing loses to edits made since on another device`() {
        val db = oldCopy(time = 1_000)
        // The phone converted on day one and renamed a task later; the server has that.
        val phone = oldCopy(time = 1_000).apply { convertToSections() }
        phone.routineTaskQueries.save("morning-01", "fixed-1", "Drink warm water.", 0, 0, 0, 9_000, 1)
        val edited = phone.table(SyncCollections.ROUTINE_TASKS).local("morning-01")!!.record

        db.convertToSections()
        val stale = db.table(SyncCollections.ROUTINE_TASKS).local("morning-01")!!.record

        assertTrue(edited.winsOver(stale))
        assertFalse(stale.winsOver(edited))
    }

    @Test
    fun `the converted routine still replaces the blocks it came from`() {
        val blocks = oldCopy(time = 1_000)
        val before = blocks.table(SyncCollections.ROUTINE_TASKS).local("morning-02")!!.record
        val db = oldCopy(time = 1_000)

        db.convertToSections()
        val after = db.table(SyncCollections.ROUTINE_TASKS).local("morning-02")!!.record

        assertEquals("adjustable-1", after.data["groupId"].toString().trim('"'))
        assertTrue(after.winsOver(before))
    }

    @Test
    fun `the built-in routine converts as never edited, so any synced copy wins`() {
        val db = oldCopy(time = 0)

        db.convertToSections()

        assertEquals(setOf(1L), db.table(SyncCollections.ROUTINE_TASKS).dirty().map { it.updatedAt }.toSet())
    }

    @Test
    fun `a routine already in sections is left alone`() {
        val db = oldCopy(time = 1_000).apply { convertToSections() }
        db.table(SyncCollections.ROUTINE_TASKS).markClean(db.table(SyncCollections.ROUTINE_TASKS).dirty())
        db.table(SyncCollections.ROUTINE_GROUPS).markClean(db.table(SyncCollections.ROUTINE_GROUPS).dirty())

        assertFalse(db.convertToSections())
        assertEquals(emptyList(), db.table(SyncCollections.ROUTINE_TASKS).dirty())
    }
}