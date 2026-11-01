package dev.habita.server.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.habita.server.db.HabitaDatabase
import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

/**
 * Opens the SQLite database at [path], creating it or migrating its schema as needed.
 */
fun openDatabase(path: String): Pair<JdbcSqliteDriver, HabitaDatabase> {
    Path(path).toAbsolutePath().parent?.createDirectories()
    val properties = Properties().apply { put("foreign_keys", "true") }
    val driver = JdbcSqliteDriver("jdbc:sqlite:$path", properties, HabitaDatabase.Schema)
    return driver to HabitaDatabase(driver)
}