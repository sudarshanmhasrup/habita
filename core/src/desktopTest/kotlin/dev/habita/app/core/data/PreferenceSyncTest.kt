package dev.habita.app.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.habita.app.core.db.CoreDatabase
import dev.habita.app.core.theme.Accent
import dev.habita.app.shared.api.SyncRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreferenceSyncTest {
    private fun database() = CoreDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { CoreDatabase.Schema.create(it) })

    @Test
    fun `a theme chosen before syncing moves over and goes up once`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            // The settings table as version 1 had it, with dark mode on and a login.
            driver.execute(null, "CREATE TABLE setting (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)", 0)
            driver.execute(null, "INSERT INTO setting VALUES ('dark_theme', 'true'), ('token', 'secret')", 0)
            CoreDatabase.Schema.migrate(driver, 1, 2)
            val database = CoreDatabase(driver)

            val dirty = PreferenceSyncTable(database).dirty()

            assertEquals(listOf("dark_theme"), dirty.map { it.id })
            assertEquals("secret", database.settingQueries.select("token").executeAsOne())
            assertTrue(Settings(database, CoroutineScope(Dispatchers.Unconfined)).darkTheme.value)
        }
    }

    @Test
    fun `choosing a colour saves it, marks it to sync and asks for a sync`() {
        val database = database()
        val settings = Settings(database, CoroutineScope(Dispatchers.Unconfined))
        var asked = 0
        settings.onPreferenceChanged = { asked++ }

        settings.setAccent(Accent.PLUM)

        assertEquals(1, asked)
        val record = PreferenceSyncTable(database).dirty().single()
        assertEquals("accent", record.id)
        assertEquals("plum", record.data["value"].toString().trim('"'))
    }

    @Test
    fun `a theme from another device applies here`() {
        val database = database()
        val table = PreferenceSyncTable(database)

        table.applyRemote(listOf(SyncRecord("accent", 5, data = buildJsonObject { put("value", "ocean") })))

        val settings = Settings(database, CoroutineScope(Dispatchers.Unconfined))
        assertEquals(Accent.OCEAN, settings.accent.value)
        assertTrue(table.dirty().isEmpty())
    }

    @Test
    fun `an unknown colour falls back to the default`() {
        assertEquals(Accent.Default, Accent.fromId("neon"))
        assertEquals(Accent.Default, Accent.fromId(null))
    }
}