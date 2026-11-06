package dev.habita.app.feature.gym.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.habita.app.feature.gym.db.GymDatabase
import dev.habita.app.shared.api.SyncCollections
import dev.habita.app.shared.gym.BodyLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GymRecordsTest {
    private fun store(driver: JdbcSqliteDriver) = GymStore(GymDatabase(driver))

    private fun table(store: GymStore, collection: String) = gymRecordTables(store).single { it.collection == collection }

    @Test
    fun `an existing phone gets the body log table`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            GymDatabase.Schema.create(driver)
            driver.execute(null, "DROP TABLE body_log", 0)
            GymDatabase.Schema.migrate(driver, 2, GymDatabase.Schema.version)
            val store = store(driver)

            store.saveBody(BodyLog("2026-11-07", weightGrams = 78_000, updatedAt = 1))

            assertEquals(78_000, store.bodyLog("2026-11-07")?.weightGrams)
        }
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
}