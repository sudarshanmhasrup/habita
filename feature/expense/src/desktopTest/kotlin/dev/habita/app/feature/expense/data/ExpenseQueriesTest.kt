package dev.habita.app.feature.expense.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.habita.app.feature.expense.db.ExpenseDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExpenseQueriesTest {
    private fun database() = ExpenseDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpenseDatabase.Schema.create(it) })

    @Test
    fun `a deleted payment from an SMS without a reference still stops the same SMS coming back`() {
        val database = database()
        val queries = database.expenseQueries
        queries.save("sms", 3_900, "Rapido", "rapido", "commute", "ONLINE", "SMS", null, 10_000, 10_000, 1, null)
        queries.save("cash", 5_000, "Tea", null, "food", "CASH", "MANUAL", null, 10_000, 10_000, 1, null)

        assertNotNull(queries.nearDuplicate(3_900, 9_000, 11_000).executeAsOneOrNull())
        // A payment typed in by hand and then deleted was a mistake, not a copy of a bank SMS.
        assertNull(queries.nearDuplicate(5_000, 9_000, 11_000).executeAsOneOrNull())
    }

    @Test
    fun `a payee's category comes from its latest sorted payment, even one synced from another phone`() {
        val queries = database().expenseQueries
        queries.saveSynced("old", 3_900, "Rapido", "rapido@ybl", "food", "ONLINE", "SMS", "1", 1_000, 1_000, 0, null)
        queries.saveSynced("new", 3_900, "Rapido", "rapido@ybl", "commute", "ONLINE", "SMS", "2", 2_000, 2_000, 0, null)
        queries.saveSynced("gone", 3_900, "Rapido", "rapido@ybl", "rent", "ONLINE", "SMS", "3", 3_000, 3_000, 1, null)
        queries.saveSynced("unsorted", 3_900, "Rapido", "rapido@ybl", null, "ONLINE", "SMS", "4", 4_000, 4_000, 0, null)

        assertEquals("commute", queries.lastCategoryForPayee("rapido@ybl").executeAsOneOrNull())
        assertNull(queries.lastCategoryForPayee("zomato@ybl").executeAsOneOrNull())
    }
}