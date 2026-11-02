package dev.habita.app.core.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * Opens a local SQLite database. Each platform entry point provides one, so the shared code never
 * deals with where database files live.
 */
fun interface DatabaseFactory {
    fun open(name: String, schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver
}