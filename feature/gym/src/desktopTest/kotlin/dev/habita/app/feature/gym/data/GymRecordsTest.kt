package dev.habita.app.feature.gym.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.habita.app.feature.gym.db.GymDatabase
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.gym.BodyLog
import dev.habita.app.shared.gym.ExerciseSwap
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GymRecordsTest {
    private fun store(driver: JdbcSqliteDriver) = GymStore(GymDatabase(driver))

    private fun table(store: GymStore, collection: String) = gymRecordTables(store).single { it.collection == collection }

    @Test
    fun `a phone on the released schema gets the new tables`() {
        // The schema as the last release shipped it, version 2, with its migrations applied.
        val released = Files.createTempFile("gym", ".db")
        Files.copy(Path.of("src/commonMain/sqldelight/databases/2.db"), released, StandardCopyOption.REPLACE_EXISTING)
        JdbcSqliteDriver("jdbc:sqlite:$released").use { driver ->
            GymDatabase.Schema.migrate(driver, 2, GymDatabase.Schema.version)
            val store = store(driver)

            store.saveBody(BodyLog("2026-11-07", weightGrams = 78_000, updatedAt = 1))
            store.saveSwap(ExerciseSwap("pec-deck-fly", "cable-fly", updatedAt = 1))

            assertEquals(78_000, store.bodyLog("2026-11-07")?.weightGrams)
            assertEquals(listOf("cable-fly"), store.swaps().map { it.replacementId })
        }
        Files.deleteIfExists(released)
    }

    @Test
    fun `a body log goes up once and comes back the same on another phone`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { senderDriver ->
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { receiverDriver ->
                GymDatabase.Schema.create(senderDriver)
                GymDatabase.Schema.create(receiverDriver)
                val sender = store(senderDriver)
                val receiver = store(receiverDriver)
                val log = BodyLog("2026-11-07", weightGrams = 78_200, waistMm = 852, updatedAt = 5)
                sender.saveBody(log)
                val senderTable = table(sender, SyncCollections.GYM_BODY)

                val pushed = senderTable.dirty()
                senderTable.markClean(pushed)
                table(receiver, SyncCollections.GYM_BODY).applyRemote(pushed)

                assertEquals(listOf("2026-11-07"), pushed.map { it.id })
                assertTrue(senderTable.dirty().isEmpty())
                assertEquals(log, receiver.bodyLog("2026-11-07"))
                assertEquals(false, table(receiver, SyncCollections.GYM_BODY).local("2026-11-07")?.dirty)
            }
        }
    }

    @Test
    fun `undoing a swap reaches the other phone as a deletion`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { senderDriver ->
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { receiverDriver ->
                GymDatabase.Schema.create(senderDriver)
                GymDatabase.Schema.create(receiverDriver)
                val sender = store(senderDriver)
                val receiver = store(receiverDriver)
                receiver.saveSwap(ExerciseSwap("pec-deck-fly", "cable-fly", updatedAt = 1), dirty = false)
                sender.saveSwap(ExerciseSwap("pec-deck-fly", "cable-fly", updatedAt = 2, deleted = true))

                table(receiver, SyncCollections.GYM_SWAPS).applyRemote(table(sender, SyncCollections.GYM_SWAPS).dirty())

                assertEquals(true, receiver.swaps().single().deleted)
            }
        }
    }
}