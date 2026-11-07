package dev.habita.app.feature.gym.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.habita.app.feature.gym.db.GymDatabase
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.gym.Body
import dev.habita.app.shared.gym.BodyLog
import dev.habita.app.shared.gym.CheckIn
import dev.habita.app.shared.gym.ExerciseSwap
import dev.habita.app.shared.gym.WorkoutSession
import dev.habita.app.shared.gym.SetLog
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
            store.saveCheckIn(CheckIn("c1", at = 1, updatedAt = 1))

            assertEquals(78_000, store.bodyLog("2026-11-07")?.weightGrams)
            assertEquals(listOf("cable-fly"), store.swaps().map { it.replacementId })
            assertEquals(listOf("c1"), store.checkIns().map { it.id })
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

    @Test
    fun `a weight and a waist logged on two devices for one day both survive`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { phoneDriver ->
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { desktopDriver ->
                GymDatabase.Schema.create(phoneDriver)
                GymDatabase.Schema.create(desktopDriver)
                val phone = store(phoneDriver)
                val desktop = store(desktopDriver)
                phone.saveBody(Body.update(null, "2026-11-08", weightGrams = 78_000, waistMm = null, now = 10))
                desktop.saveBody(Body.update(null, "2026-11-08", weightGrams = null, waistMm = 850, now = 20))
                val phoneTable = table(phone, SyncCollections.GYM_BODY)
                val desktopTable = table(desktop, SyncCollections.GYM_BODY)

                // The desktop's copy reaches the phone while the phone's weight is still unsent.
                phoneTable.inTransaction { phoneTable.applyRemote(desktopTable.dirty()) }
                val merged = phone.bodyLog("2026-11-08")!!
                // The phone sends the merged copy; the desktop takes it.
                desktopTable.inTransaction { desktopTable.applyRemote(phoneTable.dirty()) }

                assertEquals(78_000, merged.weightGrams)
                assertEquals(850, merged.waistMm)
                assertEquals(true, phoneTable.local("2026-11-08")?.dirty)
                assertEquals(merged, desktop.bodyLog("2026-11-08"))
            }
        }
    }

    @Test
    fun `a saved workout this version can't read is finished at its last set`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            GymDatabase.Schema.create(driver)
            val database = GymDatabase(driver)
            val store = GymStore(database)
            store.saveSession(WorkoutSession("s1", "upper-a", programVersion = 1, startedAt = 100, updatedAt = 100))
            store.saveSet(SetLog("set1", "s1", "bench", 1, 20_000, 12, 12, completedAt = 500, updatedAt = 500))
            // Saved by a version whose workout shape this one doesn't know.
            database.gymValueQueries.save("active_workout", """{"sessionId":"s1","shapeFromTheFuture":true}""")

            assertEquals("s1", store.recoverUnreadableActive(now = 900))

            val session = store.session("s1")!!
            assertEquals(500, session.endedAt)
            assertEquals(null, store.active())
            // Nothing left to recover, and a workout that reads fine is never touched.
            assertEquals(null, store.recoverUnreadableActive(now = 1_000))
        }
    }
}