package dev.habita.app.core.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

/** Stores databases in [directory], by default `~/.habita`. */
class DesktopDatabaseFactory(
    private val directory: File = File(System.getProperty("user.home"), ".habita"),
) : DatabaseFactory {
    override fun open(name: String, schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver {
        directory.mkdirs()
        return JdbcSqliteDriver("jdbc:sqlite:${File(directory, name).absolutePath}", Properties(), schema)
    }
}