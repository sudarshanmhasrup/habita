package dev.habita.app.feature.expense.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.habita.app.feature.expense.db.ExpenseDatabase
import dev.habita.app.shared.api.SyncCollections
import kotlin.test.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpenseSyncTest {
    @Test
    fun `a phone's budget from before syncing migrates and goes up once`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            // The two tables as version 1 had them, with a budget already set.
            driver.execute(null, "CREATE TABLE category (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, limit_paise INTEGER NOT NULL, position INTEGER NOT NULL, archived INTEGER NOT NULL DEFAULT 0)", 0)
            driver.execute(null, "INSERT INTO category VALUES ('food', 'Food', 500000, 0, 0)", 0)
            driver.execute(null, "CREATE TABLE expense_value (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)", 0)
            driver.execute(null, "INSERT INTO expense_value VALUES ('income_paise', '4000000'), ('pending_payments', '[]')", 0)
            ExpenseDatabase.Schema.migrate(driver, 1, 2)
            val database = ExpenseDatabase(driver)
            val tables = expenseSyncTables(database).associateBy { it.collection }

            val categories = tables.getValue(SyncCollections.EXPENSE_CATEGORIES).dirty()
            val settings = tables.getValue(SyncCollections.EXPENSE_SETTINGS).dirty()

            assertEquals(listOf("food"), categories.map { it.id })
            assertEquals(1, categories.single().updatedAt)
            assertEquals(listOf("income_paise"), settings.map { it.id })
            assertNull(database.expenseValueQueries.select("pending_payments").executeAsOneOrNull())
        }
    }

    @Test
    fun `a payment from another phone is saved as synced and reads back the same`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            ExpenseDatabase.Schema.create(driver)
            val database = ExpenseDatabase(driver)
            val table = expenseSyncTables(database).single { it.collection == SyncCollections.EXPENSES }
            val sender = ExpenseDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpenseDatabase.Schema.create(it) })
            sender.expenseQueries.save("e1", 25_000, "Lunch", "cafe@ybl", "food", "ONLINE", "SMS", "123456", 1_000, 2_000, 0)
            val record = expenseSyncTables(sender).single { it.collection == SyncCollections.EXPENSES }.dirty().single()

            table.applyRemote(listOf(record))

            assertEquals(record, table.local("e1")!!.record)
            assertEquals(false, table.local("e1")!!.dirty)
            assertEquals(emptyList(), table.dirty())
        }
    }

    @Test
    fun `a payment with fields from a newer app waits for an update instead of losing them`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            ExpenseDatabase.Schema.create(driver)
            val database = ExpenseDatabase(driver)
            database.expenseQueries.save("e1", 25_000, "Lunch", null, "food", "CASH", "MANUAL", null, 1_000, 2_000, 0)
            val tables = expenseSyncTables(database).associateBy { it.collection }
            val table = tables.getValue(SyncCollections.EXPENSES)
            val record = table.dirty().single()
            val newer = record.copy(data = JsonObject(record.data + ("tip" to JsonPrimitive(500))))

            assertTrue(table.canRead(record))
            assertFalse(table.canRead(newer))
            assertFalse(tables.getValue(SyncCollections.EXPENSE_SETTINGS).canRead(newer))
        }
    }
}